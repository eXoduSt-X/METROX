package code.name.monkey.retromusic.fragments.home

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.Toast
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.core.view.MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import code.name.monkey.appthemehelper.common.ATHToolbarActivity
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.retromusic.*
import code.name.monkey.retromusic.databinding.FragmentHomeBinding
import code.name.monkey.retromusic.dialogs.CreatePlaylistDialog
import code.name.monkey.retromusic.dialogs.ImportPlaylistDialog
import code.name.monkey.retromusic.extensions.dip
import code.name.monkey.retromusic.extensions.elevatedAccentColor
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.glide.RetroGlideExtension
import code.name.monkey.retromusic.glide.RetroGlideExtension.profileBannerOptions
import code.name.monkey.retromusic.glide.RetroGlideExtension.userProfileOptions
import code.name.monkey.retromusic.interfaces.IScrollHelper
import code.name.monkey.retromusic.util.PreferenceUtil.userName
import com.bumptech.glide.Glide
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis

// 1. Modelo de datos para los subtítulos
data class Subtitle(val startTime: Long, val endTime: Long, val text: String)

class HomeFragment : AbsMainActivityFragment(R.layout.fragment_home), IScrollHelper {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

   // private val navOptions = androidx.navigation.NavOptions.Builder().build()
   // private val libraryViewModel by androidx.fragment.app.viewModels<code.name.monkey.retromusic.fragments.library.LibraryViewModel>()

    private val subtitleList = mutableListOf<Subtitle>()
    private val handler = Handler(Looper.getMainLooper())
    
    // Motor de sincronización
    private val updateSubtitleTask = object : Runnable {
    override fun run() {
        if (binding.homeContent.videoPlayer.isPlaying) {
            val player = binding.homeContent.videoPlayer
            val currentPos = player.currentPosition
            
            // Actualizar Subtítulos
            val currentSub = subtitleList.find { currentPos.toLong() in it.startTime..it.endTime }
            binding.homeContent.tvSubtitleOverlay.text = currentSub?.text ?: ""
            
            // Actualizar SeekBar (¡Aquí está la magia!)
            binding.homeContent.videoSeekBar.max = player.duration
            binding.homeContent.videoSeekBar.progress = currentPos
            }
            handler.postDelayed(this, 500)
        }
    }

    private val subtitlePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                requireContext().contentResolver.openInputStream(it)?.use { stream ->
                    parseSrt(stream)
                    Toast.makeText(requireContext(), "Subtítulos cargados", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al leer subtítulos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseSrt(inputStream: java.io.InputStream) {
        subtitleList.clear()
        val lines = inputStream.bufferedReader().readLines()
        for (i in lines.indices) {
            if (lines[i].contains("-->")) {
                val times = lines[i].split(" --> ")
                val start = parseTimeToMillis(times[0].trim())
                val end = parseTimeToMillis(times[1].trim())
                if (i + 1 < lines.size) subtitleList.add(Subtitle(start, end, lines[i + 1]))
            }
        }
    }

    private fun parseTimeToMillis(time: String): Long {
        val parts = time.replace(",", ":").split(":")
        return (parts[0].toLong() * 3600000) + (parts[1].toLong() * 60000) + (parts[2].toLong() * 1000) + parts[3].toLong()
    }

    // Playlist en memoria
    private val videoPlaylist = mutableListOf<Uri>()
    private var currentIndex = 0

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            videoPlaylist.clear()
            videoPlaylist.addAll(uris)
            currentIndex = 0
            reproducirVideoActual()
        }
    }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    _binding = FragmentHomeBinding.bind(view)
// Inicializar la barra de progreso
   binding.homeContent.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
         if (fromUser) {
             binding.homeContent.videoPlayer.seekTo(progress)
         }
     }
    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
})

// Actualizar la barra mientras el video corre (dentro de un Handler o Timer)
// Puedes añadir esto al final de tu 'updateSubtitleTask' que ya tienes:
// binding.homeContent.videoSeekBar.max = binding.homeContent.videoPlayer.duration
// binding.homeContent.videoSeekBar.progress = binding.homeContent.videoPlayer.currentPosition
    // Configura la sombra a través del binding del include
    binding.homeContent.tvSubtitleOverlay.setShadowLayer(
        3f, 2f, 2f, android.graphics.Color.BLACK
    )

        // Iniciar el motor de sincronización
        handler.post(updateSubtitleTask)

        setupListeners()
        setupVideoListeners()
        
        binding.imageLayout.titleWelcome.text = String.format("%s", userName)

        enterTransition = MaterialFadeThrough().addTarget(binding.contentContainer)
        reenterTransition = MaterialFadeThrough().addTarget(binding.contentContainer)

        checkForMargins()
        loadProfile()
        setupTitle()
        colorButtons()
        
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        view.doOnLayout { adjustPlaylistButtons() }
    }

private fun setupVideoListeners() {
        binding.homeContent.btnOpenFile.setOnClickListener { videoPickerLauncher.launch("video/*") }
        binding.homeContent.btnLoadSubtitles.setOnClickListener { subtitlePickerLauncher.launch("*/*") }
           
        // Inicializar la barra de progreso
    binding.homeContent.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
       override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            binding.homeContent.videoPlayer.seekTo(progress)
        }
    }
       override fun onStartTrackingTouch(seekBar: SeekBar?) {}
       override fun onStopTrackingTouch(seekBar: SeekBar?) {}
   })
    // Controles de tiempo
     binding.homeContent.btnRewindTime.setOnClickListener {
        val player = binding.homeContent.videoPlayer
        val newPos = player.currentPosition - 5000
        player.seekTo(if (newPos < 0) 0 else newPos)
    }

     binding.homeContent.btnForwardTime.setOnClickListener {
        val player = binding.homeContent.videoPlayer
        val newPos = player.currentPosition + 5000
        player.seekTo(if (newPos > player.duration) player.duration else newPos)
    }

    // Controles de Navegación de lista
      binding.homeContent.btnPrevVideo.setOnClickListener {
        if (currentIndex > 0) {
            currentIndex--
            reproducirVideoActual()
        }
    }

       binding.homeContent.btnNextVideo.setOnClickListener {
        if (currentIndex < videoPlaylist.size - 1) {
            currentIndex++
            reproducirVideoActual()
        }
    }
        binding.homeContent.btnPlayPause.setOnClickListener {
            if (binding.homeContent.videoPlayer.isPlaying) {
                binding.homeContent.videoPlayer.pause()
                binding.homeContent.btnPlayPause.text = "Play"
            } else {
                binding.homeContent.videoPlayer.start()
                binding.homeContent.btnPlayPause.text = "Pause"
            }
        }

    }

    private fun reproducirVideoActual() {
        if (videoPlaylist.isNotEmpty()) {
            binding.homeContent.videoPlayer.setVideoURI(videoPlaylist[currentIndex])
            binding.homeContent.videoPlayer.start()
            binding.homeContent.btnPlayPause.text = "Pause"
        }
    }

    // --- MÉTODOS ORIGINALES MANTENIDOS ---
    private fun adjustPlaylistButtons() {
        val buttons = listOf(binding.homeContent.absPlaylists.history, binding.homeContent.absPlaylists.lastAdded, binding.homeContent.absPlaylists.topPlayed, binding.homeContent.absPlaylists.actionShuffle)
        buttons.maxOf { it.lineCount }.let { maxLineCount -> buttons.forEach { it.setLines(maxLineCount) } }
    }

    private fun setupListeners() {
    binding.imageLayout.bannerImage?.setOnClickListener {
    findNavController().navigate(R.id.user_info_fragment, null, null, FragmentNavigatorExtras(binding.imageLayout.userImage to "user_image"))
            reenterTransition = null
        }
        binding.homeContent.absPlaylists.lastAdded.setOnClickListener { findNavController().navigate(R.id.detailListFragment, bundleOf(EXTRA_PLAYLIST_TYPE to LAST_ADDED_PLAYLIST)); setSharedAxisYTransitions() }
        binding.homeContent.absPlaylists.topPlayed.setOnClickListener { findNavController().navigate(R.id.detailListFragment, bundleOf(EXTRA_PLAYLIST_TYPE to TOP_PLAYED_PLAYLIST)); setSharedAxisYTransitions() }
        binding.homeContent.absPlaylists.actionShuffle.setOnClickListener { libraryViewModel.shuffleSongs() }
        binding.homeContent.absPlaylists.history.setOnClickListener { findNavController().navigate(R.id.detailListFragment, bundleOf(EXTRA_PLAYLIST_TYPE to HISTORY_PLAYLIST)); setSharedAxisYTransitions() }
        binding.imageLayout.userImage.setOnClickListener {
            findNavController().navigate(R.id.user_info_fragment, null, null, FragmentNavigatorExtras(binding.imageLayout.userImage to "user_image"))
        }
    }

    private fun setupTitle() {
        binding.appBarLayout.toolbar.setNavigationOnClickListener { findNavController().navigate(R.id.action_search, null, navOptions) }
        binding.appBarLayout.title = getString(R.string.app_name)
    }

    private fun loadProfile() {
        binding.imageLayout.bannerImage?.let { Glide.with(requireContext()).load(RetroGlideExtension.getBannerModel()).profileBannerOptions(RetroGlideExtension.getBannerModel()).into(it) }
        Glide.with(requireActivity()).load(RetroGlideExtension.getUserModel()).userProfileOptions(RetroGlideExtension.getUserModel(), requireContext()).into(binding.imageLayout.userImage)
    }

    fun colorButtons() {
        binding.homeContent.absPlaylists.history.elevatedAccentColor()
        binding.homeContent.absPlaylists.lastAdded.elevatedAccentColor()
        binding.homeContent.absPlaylists.topPlayed.elevatedAccentColor()
        binding.homeContent.absPlaylists.actionShuffle.elevatedAccentColor()
    }

  private fun checkForMargins() {
    if (mainActivity.isBottomNavVisible) {
        binding.container.updateLayoutParams<ViewGroup.MarginLayoutParams> { 
            bottomMargin = dip(R.dimen.bottom_nav_height) 
        }
    }
}
override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        
        menu.removeItem(R.id.action_grid_size)
        menu.removeItem(R.id.action_layout_type)
        menu.removeItem(R.id.action_sort_order)
        
        menu.findItem(R.id.action_settings)?.let {
            it.setShowAsAction(1)
        }
        
        val toolbar = binding.appBarLayout.toolbar
        ToolbarContentTintHelper.handleOnCreateOptionsMenu(
            requireContext(),
            toolbar,
            menu,
            ATHToolbarActivity.getToolbarBackgroundColor(toolbar)
        )
    }

    override fun scrollToTop() {
        binding.container.scrollTo(0, 0)
        binding.appBarLayout.setExpanded(true)
    }

    fun setSharedAxisYTransitions() {
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true).addTarget(CoordinatorLayout::class.java)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)
    }

    companion object {
        const val TAG: String = "BannerHomeFragment"
        @JvmStatic fun newInstance(): HomeFragment = HomeFragment()
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                findNavController().navigate(R.id.settings_fragment, null, navOptions)
                true
            }
            R.id.action_import_playlist -> {
                ImportPlaylistDialog().show(childFragmentManager, "ImportPlaylist")
                true
            }
            R.id.action_add_to_playlist -> {
                CreatePlaylistDialog.create(emptyList()).show(childFragmentManager, "ShowCreatePlaylistDialog")
                true
            }
            else -> false
        }
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        ToolbarContentTintHelper.handleOnPrepareOptionsMenu(requireActivity(), binding.appBarLayout.toolbar)
    }

    override fun onResume() {
        super.onResume()
        checkForMargins()
        exitTransition = null
    }

    override fun onDestroyView() {
        handler.removeCallbacks(updateSubtitleTask)
        binding.homeContent.videoPlayer.stopPlayback()
        _binding = null
        super.onDestroyView()
    }
}
