package code.name.monkey.retromusic.ytd.core

import code.name.monkey.retromusic.ytd.models.YtdResult
import code.name.monkey.retromusic.ytd.platform.PlatformDetector

object YtdEngine {

    enum class Mode {
        VIDEO,
        AUDIO
    }

    suspend fun resolve(url: String, mode: Mode): YtdResult {

        return try {

            val platform = PlatformDetector.detect(url)

            when (platform) {

                PlatformDetector.Platform.YOUTUBE -> {
                    resolveYouTube(url, mode)
                }

                PlatformDetector.Platform.TIKTOK -> {
                    resolveTikTok(url, mode)
                }

                PlatformDetector.Platform.FACEBOOK -> {
                    resolveFacebook(url, mode)
                }

                else -> {
                    YtdResult.Error("Plataforma no soportada")
                }
            }

        } catch (e: Exception) {
            YtdResult.Error(e.message ?: "Error desconocido")
        }
    }

    private fun resolveYouTube(url: String, mode: Mode): YtdResult {
        // MOCK (lo reemplazaremos por extractor real después)
        return when (mode) {

            Mode.VIDEO -> YtdResult.Video(
                url = "$url/video.mp4",
                quality = "720p"
            )

            Mode.AUDIO -> YtdResult.Audio(
                url = "$url/audio.m4a"
            )
        }
    }

    private fun resolveTikTok(url: String, mode: Mode): YtdResult {
        return YtdResult.Video(
            url = url,
            quality = "auto"
        )
    }

    private fun resolveFacebook(url: String, mode: Mode): YtdResult {
        return YtdResult.Video(
            url = url,
            quality = "auto"
        )
    }
}
