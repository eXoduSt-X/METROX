package code.name.monkey.retromusic.fragments.home

import code.name.monkey.retromusic.databinding.FragmentHomeBinding

class HomeBinding(val binding: FragmentHomeBinding) {
    val root = binding.root
    val appBarLayout = binding.appBarLayout
    val toolbar = binding.appBarLayout.toolbar
    val contentContainer = binding.contentContainer
    val container = binding.container
    val titleWelcome = binding.imageLayout.titleWelcome
    val bannerImage = binding.imageLayout.bannerImage
    val userImage = binding.imageLayout.userImage
    
    private val homeContent = binding.homeContent
    val absPlaylists = homeContent.absPlaylists
    val lastAdded = homeContent.absPlaylists.lastAdded
    val topPlayed = homeContent.absPlaylists.topPlayed
    val actionShuffle = homeContent.absPlaylists.actionShuffle
    val history = homeContent.absPlaylists.history
    
    val videoDownloadContainer = homeContent.videoDownloadContainer
    val videoDownloadView = homeContent.videoDownloadView
    val youtubeWebView = homeContent.youtubeWebView
    val btnDownloadFloating = homeContent.btnDownloadFloating
    val btnLoadLocalVideo = homeContent.btnLoadLocalVideo
    
    // Comentadas para evitar error de compilación por falta de ID en XML
    // val etDownloadUrl = homeContent.etDownloadUrl
    // val btnClearUrl = homeContent.btnClearUrl
    // val btnStartDownload = homeContent.btnStartDownload
}
