package code.name.monkey.retromusic.fragments.home

import code.name.monkey.retromusic.databinding.FragmentHomeBinding

class HomeBinding(
    homeBinding: FragmentHomeBinding
) {
    val root = homeBinding.root
    val container = homeBinding.container
    val contentContainer = homeBinding.contentContainer
    val appBarLayout = homeBinding.appBarLayout
    val toolbar = homeBinding.appBarLayout.toolbar
    val bannerImage = homeBinding.imageLayout.bannerImage
    val userImage = homeBinding.imageLayout.userImage
    val lastAdded = homeBinding.homeContent.absPlaylists.lastAdded
    val topPlayed = homeBinding.homeContent.absPlaylists.topPlayed
    val actionShuffle = homeBinding.homeContent.absPlaylists.actionShuffle
    val history = homeBinding.homeContent.absPlaylists.history
    val titleWelcome = homeBinding.imageLayout.titleWelcome
    
    // --- NUEVAS VISTAS PARA EL DESCARGADOR Y VIDEOVIEW ---
    val etDownloadUrl = homeBinding.homeContent.etDownloadUrl
    val btnStartDownload = homeBinding.homeContent.btnStartDownload
    val videoDownloadContainer = homeBinding.homeContent.videoDownloadContainer
    val videoDownloadView = homeBinding.homeContent.videoDownloadView
}
