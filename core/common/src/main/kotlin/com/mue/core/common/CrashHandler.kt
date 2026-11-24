package com.mue.core.common

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashesDir = File(context.cacheDir, "crashes")
            if (!crashesDir.exists()) {
                crashesDir.mkdirs()
            }

            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            val formattedDate = dateFormat.format(Date(timestamp))

            val crashFile = File(crashesDir, "crash_$timestamp.txt")
            val report = buildString {
                appendLine("=== MuE Crash Report ===")
                appendLine("Timestamp: $formattedDate ($timestamp)")
                appendLine("Thread: ${thread.name} (id: ${thread.id})")
                appendLine("Exception: ${throwable::class.java.name}")
                appendLine("Message: ${throwable.message}")
                appendLine("--- Stack Trace ---")
                appendLine(throwable.stackTraceToString())
            }

            crashFile.writeText(report)
        } catch (e: Exception) {
            // Failsafe: avoid crashing inside crash handler
            e.printStackTrace()
        } finally {
            // Allow OS or default handler to gracefully terminate process
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

