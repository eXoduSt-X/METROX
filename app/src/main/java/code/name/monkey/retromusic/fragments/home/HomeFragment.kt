package code.name.monkey.retromusic.fragments.home

import android.os.Bundle
import android.view.* // Importante para View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.navigation.fragment.findNavController
import code.name.monkey.retromusic.*
import code.name.monkey.retromusic.databinding.FragmentHomeBinding
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.interfaces.IScrollHelper
import com.google.android.material.transition.MaterialSharedAxis

class HomeFragment : AbsMainActivityFragment(R.layout.fragment_home), IScrollHelper {

    private var _binding: HomeBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = HomeBinding(FragmentHomeBinding.bind(view))
        setupListeners()
        loadProfile()
        adjustPlaylistButtons()
    }

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigate(R.id.action_search, null, navOptions) }
    }

    private fun loadProfile() {
        // Implementación pendiente
    }

    private fun adjustPlaylistButtons() {
        // Implementación pendiente
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = false

    override fun scrollToTop() {
        binding.container.scrollTo(0, 0)
        binding.appBarLayout.setExpanded(true)
    }

    fun setSharedAxisXTransitions() {
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true).addTarget(CoordinatorLayout::class.java)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
