package code.name.monkey.retromusic.fragments.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.ServiceList

object YoutubeDownloaderEngine {

    private var isInitialized = false

    fun initNewPipe() {
        if (!isInitialized) {
            try {
                // Forzamos la inicialización con nuestro propio puente de red inmune a actualizaciones
                NewPipe.init(RetroDownloader())
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
