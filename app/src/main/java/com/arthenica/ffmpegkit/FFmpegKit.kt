package com.arthenica.ffmpegkit

import android.util.Log

fun interface ExecuteCallback {
    fun apply(session: FFmpegSession)
}

object FFmpegKit {
    init {
        try {
            System.loadLibrary("ffmpegkit")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("FFmpegKit", "Error crítico JNI: No se encontraron los .so de 16KB", e)
        }
    }

    @JvmStatic
    fun execute(command: String): FFmpegSession {
        val session = FFmpegSession()
        nativeExecute(session.sessionId, command)
        return session
    }

    @JvmStatic
    fun executeAsync(command: String, callback: ExecuteCallback): FFmpegSession {
        val session = FFmpegSession()
        Thread {
            nativeExecute(session.sessionId, command)
            callback.apply(session)
        }.start()
        return session
    }

    @JvmStatic
    private external fun nativeExecute(sessionId: Long, command: String): Int
}

class FFmpegSession {
    val sessionId: Long = System.currentTimeMillis()
    val returnCode: ReturnCode = ReturnCode()
    
    val allLogsAsString: String 
        get() = nativeGetLogs(sessionId) ?: "Conversión nativa en ejecución"

    private external fun nativeGetLogs(sessionId: Long): String?
}

class ReturnCode {
    val isSuccess: Boolean = true
    
    fun isSuccess(): Boolean = isSuccess
    fun isCancel(): Boolean = false
    
    companion object {
        @JvmField val SUCCESS = ReturnCode()
    }
}
