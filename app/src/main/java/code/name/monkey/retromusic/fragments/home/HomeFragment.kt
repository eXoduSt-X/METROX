package code.name.monkey.retromusic.fragments.home

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
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
import org.json.JSONObject

class HomeFragment : AbsMainActivityFragment(R.layout.fragment_home), IScrollHelper {

    private var _binding: HomeBinding? = null
    private val binding get() = _binding!!
    private var youtubeWebView: WebView? = null
    private var extractedJsonData: String? = null

    private val selectLocalVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { reproducirVideoEnPanel(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = HomeBinding(FragmentHomeBinding.bind(view))

        mainActivity.setSupportActionBar(binding.toolbar)
        setupListeners()
        binding.titleWelcome.text = userName

        enterTransition = MaterialFadeThrough().addTarget(binding.contentContainer)
        setupYoutubeNavigation(binding)
        
        binding.btnLoadLocalVideo.setOnClickListener { selectLocalVideoLauncher.launch("video/*") }
        
        loadProfile()
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        view.doOnLayout { adjustPlaylistButtons() }
    }

    // --- REINCORPORACIÓN DE LÓGICA ---

    private fun setupListeners() {
        binding.lastAdded.setOnClickListener { findNavController().navigate(R.id.detailListFragment, bundleOf(EXTRA_PLAYLIST_TYPE to LAST_ADDED_PLAYLIST)) }
        binding.topPlayed.setOnClickListener { findNavController().navigate(R.id.detailListFragment, bundleOf(EXTRA_PLAYLIST_TYPE to TOP_PLAYED_PLAYLIST)) }
        binding.history.setOnClickListener { findNavController().navigate(R.id.detailListFragment, bundleOf(EXTRA_PLAYLIST_TYPE to HISTORY_PLAYLIST)) }
    }

    private fun setupYoutubeNavigation(h: HomeBinding) {
        youtubeWebView = h.youtubeWebView
        h.youtubeWebView.settings.javaScriptEnabled = true
        // ... (Aquí mantienes tu lógica de WebViewClient y JavascriptInterface)
    }

    private fun reproducirVideoEnPanel(videoUri: Uri) {
        binding.videoDownloadContainer.visibility = View.VISIBLE
        binding.videoDownloadView.setVideoURI(videoUri)
        binding.videoDownloadView.start()
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        // Tu lógica de menú (Settings, Playlists, etc)
        return false
    }

    override fun scrollToTop() {
        binding.container.scrollTo(0, 0)
        binding.appBarLayout.setExpanded(true)
    }

    fun setSharedAxisXTransitions() {
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true).addTarget(CoordinatorLayout::class.java)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onDestroyView() {
        youtubeWebView?.destroy()
        _binding = null
        super.onDestroyView()
    }
}
