package code.name.monkey.retromusic.fragments.home

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.request.Request
import org.schabi.newpipe.extractor.downloader.request.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class RetroDownloader : Downloader() {
    
    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val url = URL(request.url())
        val connection = url.openConnection() as HttpURLConnection
        
        // Configuramos las cabeceras requeridas
        connection.requestMethod = request.httpMethod()
        request.headers().forEach { (key, values) ->
            values.forEach { value -> connection.addRequestProperty(key, value) }
        }
        
        val responseCode = connection.responseCode
        val responseMessage = connection.responseMessage
        val responseBody = connection.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        
        return Response(responseCode, responseMessage, connection.headerFields, responseBody, request.url())
    }
}
