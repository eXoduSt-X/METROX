package code.name.monkey.retromusic.fragments.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization

object YoutubeDownloaderEngine {

    private var isInitialized = false

    fun initNewPipe() {
        if (!isInitialized) {
            try {
                // 1. Vinculamos nuestro puente de red personalizado
                val downloader = RetroDownloader()
                
                // 2. Forzamos la localización global obligatoria para evitar respuestas vacías de YouTube
                val localization = Localization.fromLocale(java.util.Locale.getDefault())
                
                // 3. Inicializamos el núcleo de NewPipe con red y localización completas
                NewPipe.init(downloader, localization)
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
            // Asegurar inicialización por si acaso
            initNewPipe()

            val service: StreamingService = ServiceList.YouTube
            
            // Forzar la obtención de la información del stream rompiendo la caché vieja
            val streamInfo = StreamInfo.getInfo(service, videoUrl)

            val videoStreams = streamInfo.videoStreams
            val audioStreams = streamInfo.audioStreams

            // Priorizamos los streams de video (MP4) que se acoplen a tu panel visual
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
