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
import android.Manifest
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.ContentUris
import android.provider.MediaStore
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.Intent

// 1. Modelo de datos para los subtítulos
data class Subtitle(val startTime: Long, val endTime: Long, val text: String)

class HomeFragment : AbsMainActivityFragment(R.layout.fragment_home), IScrollHelper {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var savedPosition: Int = 0 
    private var selectedFolderUri: Uri? = null
    private val videoPlaylist = mutableListOf<Uri>()
    private var currentIndex = 0
   // private val navOptions = androidx.navigation.NavOptions.Builder().build()
   // private val libraryViewModel by androidx.fragment.app.viewModels<code.name.monkey.retromusic.fragments.library.LibraryViewModel>()
    private val downloadVideoList = mutableListOf<Pair<String, Uri>>()
    private val subtitleList = mutableListOf<Subtitle>()
    private val handler = Handler(Looper.getMainLooper())
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadVideosFromDownloads()
        } else {
            Toast.makeText(requireContext(), "Permiso denegado, no podemos cargar videos", Toast.LENGTH_SHORT).show()
        }
    }
    private val updateSubtitleTask = object : Runnable {
        override fun run() {
            if (binding.homeContent.videoPlayer.isPlaying) {
                val player = binding.homeContent.videoPlayer
                val currentPos = player.currentPosition

                // Actualizar Subtítulos
                val currentSub = subtitleList.find { currentPos.toLong() in it.startTime..it.endTime }
                binding.homeContent.tvSubtitleOverlay.text = currentSub?.text ?: ""

                // Actualizar SeekBar y Tiempos
                binding.homeContent.videoSeekBar.max = player.duration
                binding.homeContent.videoSeekBar.progress = currentPos

                binding.homeContent.tvCurrentTime.text = formatTime(currentPos)
                binding.homeContent.tvTotalTime.text = formatTime(player.duration)
            }
            handler.postDelayed(this, 500)
        }
    }
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == android.app.Activity.RESULT_OK) {
           result.data?.data?.let { uri ->
                requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectedFolderUri = uri
                selectedFolderUri = uri
                loadVideosFromSelectedFolder(uri)
              }
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
    private fun loadVideosFromSelectedFolder(uri: Uri) {
    downloadVideoList.clear()
    val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(requireContext(), uri)
    
    pickedDir?.listFiles()?.forEach { file ->
        // Filtramos solo archivos de video
        if (file.type?.startsWith("video/") == true) {
            downloadVideoList.add(Pair(file.name ?: "Video", file.uri))
        }
    }
    
    // Actualizamos el adaptador
    binding.homeContent.rvDownloads.adapter = DownloadVideoAdapter(downloadVideoList) { videoUri ->
        videoPlaylist.clear()
        videoPlaylist.add(videoUri)
        currentIndex = 0
        reproducirVideoActual()
    }
}
        
        // --- AQUÍ CONECTAMOS EL ADAPTADOR ---
        binding.homeContent.rvDownloads.adapter = DownloadVideoAdapter(downloadVideoList) { uri ->
            videoPlaylist.clear()
            videoPlaylist.add(uri)
            currentIndex = 0
            reproducirVideoActual()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        binding.homeContent.rvDownloads.layoutManager = LinearLayoutManager(requireContext())

        // LANZAMIENTO DE PERMISOS
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_VIDEO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            loadVideosFromDownloads()
        } else {
            requestPermissionLauncher.launch(permission)
        }
        val savedUri = loadSavedFolderUri()
        if (savedUri != null) {
        loadVideosFromSelectedFolder(savedUri)
        } else {
            loadVideosFromDownloads() 
        }
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
        // Botones de archivos
        binding.homeContent.btnOpenFile.setOnClickListener { videoPickerLauncher.launch("video/*") }
        binding.homeContent.btnLoadSubtitles.setOnClickListener { subtitlePickerLauncher.launch("*/*") }

        // SeekBar
        binding.homeContent.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.homeContent.videoPlayer.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        binding.homeContent.videoPlayer.setOnPreparedListener { mp ->
            mp.seekTo(savedPosition)
            mp.start()
            binding.homeContent.videoSeekBar.max = mp.duration
            binding.homeContent.tvTotalTime.text = formatTime(mp.duration)
        }

        // Navegación
        binding.homeContent.btnPrevVideo.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                reproducirVideoActual()
            }
        }

        binding.homeContent.btnRewindTime.setOnClickListener {
            val player = binding.homeContent.videoPlayer
            val newPos = (player.currentPosition - 5000).coerceAtLeast(0)
            player.seekTo(newPos)
        }

        binding.homeContent.btnForwardTime.setOnClickListener {
            val player = binding.homeContent.videoPlayer
            val newPos = (player.currentPosition + 5000).coerceAtMost(player.duration)
            player.seekTo(newPos)
        }

        binding.homeContent.btnNextVideo.setOnClickListener {
            if (currentIndex < videoPlaylist.size - 1) {
                currentIndex++
                reproducirVideoActual()
            }
        }
        // En setupVideoListeners():
       binding.homeContent.btnChooseFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            folderPickerLauncher.launch(intent)
        }
        // Estado y Pantalla
        binding.homeContent.btnPlayPause.setOnClickListener {
            val player = binding.homeContent.videoPlayer
            if (player.isPlaying) {
                player.pause()
                binding.homeContent.btnPlayPause.text = "Play"
            } else {
                player.start()
                binding.homeContent.btnPlayPause.text = "Pause"
            }
        }

        binding.homeContent.btnFullscreen.setOnClickListener {
            requireActivity().requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            val decorView = requireActivity().window.decorView
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun reproducirVideoActual() {
        if (videoPlaylist.isNotEmpty()) {
            savedPosition = 0
            binding.homeContent.videoPlayer.setVideoURI(videoPlaylist[currentIndex])
            binding.homeContent.videoPlayer.start()
            binding.homeContent.btnPlayPause.text = "Pause"
        }
    }

    private fun formatTime(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
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

    private fun saveFolderUri(uri: Uri) {
    requireContext().getSharedPreferences("video_prefs", android.content.Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_SELECTED_FOLDER_URI, uri.toString())
        .apply()
    }
    
    private fun loadSavedFolderUri(): Uri? {
    val uriString = requireContext().getSharedPreferences("video_prefs", android.content.Context.MODE_PRIVATE)
        .getString(PREF_SELECTED_FOLDER_URI, null)
    return uriString?.let { Uri.parse(it) }
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        val content = binding.homeContent
        val isLandscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val visibility = if (isLandscape) View.GONE else View.VISIBLE

        // 1. Ocultar la barra superior (referencia confirmada)
        binding.appBarLayout.visibility = visibility

        // 2. Ocultar controles individualmente (usando los IDs que autocompleta el IDE)
        content.videoSeekBar.visibility = visibility
        content.btnPrevVideo.visibility = visibility
        content.btnRewindTime.visibility = visibility
        content.btnForwardTime.visibility = visibility
        content.btnNextVideo.visibility = visibility
        content.btnPlayPause.visibility = visibility
        content.btnFullscreen.visibility = visibility
        content.btnOpenFile.visibility = visibility
        content.btnLoadSubtitles.visibility = visibility
        content.tvCurrentTime.visibility = visibility
        content.tvTotalTime.visibility = visibility
        content.btnChooseFolder.visibility = visibility

        // 3. Ajustar el contenedor de video a pantalla completa en horizontal
        content.videoContainer.layoutParams.height = if (isLandscape)
            ViewGroup.LayoutParams.MATCH_PARENT else (250 * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val PREF_SELECTED_FOLDER_URI = "pref_selected_folder_uri"
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
        if (binding.homeContent.videoPlayer.isPlaying) {
            savedPosition = binding.homeContent.videoPlayer.currentPosition
        }
        
        handler.removeCallbacks(updateSubtitleTask)
        binding.homeContent.videoPlayer.stopPlayback()
        _binding = null
        super.onDestroyView()
    }
}
