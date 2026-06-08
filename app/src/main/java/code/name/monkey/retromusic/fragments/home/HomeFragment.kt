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
    private var floatingButton: View? = null

    private val selectLocalVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { reproducirVideoEnPanel(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = HomeBinding(FragmentHomeBinding.bind(view))
        mainActivity.setSupportActionBar(binding.toolbar)
        mainActivity.supportActionBar?.title = null
        setupListeners()
        binding.titleWelcome.text = userName
        enterTransition = MaterialFadeThrough().addTarget(binding.contentContainer)
        reenterTransition = MaterialFadeThrough().addTarget(binding.contentContainer)
        checkForMargins()
        setupYoutubeNavigation(binding)
        binding.btnLoadLocalVideo.setOnClickListener { selectLocalVideoLauncher.launch("video/*") }
        loadProfile()
        setupTitle()
        colorButtons()
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        view.doOnLayout { adjustPlaylistButtons() }
    }

    private fun reproducirVideoEnPanel(videoUri: Uri) {
        binding.videoDownloadContainer.visibility = View.VISIBLE
        binding.videoDownloadView.setVideoURI(videoUri)
        binding.videoDownloadView.requestFocus()
        binding.videoDownloadView.start()
    }

    private fun adjustPlaylistButtons() {
        val buttons = listOf(binding.history, binding.lastAdded, binding.topPlayed, binding.actionShuffle)
        val maxLineCount = buttons.map { it.lineCount }.maxOrNull() ?: 1
        buttons.forEach { it.setLines(maxLineCount) }
    }

    private fun setupTitle() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigate(R.id.action_search, null, navOptions) }
        binding.toolbar.title = getString(R.string.app_name)
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
        if (binding.videoDownloadView.isPlaying) binding.videoDownloadView.stopPlayback()
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
        @JvmStatic fun newInstance() = HomeFragment()
    }
}
