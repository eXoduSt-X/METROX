package code.name.monkey.retromusic.fragments.home

import code.name.monkey.retromusic.databinding.FragmentHomeBinding

class HomeBinding(val binding: FragmentHomeBinding) {
    val root = binding.root
    
    // Acceso a la estructura principal
    val appBarLayout = binding.appBarLayout
    val toolbar = binding.appBarLayout.toolbar
    val contentContainer = binding.contentContainer
    val container = binding.container
    
    // Acceso a componentes de cabecera
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
    
    // Componentes de video y webview (Se garantiza acceso a través de homeContent)
    val videoDownloadContainer = homeContent.videoDownloadContainer
    val videoDownloadView = homeContent.videoDownloadView
    val youtubeWebView = homeContent.youtubeWebView
    val btnDownloadFloating = homeContent.btnDownloadFloating
    val btnLoadLocalVideo = homeContent.btnLoadLocalVideo
    
    // Opcionales: Si estos no existen en tu XML, el compilador fallará. 
    // Si tienes errores de "Unresolved reference", comenta estas dos líneas:
    val etDownloadUrl = homeContent.etDownloadUrl
    val btnClearUrl = homeContent.btnClearUrl
    val btnStartDownload = homeContent.btnStartDownload
}
