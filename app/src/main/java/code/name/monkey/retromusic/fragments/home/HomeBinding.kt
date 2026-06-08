package code.name.monkey.retromusic.fragments.home

import code.name.monkey.retromusic.databinding.FragmentHomeBinding

class HomeBinding(val binding: FragmentHomeBinding) {
    val root = binding.root
    
    // Cabecera y Toolbar
    val appBarLayout = binding.appBarLayout
    val toolbar = binding.appBarLayout.toolbar
    val contentContainer = binding.contentContainer
    val container = binding.container
    
    // Perfil
    val titleWelcome = binding.imageLayout.titleWelcome
    val bannerImage = binding.imageLayout.bannerImage
    val userImage = binding.imageLayout.userImage
    
    // Playlists (Acceso a través de homeContent)
    private val homeContent = binding.homeContent
    val absPlaylists = homeContent.absPlaylists
    val lastAdded = homeContent.absPlaylists.lastAdded
    val topPlayed = homeContent.absPlaylists.topPlayed
    val actionShuffle = homeContent.absPlaylists.actionShuffle
    val history = homeContent.absPlaylists.history
    
    // Video y WebView
    val youtubeWebView = homeContent.youtubeWebView
    val videoDownloadContainer = homeContent.videoDownloadContainer
    val videoDownloadView = homeContent.videoDownloadView
    val btnDownloadFloating = homeContent.btnDownloadFloating
    val btnLoadLocalVideo = homeContent.btnLoadLocalVideo
    
    // Botones adicionales (si tu XML los tiene)
    val btnStartDownload = homeContent.btnStartDownload
    
    // NOTA: Si en el futuro agregas los IDs al XML, 
    // puedes descomentar estas líneas:
    // val etDownloadUrl = homeContent.etDownloadUrl
    // val btnClearUrl = homeContent.btnClearUrl
}
