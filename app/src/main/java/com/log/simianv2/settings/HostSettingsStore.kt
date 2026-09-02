package com.log.simianv2.settings

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject

/** Persistent settings stored in the hooked host application's private data directory. */
object HostSettingsStore {
    const val QUICK_ANSWER = "quick_answer"
    const val AUTO_ANSWER_ENABLED = "auto_answer_enabled"
    const val AUTO_ANSWER_SPEED = "auto_answer_speed"
    const val AUTO_CLICK_HAPPY_ACCEPT = "auto_click_happy_accept"
    const val AUTO_CLICK_CONTINUE = "auto_click_continue"
    const val AUTO_CLICK_CONTINUE_PK = "auto_click_continue_pk"
    const val CUSTOM_ANSWER_ENABLED = "custom_answer_enabled"
    const val CUSTOM_ANSWER = "custom_answer"
    const val CUSTOM_QUESTION_COUNT_ENABLED = "custom_question_count_enabled"
    const val CUSTOM_QUESTION_COUNT = "custom_question_count"
    const val CUSTOM_END_TIME_ENABLED = "custom_end_time_enabled"
    const val CUSTOM_END_TIME = "custom_end_time"
    const val UNLIMITED_NAME = "unlimited_name"
    const val LOG_OVERLAY = "log_overlay"

    private const val DIRECTORY_NAME = "simian"
    private const val FILE_NAME = "settings.json"
    private val lock = Any()

    fun get(context: Context, key: String, defaultValue: Any): Any =
        synchronized(lock) {
            val data = read(context)
            @Suppress("UNCHECKED_CAST")
            when (defaultValue) {
                is Boolean -> data.optBoolean(key, defaultValue)
                is String -> data.optString(key, defaultValue)
                is Int -> data.optInt(key, defaultValue)
                is Long -> data.optLong(key, defaultValue)
                else -> data.opt(key).takeUnless {
                    it == null || it == JSONObject.NULL
                } ?: defaultValue
            }
        }

    fun put(context: Context, key: String, value: Any) {
        synchronized(lock) {
            val data = read(context)
            data.put(key, value)
            write(context, data)
        }
    }

    private fun read(context: Context): JSONObject {
        val file = settingsFile(context)
        if (!file.exists()) return JSONObject()
        return runCatching {
            val bytes = AtomicFile(file).readFully()
            JSONObject(bytes.toString(Charsets.UTF_8))
        }
            .getOrDefault(JSONObject())
    }

    private fun write(context: Context, data: JSONObject) {
        val file = settingsFile(context)
        file.parentFile?.mkdirs()
        val atomicFile = AtomicFile(file)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(data.toString(2).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (_: Throwable) {
            output?.let(atomicFile::failWrite)
        }
    }

    private fun settingsFile(context: Context): File =
        File(File(context.filesDir, DIRECTORY_NAME), FILE_NAME)
}
