package code.name.monkey.retromusic.ytd.platform

object PlatformDetector {

    enum class Platform {
        YOUTUBE,
        TIKTOK,
        FACEBOOK,
        UNKNOWN
    }

    fun detect(url: String): Platform {
        return when {
            url.contains("youtu.be") || url.contains("youtube.com") -> Platform.YOUTUBE
            url.contains("tiktok.com") -> Platform.TIKTOK
            url.contains("facebook.com") || url.contains("fb.watch") -> Platform.FACEBOOK
            else -> Platform.UNKNOWN
        }
    }
}
