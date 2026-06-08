package code.name.monkey.retromusic.fragments.home

import code.name.monkey.retromusic.databinding.FragmentHomeBinding

class HomeBinding(val binding: FragmentHomeBinding) {
    // Exponemos el binding original para que el Fragment pueda acceder a todo
    val root = binding.root
    
    // --- ACCESO A ESTRUCTURA DE CABECERA ---
    // EXPONEMOS APP BAR PARA PODER HACER .setExpanded(true)
    val appBarLayout = binding.appBarLayout 
    // EXPONEMOS TOOLBAR PARA EL TÍTULO
    val toolbar = binding.appBarLayout.toolbar
    
    val contentContainer = binding.contentContainer
    val container = binding.container
    val titleWelcome = binding.imageLayout.titleWelcome
    val bannerImage = binding.imageLayout.bannerImage
    val userImage = binding.imageLayout.userImage
    
    // Referencias dentro del include 'home_content'
    private val homeContent = binding.homeContent
    val absPlaylists = homeContent.absPlaylists
    val lastAdded = homeContent.absPlaylists.lastAdded
    val topPlayed = homeContent.absPlaylists.topPlayed
    val actionShuffle = homeContent.absPlaylists.actionShuffle
    val history = homeContent.absPlaylists.history
    
    // Componentes de video y webview
    val etDownloadUrl = homeContent.etDownloadUrl
    val btnClearUrl = homeContent.btnClearUrl
    val btnStartDownload = homeContent.btnStartDownload
    val btnLoadLocalVideo = homeContent.btnLoadLocalVideo
    val videoDownloadContainer = homeContent.videoDownloadContainer
    val videoDownloadView = homeContent.videoDownloadView
    val youtubeWebView = homeContent.youtubeWebView
    val btnDownloadFloating = homeContent.btnDownloadFloating
}
