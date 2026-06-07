package code.name.monkey.retromusic.ytd.extractor

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfo
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

object YoutubeExtractor {

    private var isInitialized = false
    private val youtubeServiceId = YoutubeService(0).serviceId

    // Cliente OkHttp con configuraciones de tiempo de espera óptimas
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Implementación del Downloader que NewPipe exige para procesar tráfico HTTP
    private val appDownloader = object : Downloader() {
        @Throws(IOException::class)
        override fun execute(request: Request): Response {
            val url = request.url()
            val method = request.method()
            val headers = request.headers()
            val body = request.data()

            val builder = okhttp3.Request.Builder().url(url)
            
            // Mapeamos las cabeceras requeridas por NewPipe al cliente OkHttp
            headers.forEach { (key, values) ->
                values.forEach { value -> builder.addHeader(key, value) }
            }
            
            // Agente de usuario por defecto para evitar bloqueos del backend de YouTube
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

            val okHttpRequest = when {
                method.equals("POST", ignoreCase = true) -> {
                    val bodyBytes = body ?: ByteArray(0)
                    val mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8")
                    builder.post(okhttp3.RequestBody.create(mediaType, bodyBytes)).build()
                }
                else -> builder.get().build()
            }

            val okHttpResponse = httpClient.newCall(okHttpRequest).execute()
            val responseBody = okHttpResponse.body()?.string() ?: ""
            
            return Response(
                okHttpResponse.code(),
                okHttpResponse.message(),
                okHttpResponse.headers().toMultimap(),
                responseBody,
                okHttpResponse.request().url().toString()
            )
        }
    }

    /**
     * Inicializa el entorno de NewPipe vinculando nuestro Downloader personalizado.
     */
    fun initNewPipe() {
        if (!isInitialized) {
            try {
                NewPipe.init(appDownloader)
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Extrae de forma nativa la URL directa del stream de video de YouTube.
     */
    fun extract(url: String): String {
        return try {
            // Aseguramos que el motor de red de NewPipe esté activo
            initNewPipe()

            val service = NewPipe.getService(youtubeServiceId)
            val streamInfo = StreamInfo.getInfo(service, url)

            // Buscamos preferentemente flujos de video que contengan pista de audio integrada (Muxed)
            // de lo contrario, tomamos el flujo de solo video con mayor resolución disponible.
            val videoStream = streamInfo.videoStreams.maxByOrNull { it.height ?: 0 }
                ?: streamInfo.videoOnlyStreams.maxByOrNull { it.height ?: 0 }

            videoStream?.url ?: url
        } catch (e: Exception) {
            e.printStackTrace()
            url // Retorna la URL original como respaldo si ocurre un fallo en el descifrado
        }
    }
}
