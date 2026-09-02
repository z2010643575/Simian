package com.log.simianv2.dexkit

import android.content.Context
import android.util.AtomicFile
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import org.json.JSONArray
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

/** One initialization session. DexKit is opened lazily only when a cache entry cannot be restored. */
internal class DexKitLookupSession(private val context: Context) : Closeable {
    private val classLoader = context.classLoader
    private val version = HostVersion.read(context)
    private var dirty = false
    private val cacheFile = File(File(context.filesDir, CACHE_DIRECTORY), CACHE_FILE)
    private val entries = loadEntries()
    private var bridge: DexKitBridge? = null

    var usedDexKit: Boolean = false
        private set

    fun scoped(featureName: String) = DexKitLookup(this, featureName)

    internal fun method(
        cacheKey: String,
        label: String,
        matcher: MethodMatcher.() -> Unit,
    ): Method = restore(cacheKey) as? Method ?: findMethod(cacheKey, label, matcher)

    internal fun constructor(
        cacheKey: String,
        label: String,
        matcher: MethodMatcher.() -> Unit,
    ): Constructor<*> = restore(cacheKey) as? Constructor<*>
        ?: findConstructor(cacheKey, label, matcher)

    internal fun findClass(
        cacheKey: String,
        label: String,
        matcher: ClassMatcher.() -> Unit,
    ): Class<*> = restoreClass(cacheKey) ?: findClassWithDexKit(cacheKey, label, matcher)

    fun save() {
        if (!dirty) return
        cacheFile.parentFile?.mkdirs()
        val root = JSONObject()
            .put("schemaVersion", CACHE_SCHEMA_VERSION)
            .put("versionCode", version.versionCode)
            .put("versionName", version.versionName)
            .put("lastUpdateTime", version.lastUpdateTime)
            .put("targets", entries)
        val atomicFile = AtomicFile(cacheFile)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
            dirty = false
        } catch (error: Throwable) {
            output?.let(atomicFile::failWrite)
            throw error
        }
    }

    override fun close() {
        bridge?.close()
        bridge = null
    }

    private fun findMethod(
        cacheKey: String,
        label: String,
        matcher: MethodMatcher.() -> Unit,
    ): Method {
        val method = dexKit().findMethod { matcher { matcher() } }
            .singleOrNull()?.getMethodInstance(classLoader)
            ?: error("未找到 $label")
        remember(cacheKey, method)
        return method
    }

    private fun findConstructor(
        cacheKey: String,
        label: String,
        matcher: MethodMatcher.() -> Unit,
    ): Constructor<*> {
        val constructor = dexKit().findMethod {
            matcher {
                name = "<init>"
                matcher()
            }
        }.singleOrNull()?.getConstructorInstance(classLoader)
            ?: error("未找到 $label")
        remember(cacheKey, constructor)
        return constructor
    }

    private fun findClassWithDexKit(
        cacheKey: String,
        label: String,
        matcher: ClassMatcher.() -> Unit,
    ): Class<*> {
        val clazz = dexKit().findClass { matcher { matcher() } }
            .singleOrNull()?.getInstance(classLoader)
            ?: error("未找到 $label")
        rememberClass(cacheKey, clazz)
        return clazz
    }

    private fun restore(cacheKey: String): Executable? {
        val data = entries.optJSONObject(cacheKey) ?: return null
        if (data.optString("kind") == "class") return null
        return runCatching {
            val owner = classLoader.loadClass(data.getString("className"))
            val parameterTypes = data.getJSONArray("parameterTypes").toClassArray(classLoader)
            if (data.getBoolean("constructor")) {
                owner.getDeclaredConstructor(*parameterTypes)
            } else {
                owner.getDeclaredMethod(data.getString("methodName"), *parameterTypes)
            }
        }.onFailure {
            entries.remove(cacheKey)
            dirty = true
        }.getOrNull()
    }

    private fun restoreClass(cacheKey: String): Class<*>? {
        val data = entries.optJSONObject(cacheKey) ?: return null
        if (data.optString("kind") != "class") return null
        return runCatching {
            classLoader.loadClass(data.getString("className"))
        }.onFailure {
            entries.remove(cacheKey)
            dirty = true
        }.getOrNull()
    }

    private fun remember(cacheKey: String, executable: Executable) {
        entries.put(
            cacheKey,
            JSONObject()
                .put("kind", if (executable is Constructor<*>) "constructor" else "method")
                .put("className", executable.declaringClass.name)
                .put("methodName", if (executable is Method) executable.name else "<init>")
                .put("constructor", executable is Constructor<*>)
                .put("parameterTypes", JSONArray(executable.parameterTypes.map(Class<*>::getName))),
        )
        dirty = true
    }

    private fun rememberClass(cacheKey: String, clazz: Class<*>) {
        entries.put(
            cacheKey,
            JSONObject()
                .put("kind", "class")
                .put("className", clazz.name),
        )
        dirty = true
    }

    private fun dexKit(): DexKitBridge {
        bridge?.let { return it }
        ensureNativeLoaded()
        usedDexKit = true
        return DexKitBridge.create(context.applicationInfo.sourceDir).also { bridge = it }
    }

    private fun loadEntries(): JSONObject {
        if (!cacheFile.exists()) return JSONObject()
        return runCatching {
            val root = JSONObject(AtomicFile(cacheFile).readFully().toString(Charsets.UTF_8))
            val matches =
                root.optInt("schemaVersion") == CACHE_SCHEMA_VERSION &&
                        root.optLong("versionCode") == version.versionCode &&
                        root.optString("versionName") == version.versionName &&
                        root.optLong("lastUpdateTime") == version.lastUpdateTime
            if (matches) root.optJSONObject("targets") ?: JSONObject() else JSONObject().also {
                dirty = true
            }
        }.getOrElse {
            dirty = true
            JSONObject()
        }
    }

    private data class HostVersion(
        val versionCode: Long,
        val versionName: String,
        val lastUpdateTime: Long,
    ) {
        companion object {
            fun read(context: Context): HostVersion {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                return HostVersion(
                    versionCode = info.longVersionCode,
                    versionName = info.versionName.orEmpty(),
                    lastUpdateTime = info.lastUpdateTime,
                )
            }
        }
    }

    private companion object {
        const val CACHE_SCHEMA_VERSION = 2
        const val CACHE_DIRECTORY = "simian"
        const val CACHE_FILE = "dexkit_targets.json"

        @Volatile
        var nativeLoaded = false

        @Synchronized
        fun ensureNativeLoaded() {
            if (nativeLoaded) return
            System.loadLibrary("dexkit")
            nativeLoaded = true
        }
    }
}

internal class DexKitLookup(
    private val session: DexKitLookupSession,
    private val featureName: String,
) {
    fun method(
        id: String,
        label: String,
        matcher: MethodMatcher.() -> Unit,
    ): Method = session.method("$featureName.$id", label, matcher)

    fun constructor(
        id: String,
        label: String,
        matcher: MethodMatcher.() -> Unit,
    ): Constructor<*> = session.constructor("$featureName.$id", label, matcher)

    fun findClass(
        id: String,
        label: String,
        matcher: ClassMatcher.() -> Unit,
    ): Class<*> = session.findClass("$featureName.$id", label, matcher)
}

private fun JSONArray.toClassArray(classLoader: ClassLoader): Array<Class<*>> =
    Array(length()) { index -> classForName(getString(index), classLoader) }

private fun classForName(name: String, classLoader: ClassLoader): Class<*> =
    when (name) {
        "boolean" -> Boolean::class.javaPrimitiveType!!
        "byte" -> Byte::class.javaPrimitiveType!!
        "char" -> Char::class.javaPrimitiveType!!
        "short" -> Short::class.javaPrimitiveType!!
        "int" -> Int::class.javaPrimitiveType!!
        "long" -> Long::class.javaPrimitiveType!!
        "float" -> Float::class.javaPrimitiveType!!
        "double" -> Double::class.javaPrimitiveType!!
        "void" -> Void.TYPE
        else -> Class.forName(name, false, classLoader)
    }
