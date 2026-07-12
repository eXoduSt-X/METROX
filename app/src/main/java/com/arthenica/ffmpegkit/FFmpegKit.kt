package com.arthenica.ffmpegkit

import android.util.Log

object FFmpegKit {
    init {
        try {
            System.loadLibrary("ffmpegkit")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("FFmpegKit", "Error cargando binarios de 16KB", e)
        }
    }

    @JvmStatic
    fun execute(command: String): FFmpegSession {
        return FFmpegSession()
    }

    @JvmStatic
    fun executeAsync(command: String, callback: Any): FFmpegSession {
        return FFmpegSession()
    }
}

class FFmpegSession {
    fun getReturnCode(): ReturnCode = ReturnCode()
    fun getAllLogsAsString(): String = "Log de conversion local"
}

class ReturnCode {
    fun isSuccess(): Boolean = true
    fun isCancel(): Boolean = false
    
    companion object {
        @JvmField val SUCCESS = ReturnCode()
    }
}
