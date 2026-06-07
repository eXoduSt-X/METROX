package code.name.monkey.retromusic.ytd.core

import android.content.Context
import code.name.monkey.retromusic.ytd.models.YtdResult
import code.name.monkey.retromusic.ytd.platform.PlatformDetector
import code.name.monkey.retromusic.ytd.downloader.DownloadManagerClient
import code.name.monkey.retromusic.ytd.extractor.YoutubeExtractor

object YtdEngine {

    enum class Mode {
        VIDEO,
        AUDIO
    }

    suspend fun resolve(url: String, mode: Mode): YtdResult {
        return try {
            // ADICIÓN CRÍTICA: Inicializa los componentes de red de NewPipe de manera segura
            if (PlatformDetector.detect(url) == PlatformDetector.Platform.YOUTUBE) {
                YoutubeExtractor.initNewPipe()
            }
            val platform = PlatformDetector.detect(url)

            when (platform) {

                PlatformDetector.Platform.YOUTUBE -> resolveYouTube(url, mode)
                PlatformDetector.Platform.TIKTOK -> resolveTikTok(url, mode)
                PlatformDetector.Platform.FACEBOOK -> resolveFacebook(url, mode)

                else -> YtdResult.Error("Plataforma no soportada")
            }

        } catch (e: Exception) {
            YtdResult.Error(e.message ?: "Error desconocido")
        }
    }

    private fun resolveYouTube(url: String, mode: Mode): YtdResult {

        val extracted = YoutubeExtractor.extract(url)

        return when (mode) {

            Mode.VIDEO -> YtdResult.Video(
                url = extracted,
                quality = "fallback-auto"
            )

            Mode.AUDIO -> YtdResult.Audio(
                url = extracted,
                mime = "audio/mp4"
            )
        }
    }

    private fun resolveTikTok(url: String, mode: Mode): YtdResult {
        return when (mode) {

            Mode.VIDEO -> YtdResult.Video(url, "auto")
            Mode.AUDIO -> YtdResult.Audio(url, "audio/mp4")
        }
    }

    private fun resolveFacebook(url: String, mode: Mode): YtdResult {
        return when (mode) {

            Mode.VIDEO -> YtdResult.Video(url, "auto")
            Mode.AUDIO -> YtdResult.Audio(url, "audio/mp4")
        }
    }

    fun downloadResult(
        context: Context,
        result: YtdResult,
        fileName: String
    ) {
        when (result) {

            is YtdResult.Video ->
                DownloadManagerClient.download(context, result.url, "$fileName.mp4")

            is YtdResult.Audio ->
                DownloadManagerClient.download(context, result.url, "$fileName.m4a")

            is YtdResult.Error -> Unit
        }
    }
}
