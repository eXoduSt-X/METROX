package code.name.monkey.retromusic.fragments.home

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.ServiceList

object YoutubeDownloaderEngine {

    private var isInitialized = false

    // Inicialización segura de NewPipe en segundo plano
    fun initNewPipe(context: Context) {
        if (!isInitialized) {
            try {
                // Instanciamos el cliente de red que requiere NewPipe para las consultas
                NewPipe.init(RetroLinkDownloaderHttpClient.getInstance())
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Extrae el enlace directo de streaming (Audio o Video) sin congelar la interfaz.
     * @param videoUrl Enlace de YouTube introducido por el usuario.
     * @param callback Retorna la URL directa si tiene éxito, o null si falla.
     */
    suspend fun extraerStreamUrl(videoUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Obtener el servicio de YouTube de forma explícita
            val service: StreamingService = ServiceList.YouTube

            // 2. Extraer la información completa del stream
            val streamInfo = StreamInfo.getInfo(service, videoUrl)

            // 3. Obtener los streams de video/audio combinados o solo audio de mayor calidad
            val videoStreams = streamInfo.videoStreams
            val audioStreams = streamInfo.audioStreams

            if (videoStreams.isNotEmpty()) {
                // Retorna el primer stream de video disponible (MP4 por defecto)
                videoStreams[0].url
            } else if (audioStreams.isNotEmpty()) {
                // Si no hay video directo acoplado, extrae el audio de respaldo
                audioStreams[0].url
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

