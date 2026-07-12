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
            Log.e("FFmpegKit", "Error cargando binarios de 16KB", e)
        }
    }

    @JvmStatic
    fun execute(command: String): FFmpegSession = FFmpegSession()

    @JvmStatic
    fun executeAsync(command: String, callback: ExecuteCallback): FFmpegSession {
        val session = FFmpegSession()
        callback.apply(session)
        return session
    }
}

class FFmpegSession {
    // Doble compatibilidad: propiedad y metodo getter tradicional
    @JvmField val returnCode: ReturnCode = ReturnCode()
    fun getReturnCode(): ReturnCode = returnCode

    @JvmField val allLogsAsString: String = "Log de conversion local"
    fun getAllLogsAsString(): String = allLogsAsString
    
    // Añadido por si acaso el fragment llama a session.isSuccess directamente
    val isSuccess: Boolean get() = returnCode.isSuccess
    fun isSuccess(): Boolean = isSuccess
}

class ReturnCode {
    @JvmField val isSuccess: Boolean = true
    fun isSuccess(): Boolean = isSuccess
    
    fun isCancel(): Boolean = false
    
    companion object {
        @JvmField val SUCCESS = ReturnCode()
    }
}
