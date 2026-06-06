package code.name.monkey.retromusic.fragments.home

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
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
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.interfaces.IScrollHelper
import code.name.monkey.retromusic.util.PreferenceUtil.userName
import com.bumptech.glide.Glide
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HomeFragment :
    AbsMainActivityFragment(R.layout.fragment_home), IScrollHelper {

    private var _binding: HomeBinding? = null
    private val binding get() = _binding!!

    // Registro del Selector de Archivos para cargar Videos Locales (.mp4)
    private val selectLocalVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            reproducirVideoEnPanel(it)
            Toast.makeText(requireContext(), "Video local cargado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val homeBinding = FragmentHomeBinding.bind(view)
        _binding = HomeBinding(homeBinding)
        mainActivity.setSupportActionBar(binding.toolbar)
        mainActivity.supportActionBar?.title = null
        setupListeners()
        binding.titleWelcome.text = String.format("%s", userName)

        enterTransition = MaterialFadeThrough().addTarget(binding.contentContainer)
        reenterTransition = MaterialFadeThrough().addTarget(binding.contentContainer)

        checkForMargins()

        // --- MANEJO DE EVENTOS DEL PANEL DE DESCARGAS ---
        
        binding.btnStartDownload.setOnClickListener {
            val urlIntroducida = binding.etDownloadUrl.text.toString().trim()
            if (urlIntroducida.isNotEmpty() && urlIntroducida.startsWith("http")) {
                procesarEnlaceHome(urlIntroducida)
                binding.etDownloadUrl.setText("") // Resetea el cuadro automáticamente
            } else {
                Toast.makeText(requireContext(), "Por favor introduce un enlace válido", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearUrl.setOnClickListener {
            binding.etDownloadUrl.setText("")
        }

        binding.btnLoadLocalVideo.setOnClickListener {
            selectLocalVideoLauncher.launch("video/*")
        }

        loadProfile()
        setupTitle()
        colorButtons()
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        view.doOnLayout {
            adjustPlaylistButtons()
        }
    }

    private fun reproducirVideoEnPanel(videoUri: Uri) {
        binding.videoDownloadContainer.visibility = View.VISIBLE
        binding.videoDownloadView.setVideoURI(videoUri)
        binding.videoDownloadView.requestFocus()
        binding.videoDownloadView.start()
    }

    private fun procesarEnlaceHome(urlVideo: String) {
        Toast.makeText(requireContext(), "Analizando flujo de video...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiUrL = URL("https://api.cobalt.tools/api/json")
                val conn = apiUrL.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true

                val jsonInput = JSONObject().apply {
                    put("url", urlVideo)
                    put("videoQuality", "720")
                    put("isAudioOnly", false)
                }

                conn.outputStream.use { os ->
                    val input = jsonInput.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = conn.responseCode
                if (responseCode == 200 || responseCode == 201) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    val urlDirecta = jsonResponse.optString("url")

                    if (urlDirecta.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            reproducirVideoEnPanel(Uri.parse(urlDirecta))

                            val cancionActual = MusicPlayerRemote.currentSong
                            val nombreSeguro = if (!cancionActual.title.isNullOrEmpty()) {
                                "${cancionActual.title} - ${cancionActual.artistName}".replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                            } else {
                                "Metraje_Sincro_${System.currentTimeMillis()}"
                            }
                            
                            ejecutarDescargaDelSistema(urlDirecta, "$nombreSeguro.mp4")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error del servidor: $responseCode", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Fallo de conexión", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun ejecutarDescargaDelSistema(url: String, nombreArchivo: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("Descargando metraje...")
                setDescription(nombreArchivo)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nombreArchivo)
                setMimeType("video/mp4")
            }
            val manager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(requireContext(), "Descarga iniciada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error de descarga: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun adjustPlaylistButtons() {
        val buttons = listOf(binding.history, binding.lastAdded, binding.topPlayed, binding.actionShuffle)
        buttons.maxOf { it.lineCount }.let { maxLineCount ->
            buttons.forEach { button -> button.setLines(maxLineCount) }
        }
    }

    private fun setupListeners() {
        binding.bannerImage?.setOnClickListener {
            findNavController().navigate(R.id.user_info_fragment, null, null, FragmentNavigatorExtras(binding.userImage to "user_image"))
            reenterTransition = null
        }
        binding.lastAdded.setOnClickListener {
            findNavController().navigate(R.id.detailListFragment, bundleOf(EXTRA_PLAYLIST_TYPE to LAST_ADDED_PLAYLIST))
            setSharedAxisYTransitions()
        }
        binding.topPlayed.setOnClickListener {
            findNavController().navigate(R.id.detailListFragment, bundleOf(EXTRA_PLAYLIST_TYPE to TOP_PLAYED_PLAYLIST))
            setSharedAxisYTransitions()
        }
        binding.actionShuffle.setOnClickListener { libraryViewModel.shuffleSongs() }
        binding.history.setOnClickListener {
            findNavController().navigate(R.id.detailListFragment, bundleOf(EXTRA_PLAYLIST_TYPE to HISTORY_PLAYLIST))
            setSharedAxisYTransitions()
        }
        binding.userImage.setOnClickListener {
            findNavController().navigate(R.id.user_info_fragment, null, null, FragmentNavigatorExtras(binding.userImage to "user_image"))
        }
    }

    private fun setupTitle() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigate(R.id.action_search, null, navOptions) }
        binding.appBarLayout.title = getString(R.string.app_name)
    }

    private fun loadProfile() {
        binding.bannerImage?.let {
            Glide.with(requireContext()).load(RetroGlideExtension.getBannerModel()).profileBannerOptions(RetroGlideExtension.getBannerModel()).into(it)
        }
        Glide.with(requireActivity()).load(RetroGlideExtension.getUserModel()).userProfileOptions(RetroGlideExtension.getUserModel(), requireContext()).into(binding.userImage)
    }

    fun colorButtons() {
        binding.history.elevatedAccentColor()
        binding.lastAdded.elevatedAccentColor()
        binding.topPlayed.elevatedAccentColor()
        binding.actionShuffle.elevatedAccentColor()
    }

    private fun checkForMargins() {
        if (mainActivity.isBottomNavVisible) {
            binding.container.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = dip(R.dimen.bottom_nav_height) }
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        menu.removeItem(R.id.action_grid_size)
        menu.removeItem(R.id.action_layout_type)
        menu.removeItem(R.id.action_sort_order)
        menu.findItem(R.id.action_settings).setShowAsAction(SHOW_AS_ACTION_IF_ROOM)
        ToolbarContentTintHelper.handleOnCreateOptionsMenu(requireContext(), binding.toolbar, menu, ATHToolbarActivity.getToolbarBackgroundColor(binding.toolbar))
    }

    override fun scrollToTop() {
        binding.container.scrollTo(0, 0)
        binding.appBarLayout.setExpanded(true)
    }

    // --- MÉTODOS DE ANIMACIÓN REQUERIDOS POR LOS ADAPTADORES ---
    
    fun setSharedAxisXTransitions() {
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true).addTarget(CoordinatorLayout::class.java)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    private fun setSharedAxisYTransitions() {
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true).addTarget(CoordinatorLayout::class.java)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> findNavController().navigate(R.id.settings_fragment, null, navOptions)
            R.id.action_import_playlist -> ImportPlaylistDialog().show(childFragmentManager, "ImportPlaylist")
            R.id.action_add_to_playlist -> CreatePlaylistDialog.create(emptyList()).show(childFragmentManager, "ShowCreatePlaylistDialog")
        }
        return false
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        ToolbarContentTintHelper.handleOnPrepareOptionsMenu(requireActivity(), binding.toolbar)
    }

    override fun onResume() {
        super.onResume()
        checkForMargins()
        exitTransition = null
    }

    override fun onDestroyView() {
        if (binding.videoDownloadView.isPlaying) {
            binding.videoDownloadView.stopPlayback()
        }
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG: String = "BannerHomeFragment"

        @JvmStatic
        fun newInstance(): HomeFragment {
            return HomeFragment()
        }
    }
}
