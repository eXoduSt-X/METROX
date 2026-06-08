package code.name.monkey.retromusic.fragments.home

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
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

class HomeFragment : AbsMainActivityFragment(R.layout.fragment_home), IScrollHelper {

    private var _binding: HomeBinding? = null
    private val binding get() = _binding!!

    // Referencia directa al WebView y al botón flotante nativo
    private var youtubeWebView: WebView? = null
    private var extractedJsonData: String? = null

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

        // --- INICIALIZACIÓN DEL NAVEGADOR WEB Y EXTRACCIÓN (ESTILO SNAPTUBE) ---
        setupYoutubeNavigation(homeBinding)

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

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupYoutubeNavigation(homeBinding: FragmentHomeBinding) {
        // Vinculamos las vistas mapeadas del XML modificado
        youtubeWebView = homeBinding.youtubeWebView
        val btnDownloadFloating = homeBinding.btnDownloadFloating

        youtubeWebView?.let { webView ->
            val settings = webView.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            
            // Forzamos un User-Agent móvil de Chrome moderno para que cargue la interfaz ligera de m.youtube.com
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Redmi Note 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

            // Creamos la interfaz puente entre JavaScript y Kotlin
            webView.addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun onDataExtracted(jsonString: String?) {
                    if (!jsonString.isNullOrEmpty() && jsonString != "null") {
                        extractedJsonData = jsonString
                        // Volvemos al hilo principal para renderizar la UI de Android de forma segura
                        activity?.runOnUiThread {
                            btnDownloadFloating?.visibility = View.VISIBLE
                        }
                    }
                }
            }, "MetroExtractor")

            // Control de navegación interna
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    // Si el usuario sale de un reproductor de video, ocultamos el botón preventivamente
                    if (!url.contains("youtube.com/watch?v=") && !url.contains("youtu.be/")) {
                        btnDownloadFloating?.visibility = View.GONE
                        extractedJsonData = null
                    }
                    return false // Permite que el WebView cargue el enlace internamente
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Si la URL que terminó de cargar es un metraje válido, inyectamos el anzuelo
                    if (url != null && (url.contains("youtube.com/watch?v=") || url.contains("youtu.be/"))) {
                        injectScriptExtractor()
                    }
                }
            }

            // Cargamos la interfaz móvil por defecto al iniciar el Fragment
            webView.loadUrl("https://m.youtube.com")
        }

        // Configuración del click sobre tu botón flotante nativo
        btnDownloadFloating?.setOnClickListener {
            if (!extractedJsonData.isNullOrEmpty()) {
                procesarFlujoDeDescarga(extractedJsonData!!)
            } else {
                Toast.makeText(requireContext(), "Analizando firmas del reproductor... Espera un segundo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun injectScriptExtractor() {
        youtubeWebView?.evaluateJavascript(
            """
            (function() {
                if (window.ytInitialPlayerResponse) {
                    MetroExtractor.onDataExtracted(JSON.stringify(window.ytInitialPlayerResponse));
                } else {
                    // Espera reactiva de 1.5 segundos si el DOM móvil de Google sufre retraso
                    setTimeout(function() {
                        if (window.ytInitialPlayerResponse) {
                            MetroExtractor.onDataExtracted(JSON.stringify(window.ytInitialPlayerResponse));
                        }
                    }, 1500);
                }
            })();
            """.trimIndent(), null
        )
    }

    private fun procesarFlujoDeDescarga(jsonData: String) {
        try {
            val playerResponse = JSONObject(jsonData)
            val streamingData = playerResponse.getJSONObject("streamingData")
            val videoDetails = playerResponse.getJSONObject("videoDetails")
            val tituloVideo = videoDetails.optString("title", "video_metro").replace(" ", "_") + ".mp4"

            // Extraemos los bloques de flujos progresivos (Video + Audio combinados listos para descargar)
            var urlDescarga: String? = null
            if (streamingData.has("formats")) {
                val formats = streamingData.getJSONArray("formats")
                if (formats.length() > 0) {
                    // Priorizamos el primer flujo progresivo disponible procesado de forma nativa por Chrome
                    urlDescarga = formats.getJSONObject(0).optString("url", "")
                }
            }

            // Fallback secundario si los streams vienen multiplexados en adaptiveFormats
            if (urlDescarga.isNullOrEmpty() && streamingData.has("adaptiveFormats")) {
                val adaptiveFormats = streamingData.getJSONArray("adaptiveFormats")
                for (i in 0 until adaptiveFormats.length()) {
                    val format = adaptiveFormats.getJSONObject(i)
                    if (format.optString("mimeType", "").contains("video/mp4")) {
                        urlDescarga = format.optString("url", "")
                        break
                    }
                }
            }

            if (!urlDescarga.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "¡Enlace capturado de forma legítima!", Toast.LENGTH_SHORT).show()
                
                // Acción dual: Iniciamos la reproducción directa en tu panel de video local e iniciamos la descarga en segundo plano
                reproducirVideoEnPanel(Uri.parse(urlDescarga))
                ejecutarDescargaDelSistema(urlDescarga, tituloVideo)
            } else {
                Toast.makeText(requireContext(), "Error: Este flujo multimedia requiere descifrado local complejo.", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error de extracción en el WebView: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reproducirVideoEnPanel(videoUri: Uri) {
        binding.videoDownloadContainer.visibility = View.VISIBLE
        binding.videoDownloadView.setVideoURI(videoUri)
        binding.videoDownloadView.requestFocus()
        binding.videoDownloadView.start()
    }

    private fun ejecutarDescargaDelSistema(url: String, nombreArchivo: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("Descargando de Metro...")
                setDescription(nombreArchivo)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nombreArchivo)
                setMimeType("video/mp4")
            }
            val manager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(requireContext(), "Descarga mandada a la cola pública", View.LENGTH_SHORT).show()
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
        // Destrucción preventiva del WebView para evitar fugas de memoria del motor Chromium
        youtubeWebView?.let {
            it.removeJavascriptInterface("MetroExtractor")
            it.removeAllViews()
            it.destroy()
        }
        youtubeWebView = null
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
