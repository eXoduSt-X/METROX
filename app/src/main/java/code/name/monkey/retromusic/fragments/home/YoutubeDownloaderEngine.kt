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

    // Inicialización interna simplificada sin parámetros externos
    fun initNewPipe() {
        if (!isInitialized) {
            try {
                // Usamos el inicializador por defecto de NewPipe para su cliente de red nativo
                org.schabi.newpipe.extractor.NewPipe.init(org.schabi.newpipe.extractor.downloader.Downloader.Factory.getDownloader())
                isInitialized = true
            } catch (e1: Exception) {
                try {
                    // Alternativa de respaldo si la fábrica cambia de nombre en esta versión
                    val defaultDownloader = Class.forName("org.schabi.newpipe.extractor.downloader.Downloader").getDeclaredConstructor().newInstance()
                    NewPipe.init(defaultDownloader as org.schabi.newpipe.extractor.downloader.Downloader)
                    isInitialized = true
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        }
    }

    /**
     * Extrae el enlace directo de streaming (Audio o Video) sin congelar la interfaz.
     */
    suspend fun extraerStreamUrl(videoUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val service: StreamingService = ServiceList.YouTube
            val streamInfo = StreamInfo.getInfo(service, videoUrl)

            val videoStreams = streamInfo.videoStreams
            val audioStreams = streamInfo.audioStreams

            if (videoStreams.isNotEmpty()) {
                videoStreams[0].url
            } else if (audioStreams.isNotEmpty()) {
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
