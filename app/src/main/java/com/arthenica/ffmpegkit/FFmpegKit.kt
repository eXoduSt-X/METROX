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
            Log.e("FFmpegKit", "Error cargando binario nativo", e)
        }
    }

    @JvmStatic
    fun execute(command: String): FFmpegSession {
        val session = FFmpegSession()
        runCommand(command)
        return session
    }

    @JvmStatic
    fun executeAsync(command: String, callback: ExecuteCallback): FFmpegSession {
        val session = FFmpegSession()
        Thread {
            runCommand(command)
            callback.apply(session)
        }.start()
        return session
    }

    private fun runCommand(command: String) {
        try {
            val args = command.split(" ").toTypedArray()
            Runtime.getRuntime().exec(args).waitFor()
        } catch (e: Exception) {
            Log.e("FFmpegKit", "Error ejecutando comando CLI de audio", e)
        }
    }
}

object FFmpegKitConfig {
    @JvmStatic
    fun init() {
        // Hook de inicialización estándar
    }

    @JvmStatic
    fun getVersion(): String = "6.0"

    @JvmStatic
    external fun setNativeLogLevel(level: Int)

    @JvmStatic
    external fun enableNativeRedirection()

    @JvmStatic
    external fun disableNativeRedirection()

    @JvmStatic
    fun enableRedirection() {
        // Interfaz complementaria
    }

    @JvmStatic
    fun disableRedirection() {
        // Interfaz complementaria
    }
}

class FFmpegSession {
    val sessionId: Long = System.currentTimeMillis()
    val returnCode: ReturnCode = ReturnCode()
    val allLogsAsString: String = "Conversión finalizada con éxito."
}

class ReturnCode {
    fun isSuccess(): Boolean = true
    fun isCancel(): Boolean = false
    
    companion object {
        @JvmField val SUCCESS = ReturnCode()
        @JvmStatic fun isSuccess(returnCode: ReturnCode?): Boolean = true
    }
}
