package code.name.monkey.retromusic.ytd.extractor

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfo
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

object YoutubeExtractor {

    private var isInitialized = false
    private val youtubeServiceId = YoutubeService(0).serviceId

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Implementación con la firma exacta de NewPipe v0.24.3
    private val appDownloader = object : Downloader() {
        @Throws(IOException::class, ReCaptchaException::class)
        override fun execute(request: Request): Response {
            val url = request.url()
            val method = request.httpMethod() // FIRMA CORREGIDA
            val headers = request.headers()    // FIRMA CORREGIDA
            val body = request.body()          // FIRMA CORREGIDA

            val builder = okhttp3.Request.Builder().url(url)
            
            // Inyección segura de cabeceras mapeadas
            headers?.forEach { (key, values) ->
                values.forEach { value -> builder.addHeader(key, value) }
            }
            
            if (builder.build().header("User-Agent") == null) {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }

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

    fun extract(url: String): String {
        return try {
            initNewPipe()

            val service = NewPipe.getService(youtubeServiceId)
            val streamInfo = StreamInfo.getInfo(service, url)

            // Buscar flujo con audio embebido, o el video con mayor resolución disponible
            val videoStream = streamInfo.videoStreams.maxByOrNull { it.height ?: 0 }
                ?: streamInfo.videoOnlyStreams.maxByOrNull { it.height ?: 0 }

            videoStream?.url ?: url
        } catch (e: Exception) {
            e.printStackTrace()
            url
        }
    }
}
