package code.name.monkey.retromusic.ytd.extractor

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo

object YoutubeExtractor {

    init {
        NewPipe.init(null)
    }

    fun extract(url: String): StreamInfo {
        return StreamInfo.getInfo(url)
    }
}
