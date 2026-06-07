package code.name.monkey.retromusic.fragments.home

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class RetroDownloader : Downloader() {
    
    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val url = URL(request.url())
        val connection = url.openConnection() as HttpURLConnection
        
        // 1. Configurar método y tiempos de espera para evitar bloqueos por lag
        connection.requestMethod = request.httpMethod()
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        
        // 2. Forzar un User-Agent real de Android para camuflar la petición
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        connection.setRequestProperty("Accept-Language", "es-ES,es;q=0.9")
        
        // 3. Añadir el resto de las cabeceras que NewPipe pida originalmente
        request.headers().forEach { (key, values) ->
            values.forEach { value -> 
                if (!key.equals("User-Agent", ignoreCase = true)) {
                    connection.addRequestProperty(key, value)
                }
            }
        }
        
        val responseCode = connection.responseCode
        
        // 4. Leer la respuesta manejando flujos de error de red de forma segura
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader()?.use { it.readText() } ?: ""
        
        return Response(responseCode, connection.responseMessage ?: "", connection.headerFields, responseBody, request.url())
    }
}
