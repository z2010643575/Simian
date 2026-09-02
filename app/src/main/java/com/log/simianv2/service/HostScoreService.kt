package com.log.simianv2.service

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** Calls the host's authenticated legacy exercise API to query and submit score. */
object HostScoreService {
    @Volatile
    var isReady = false
        private set

    private lateinit var apiService: Any
    private lateinit var gson: Any
    private lateinit var postSavedExpMethod: Method
    private lateinit var getCurrentUserExpMethod: Method
    private lateinit var requestBodyClass: Class<*>
    private lateinit var continuationClass: Class<*>
    private lateinit var coroutineContext: Any

    fun initialize(classLoader: ClassLoader) {
        val serviceClass =
            classLoader.loadClass("com.fenbi.android.leo.api.LeoExerciseCommonLegacyApiService")
        val companionClass = classLoader.loadClass("${serviceClass.name}\$a")
        val companion = companionClass.getDeclaredField("a").accessibleGet(null)
        apiService =
            companion.javaClass.declaredMethods
                .first { it.name == "a" && it.parameterCount == 0 }
                .accessibleInvoke(companion)!!
        gson = classLoader.loadClass("com.google.gson.Gson").getDeclaredConstructor().newInstance()
        postSavedExpMethod =
            apiService.javaClass.declaredMethods.first {
                it.name == "postSavedExp" && it.parameterCount == 2
            }
        getCurrentUserExpMethod =
            apiService.javaClass.declaredMethods.first {
                it.name == "getCurrentUserExp" && it.parameterCount == 1
            }
        requestBodyClass = postSavedExpMethod.parameterTypes.first()
        continuationClass = postSavedExpMethod.parameterTypes.last()
        coroutineContext =
            classLoader
                .loadClass("kotlin.coroutines.EmptyCoroutineContext")
                .getDeclaredField("INSTANCE")
                .accessibleGet(null)
        isReady = true
    }

    fun getCurrentScore(onResult: (Result<Int>) -> Unit) {
        if (!isReady) {
            onResult(Result.failure(IllegalStateException("刷分接口尚未初始化，请返回首页后重试")))
            return
        }
        val continuation = continuation { result ->
            onResult(
                result.mapCatching { response ->
                    requireNotNull(response).findField("curWeekScore").accessibleGet(response)
                        as Int
                }
            )
        }
        runCatching { getCurrentUserExpMethod.accessibleInvoke(apiService, continuation) }
            .onFailure { onResult(Result.failure(it.unwrap())) }
    }

    fun addScore(score: Int, onResult: (Result<Any?>) -> Unit) {
        if (!isReady) {
            onResult(Result.failure(IllegalStateException("刷分接口尚未初始化，请返回首页后重试")))
            return
        }
        runCatching {
                val fromJson =
                    gson.javaClass.methods.first {
                        it.name == "fromJson" &&
                            it.parameterTypes.contentEquals(
                                arrayOf(String::class.java, Class::class.java)
                            )
                    }
                val body = fromJson.invoke(gson, "{\"todayExercises\":[{}]}", requestBodyClass)!!
                val exercises = body.findField("todayExercises").accessibleGet(body) as List<*>
                val exercise = requireNotNull(exercises.firstOrNull())
                exercise.findField("finishTime").accessibleSet(exercise, System.currentTimeMillis())
                exercise.findField("obtainExp").accessibleSet(exercise, score)
                postSavedExpMethod.accessibleInvoke(apiService, body, continuation(onResult))
            }
            .onFailure { onResult(Result.failure(it.unwrap())) }
    }

    private fun continuation(onResult: (Result<Any?>) -> Unit): Any =
        Proxy.newProxyInstance(continuationClass.classLoader, arrayOf(continuationClass)) {
            _,
            method,
            args ->
            when (method.name) {
                "getContext" -> coroutineContext
                "resumeWith" -> {
                    val value = args?.firstOrNull()
                    if (value?.javaClass?.name == "kotlin.Result\$Failure") {
                        val error = value.findField("exception").accessibleGet(value) as Throwable
                        onResult(Result.failure(error))
                    } else {
                        onResult(Result.success(value))
                    }
                    null
                }
                else -> null
            }
        }

    private fun Any.findField(name: String): Field {
        var type: Class<*>? = javaClass
        while (type != null) {
            runCatching {
                return type.getDeclaredField(name)
            }
            type = type.superclass
        }
        error("未找到字段: $name")
    }

    private fun Field.accessibleGet(instance: Any?): Any {
        isAccessible = true
        return requireNotNull(get(instance)) { "字段 $name 的值为空" }
    }

    private fun Field.accessibleSet(instance: Any, value: Any) {
        isAccessible = true
        set(instance, value)
    }

    private fun Method.accessibleInvoke(instance: Any?, vararg args: Any?): Any? {
        isAccessible = true
        return invoke(instance, *args)
    }

    private fun Throwable.unwrap(): Throwable = cause ?: this
}
