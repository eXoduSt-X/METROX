package code.name.monkey.retromusic.fragments.home

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader

object YoutubeDownloaderEngine {

    private var isInitialized = false

    // Inicialización usando el downloader interno por defecto de NewPipe
    fun initNewPipe(downloaderImpl: Downloader) {
        if (!isInitialized) {
            try {
                NewPipe.init(downloaderImpl)
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Extrae el enlace directo de streaming (Audio o Video) sin congelar la interfaz.
     */
    suspend fun extraerStreamUrl(videoUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Obtener el servicio de YouTube de forma explícita
            val service: StreamingService = ServiceList.YouTube

            // 2. Extraer la información completa del stream
            val streamInfo = StreamInfo.getInfo(service, videoUrl)

            // 3. Obtener los streams de video disponibles
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
