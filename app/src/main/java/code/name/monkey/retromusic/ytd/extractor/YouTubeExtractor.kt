package code.name.monkey.retromusic.ytd.extractor

import org.schabi.newpipe.extractor.stream.StreamInfo

object YoutubeExtractor {

    fun extract(url: String): StreamInfo? {
        return try {
            StreamInfo.getInfo(url)
        } catch (e: Exception) {
            null
        }
    }
}
