package code.name.monkey.retromusic.ytd.extractor

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.URL

object YoutubeExtractor {

    init {
        NewPipe.init(null)
    }

    fun extract(url: String): StreamInfo {
        val service = NewPipe.getService(0) as YoutubeService
        val streamUrl = service.getStreamUrl(url)
        return StreamInfo.getInfo(streamUrl)
    }
}
