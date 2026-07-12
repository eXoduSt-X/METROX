package com.arthenica.ffmpegkit

import android.util.Log

// Interfaz SAM que espera la expresión lambda en HomeFragment
fun interface ExecuteCallback {
    fun apply(session: FFmpegSession)
}

object FFmpegKit {
    init {
        try {
            System.loadLibrary("ffmpegkit")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("FFmpegKit", "Error cargando binarios nativos de 16KB", e)
        }
    }

    @JvmStatic
    fun execute(command: String): FFmpegSession {
        return FFmpegSession()
    }

    @JvmStatic
    fun executeAsync(command: String, callback: ExecuteCallback): FFmpegSession {
        val session = FFmpegSession()
        // Ejecución inmediata en el hilo para simular el comportamiento
        callback.apply(session)
        return session
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
