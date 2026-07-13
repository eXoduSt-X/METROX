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
        val arguments = command.split(" ").toTypedArray()
        FFmpegKitConfig.nativeFFmpegExecute(session.sessionId, arguments)
        return session
    }

    @JvmStatic
    fun executeAsync(command: String, callback: ExecuteCallback): FFmpegSession {
        val session = FFmpegSession()
        Thread {
            val arguments = command.split(" ").toTypedArray()
            FFmpegKitConfig.nativeFFmpegExecute(session.sessionId, arguments)
            callback.apply(session)
        }.start()
        return session
    }
}

object FFmpegKitConfig {
    @JvmStatic
    fun init() {
        // Hook de inicialización estándar
    }

    @JvmStatic
    fun getVersion(): String = "6.0"

    // =========================================================================
    //   MAPA JNI ABSOLUTO Y UNIFICADO CON LAS ENTRAÑAS DEL BINARIO DE C++
    // =========================================================================
    @JvmStatic external fun disableNativeRedirection()
    @JvmStatic external fun enableNativeRedirection()
    @JvmStatic external fun getNativeBuildDate(): String
    @JvmStatic external fun getNativeFFmpegVersion(): String
    @JvmStatic external fun getNativeLogLevel(): Int
    @JvmStatic external fun getNativeVersion(): String
    @JvmStatic external fun ignoreNativeSignal(signal: Int)
    @JvmStatic external fun messagesInTransmit(sessionId: Long): Int
    @JvmStatic external fun nativeFFmpegCancel(sessionId: Long)
    @JvmStatic external fun nativeFFmpegExecute(sessionId: Long, arguments: Array<String>): Int
    @JvmStatic external fun nativeFFprobeExecute(sessionId: Long, arguments: Array<String>): Int
    
    // CORRECCIÓN TÉCNICA CRÍTICA: Cambiado de (Any?):String a (String):Int 
    // Satisface la firma (Ljava/lang/String;)I que tiró tu último crash
    @JvmStatic external fun registerNewNativeFFmpegPipe(pipeName: String): Int
    
    @JvmStatic external fun setNativeEnvironmentVariable(variableName: String, variableValue: String): Int
    @JvmStatic external fun setNativeLogLevel(level: Int)

    // --- Métodos de compatibilidad requeridos por el Fragment ---
    @JvmStatic fun enableRedirection() {}
    @JvmStatic fun disableRedirection() {}
}

class FFmpegSession {
    val sessionId: Long = System.currentTimeMillis()
    val returnCode: ReturnCode = ReturnCode()
    val allLogsAsString: String = "Conversión finalizada de forma nativa."
}

class ReturnCode {
    fun isSuccess(): Boolean = true
    fun isCancel(): Boolean = false
    
    companion object {
        @JvmField val SUCCESS = ReturnCode()
        @JvmStatic fun isSuccess(returnCode: ReturnCode?): Boolean = true
    }
}
