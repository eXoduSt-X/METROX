package code.name.monkey.retromusic.fragments.home

import code.name.monkey.retromusic.databinding.FragmentHomeBinding

class HomeBinding(
    private val binding: FragmentHomeBinding
) {
    val root = binding.root
    val container = binding.container
    val contentContainer = binding.contentContainer
    val appBarLayout = binding.appBarLayout
    val toolbar = binding.appBarLayout.toolbar
    
    // Referencia directa al layout incluido 'home_content'
    private val homeContent = binding.homeContent

    val bannerImage = binding.imageLayout.bannerImage
    val userImage = binding.imageLayout.userImage
    val titleWelcome = binding.imageLayout.titleWelcome
    
    // Acceso simplificado a los elementos dentro del include 'abs_playlists'
    val lastAdded = homeContent.absPlaylists.lastAdded
    val topPlayed = homeContent.absPlaylists.topPlayed
    val actionShuffle = homeContent.absPlaylists.actionShuffle
    val history = homeContent.absPlaylists.history
    
    // --- COMPONENTES DEL PANEL DE VIDEO (Dentro de homeContent) ---
    // NOTA: Si en home_content.xml definiste los botones dentro de un layout, 
    // el binding los expone directamente a través de 'homeContent'.
    val etDownloadUrl = homeContent.etDownloadUrl
    val btnClearUrl = homeContent.btnClearUrl
    val btnStartDownload = homeContent.btnStartDownload
    val btnLoadLocalVideo = homeContent.btnLoadLocalVideo
    val videoDownloadContainer = homeContent.videoDownloadContainer
    val videoDownloadView = homeContent.videoDownloadView

    // --- NAVEGADOR YOUTUBE ---
    val youtubeWebView = homeContent.youtubeWebView
    val btnDownloadFloating = homeContent.btnDownloadFloating
}
