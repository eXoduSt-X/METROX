package code.name.monkey.retromusic.ytd.downloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object DownloadManagerClient {

    fun download(
        context: Context,
        url: String,
        fileName: String
    ): Long {

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Descargando contenido...")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }
}
