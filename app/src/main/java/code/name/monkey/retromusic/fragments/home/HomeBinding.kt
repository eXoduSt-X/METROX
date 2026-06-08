package code.name.monkey.retromusic.fragments.home

import code.name.monkey.retromusic.databinding.FragmentHomeBinding
import android.view.View

class HomeBinding(binding: FragmentHomeBinding) {
    val root = binding.root
    val container = binding.container
    val contentContainer = binding.contentContainer
    val appBarLayout = binding.appBarLayout
    val toolbar = binding.appBarLayout.toolbar
    
    // Buscamos los IDs directamente en el root del FragmentHomeBinding
    val lastAdded = binding.root.findViewById<View>(code.name.monkey.retromusic.R.id.lastAdded)
    val topPlayed = binding.root.findViewById<View>(code.name.monkey.retromusic.R.id.topPlayed)
    val actionShuffle = binding.root.findViewById<View>(code.name.monkey.retromusic.R.id.actionShuffle)
    val history = binding.root.findViewById<View>(code.name.monkey.retromusic.R.id.history)
    
    val etDownloadUrl = binding.root.findViewById<View>(code.name.monkey.retromusic.R.id.etDownloadUrl)
    val btnClearUrl = binding.root.findViewById<View>(code.name.monkey.retromusic.R.id.btnClearUrl)
    val btnStartDownload = binding.root.findViewById<View>(code.name.monkey.retromusic.R.id.btnStartDownload)
    val btnLoadLocalVideo = binding.root.findViewById<View>(code.name.monkey.retromusic.R.id.btnLoadLocalVideo)
    val videoDownloadContainer = binding.root.findViewById<View>(code.name.monkey.retromusic.R.id.videoDownloadContainer)
    val videoDownloadView = binding.root.findViewById<View>(code.name.monkey.retromusic.R.id.videoDownloadView)

    val youtubeWebView = binding.root.findViewById<android.webkit.WebView>(code.name.monkey.retromusic.R.id.youtubeWebView)
    val btnDownloadFloating = binding.root.findViewById<View>(code.name.monkey.retromusic.R.id.btnDownloadFloating)
}
