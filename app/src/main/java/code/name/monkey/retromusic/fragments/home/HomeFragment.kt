package code.name.monkey.retromusic.fragments.home

import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentHomeBinding
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.interfaces.IScrollHelper

class HomeFragment : AbsMainActivityFragment(R.layout.fragment_home), IScrollHelper {

    private var _binding: FragmentHomeBinding? = null // Asumimos que usas ViewBinding
    private val binding get() = _binding!!

    // Playlist en memoria
    private val videoPlaylist = mutableListOf<Uri>()
    private var currentIndex = 0

    // Lanzador para seleccionar múltiples videos
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

        setupVideoListeners()
    }

    private fun setupVideoListeners() {
        // Botón para cargar playlist
        binding.btnOpenFile.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        // Play / Pause
        binding.btnPlayPause.setOnClickListener {
            if (binding.videoPlayer.isPlaying) {
                binding.videoPlayer.pause()
                binding.btnPlayPause.text = "Play"
            } else {
                binding.videoPlayer.start()
                binding.btnPlayPause.text = "Pause"
            }
        }

        // Siguiente video
        binding.btnForward.setOnClickListener {
            if (currentIndex < videoPlaylist.size - 1) {
                currentIndex++
                reproducirVideoActual()
            } else {
                Toast.makeText(requireContext(), "Fin de la lista", Toast.LENGTH_SHORT).show()
            }
        }

        // Video anterior
        binding.btnRewind.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                reproducirVideoActual()
            }
        }
    }

    private fun reproducirVideoActual() {
        if (videoPlaylist.isNotEmpty()) {
            binding.videoPlayer.setVideoURI(videoPlaylist[currentIndex])
            binding.videoPlayer.start()
            binding.btnPlayPause.text = "Pause"
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
            Glide.with(requireContext())
                .load(RetroGlideExtension.getBannerModel())
                .profileBannerOptions(RetroGlideExtension.getBannerModel())
                .into(it)
        }
        Glide.with(requireActivity())
            .load(RetroGlideExtension.getUserModel())
            .userProfileOptions(RetroGlideExtension.getUserModel(), requireContext())
            .into(binding.userImage)
    }

    fun colorButtons() {
        binding.history.elevatedAccentColor()
        binding.lastAdded.elevatedAccentColor()
        binding.topPlayed.elevatedAccentColor()
        binding.actionShuffle.elevatedAccentColor()
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
        menu.findItem(R.id.action_settings).setShowAsAction(SHOW_AS_ACTION_IF_ROOM)
        ToolbarContentTintHelper.handleOnCreateOptionsMenu(requireContext(), binding.toolbar, menu, ATHToolbarActivity.getToolbarBackgroundColor(binding.toolbar))
    }

    override fun scrollToTop() {
        binding.container.scrollTo(0, 0)
        binding.appBarLayout.setExpanded(true)
    }

    private fun setSharedAxisYTransitions() {
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true).addTarget(CoordinatorLayout::class.java)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)
    }

    companion object {
        const val TAG: String = "BannerHomeFragment"
        @JvmStatic fun newInstance(): HomeFragment = HomeFragment()
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
        binding.videoPlayer.stopPlayback()
        _binding = null
        super.onDestroyView()
    }
}
