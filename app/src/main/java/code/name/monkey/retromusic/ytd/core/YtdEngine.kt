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

    return try {

        val streamInfo = YoutubeExtractor.extract(url)

        val streams = streamInfo.videoStreams
        val audioStreams = streamInfo.audioStreams

        when (mode) {

            Mode.VIDEO -> {

                val bestVideo = streams.maxByOrNull { it.height } // mejor calidad

                if (bestVideo != null) {
                    YtdResult.Video(
                        url = bestVideo.url,
                        quality = "${bestVideo.height}p"
                    )
                } else {
                    YtdResult.Error("No video streams")
                }
            }

            Mode.AUDIO -> {

                val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }

                if (bestAudio != null) {
                    YtdResult.Audio(
                        url = bestAudio.url,
                        mime = "audio/mp4"
                    )
                } else {
                    YtdResult.Error("No audio streams")
                }
            }
        }

    } catch (e: Exception) {
        YtdResult.Error("YouTube error: ${e.message}")
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
