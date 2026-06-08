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
    val btnLoadLocalVideo = homeContent.btnLoadLocalVideo
    
    // Comentado para evitar el error de "Unresolved Reference"
    // val btnDownloadFloating = homeContent.btnDownloadFloating
}
