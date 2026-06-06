package code.name.monkey.retromusic.ytd.models

sealed class YtdResult {

    data class Video(
        val url: String,
        val quality: String
    ) : YtdResult()

    data class Audio(
        val url: String,
        val mime: String = "audio/mp4"
    ) : YtdResult()

    data class Error(
        val message: String
    ) : YtdResult()
}
