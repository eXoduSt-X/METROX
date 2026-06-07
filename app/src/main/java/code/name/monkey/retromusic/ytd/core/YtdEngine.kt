package code.name.monkey.retromusic.ytd.core

import android.content.Context
import code.name.monkey.retromusic.ytd.models.YtdResult
import code.name.monkey.retromusic.ytd.platform.PlatformDetector
import code.name.monkey.retromusic.ytd.downloader.DownloadManagerClient

object YtdEngine {

    enum class Mode {
        VIDEO,
        AUDIO
    }

    // =========================
    // ENTRY POINT (RESOLVER)
    // =========================
    suspend fun resolve(url: String, mode: Mode): YtdResult {

        return try {

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

    // =========================
    // YOUTUBE (BASE REALISTA)
    // =========================
    private fun resolveYouTube(url: String, mode: Mode): YtdResult {

    val streamInfo = YoutubeExtractor.extract(url)
        ?: return YtdResult.Error("Extractor no disponible en CI")

    val videos = streamInfo.videoStreams ?: emptyList()
    val audios = streamInfo.audioStreams ?: emptyList()

    if (videos.isEmpty() && audios.isEmpty()) {
        return YtdResult.Error("No streams disponibles")
    }

    return when (mode) {

        Mode.VIDEO -> {
            val best = videos.maxByOrNull { it.height ?: 0 }
            if (best != null) {
                YtdResult.Video(best.url, "${best.height}p")
            } else {
                YtdResult.Error("No video streams")
            }
        }

        Mode.AUDIO -> {
            val best = audios.maxByOrNull { it.averageBitrate ?: 0 }
            if (best != null) {
                YtdResult.Audio(best.url, best.averageBitrate)
            } else {
                YtdResult.Error("No audio streams")
            }
        }
    }
}
    // =========================
    // TIKTOK (LAZY + FALLBACK)
    // =========================
    private fun resolveTikTok(url: String, mode: Mode): YtdResult {
        return when (mode) {

            Mode.VIDEO -> YtdResult.Video(
                url = url,
                quality = "auto"
            )

            Mode.AUDIO -> YtdResult.Audio(
                url = url,
                mime = "audio/mp4"
            )
        }
    }

    // =========================
    // FACEBOOK (LAZY)
    // =========================
    private fun resolveFacebook(url: String, mode: Mode): YtdResult {
        return when (mode) {

            Mode.VIDEO -> YtdResult.Video(
                url = url,
                quality = "auto"
            )

            Mode.AUDIO -> YtdResult.Audio(
                url = url,
                mime = "audio/mp4"
            )
        }
    }

    // =========================
    // 🔥 DESCARGA REAL
    // =========================
    fun downloadResult(
        context: Context,
        result: YtdResult,
        fileName: String
    ) {

        when (result) {

            is YtdResult.Video -> {
                DownloadManagerClient.download(
                    context,
                    result.url,
                    "$fileName.mp4"
                )
            }

            is YtdResult.Audio -> {
                DownloadManagerClient.download(
                    context,
                    result.url,
                    "$fileName.m4a"
                )
            }

            is YtdResult.Error -> {
                // no hace nada
            }
        }
    }
}
