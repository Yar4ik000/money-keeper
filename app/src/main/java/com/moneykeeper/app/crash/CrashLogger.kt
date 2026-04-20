package com.moneykeeper.app.crash

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    const val LOG_DIR = "crash_logs"
    private const val MAX_FILES = 5

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try { write(appCtx, thread, ex) } catch (_: Throwable) {}
            previous?.uncaughtException(thread, ex)
        }
    }

    private fun write(context: Context, thread: Thread, ex: Throwable) {
        val dir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }
        dir.listFiles()
            ?.sortedBy { it.lastModified() }
            ?.dropLast(MAX_FILES - 1)
            ?.forEach { it.delete() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        File(dir, "crash_$ts.txt").bufferedWriter().use { w ->
            w.write("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
            w.write("Thread: ${thread.name}\n\n")
            w.write(ex.stackTraceToString())
        }
    }

    fun logFiles(context: Context): List<File> =
        File(context.filesDir, LOG_DIR)
            .takeIf { it.exists() }
            ?.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
