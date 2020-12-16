package com.sournary.architecturecomponent.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import com.google.android.material.chip.Chip
import com.sournary.architecturecomponent.R
import com.sournary.architecturecomponent.databinding.FragmentHomeBinding
import com.sournary.architecturecomponent.ext.autoCleared
import com.sournary.architecturecomponent.ext.hideKeyboard
import com.sournary.architecturecomponent.model.Genre
import com.sournary.architecturecomponent.ui.common.BaseFragment
import com.sournary.architecturecomponent.ui.common.MainViewModel
import com.sournary.architecturecomponent.ui.common.NetworkStateAdapter
import com.sournary.architecturecomponent.widget.MovieItemDecoration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

/**
 * The Fragment represents home screen.
 */
@FlowPreview
@ExperimentalCoroutinesApi
class HomeFragment : BaseFragment<FragmentHomeBinding, HomeViewModel>() {

    private var adapter by autoCleared<MovieListAdapter>()

    private val mainViewModel: MainViewModel by activityViewModels()
    override val viewModel: HomeViewModel by viewModels { HomeViewModelFactory(this) }

    override val layoutId: Int = R.layout.fragment_home

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setOnApplyWindowInsetsListener { _, insets ->
            view.updatePadding(top = insets.systemWindowInsetTop)
            binding.movieRecycler.updatePadding(bottom = insets.systemWindowInsetBottom)
            insets
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setupSearch()
        setupMovieList()
        setupViewModel()
        // Problem 2: Reload data after every rotation or navigation
        // We load data each time onActivityCreated() is called.
        // When user navigate or rotate screen, this above function will be recalled because
        // onActivityCreated() is recalled.
        // Fixme: delete and execute in ViewModel.
        viewModel.loadNowPlayingMovie()

        // Problem 4: Exposing LiveData as Mutable to views
        // Fixme: delete me and make as suggestion in ViewModel.
        viewModel.game.value = "New game"
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSearch() {
        binding.searchTextInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                getMoviesFromSearch()
                true
            } else {
                false
            }
        }
        binding.searchTextInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                getMoviesFromSearch()
                true
            } else {
                false
            }
        }
        binding.searchTextInput.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP &&
                event.x <= binding.searchTextInput.compoundDrawables[0].bounds.width()
            ) {
                mainViewModel.openNavigation()
                true
            } else {
                false
            }
        }
        binding.retryButton.setOnClickListener {
            viewModel.retryGetMovies()
        }
        binding.genreGroup.setOnCheckedChangeListener { _, checkedId ->
            viewModel.checkId = checkedId
        }
    }

    private fun getMoviesFromSearch() {
        hideKeyboard()
        val searchText = binding.searchTextInput.text?.trim() ?: return
        viewModel.genres.value?.forEach { genre ->
            if (genre.name == searchText) {
                viewModel.showMoviesOfCategory(genre)
            }
        }
    }

    private fun setupMovieList() {
        binding.movieSwipeRefresh.setOnRefreshListener {
            adapter.refresh()
        }
        adapter = MovieListAdapter { movie ->
            val directions = HomeFragmentDirections.navigateToMovieDetail(movie.id)
            navController.navigate(directions)
        }
        binding.movieRecycler.adapter = adapter.withLoadStateHeaderAndFooter(
            header = NetworkStateAdapter { adapter.retry() },
            footer = NetworkStateAdapter { adapter.retry() }
        )
        val loadStateListener = { loadStates: CombinedLoadStates ->
            binding.movieSwipeRefresh.isVisible = loadStates.refresh !is LoadState.Loading
            binding.progress.isVisible = loadStates.refresh is LoadState.Loading
            binding.retryButton.isVisible = loadStates.refresh is LoadState.Error
            if (binding.movieSwipeRefresh.isRefreshing) {
                binding.movieSwipeRefresh.isRefreshing = false
            }
            if (loadStates.refresh is LoadState.Loading) {
                viewModel.scrollToInitPosition { binding.movieRecycler.scrollToPosition(0) }
            }
        }
        adapter.addLoadStateListener(loadStateListener)
        val divider =
            ContextCompat.getDrawable(context ?: return, R.drawable.shape_movie_divider) ?: return
        val horizontalSpacing = resources.getDimensionPixelOffset(R.dimen.dp_16)
        val itemDecoration = MovieItemDecoration(horizontalSpacing, divider)
        binding.movieRecycler.addItemDecoration(itemDecoration)
    }

    private fun setupViewModel() {
        mainViewModel.setLockNavigation(false)
        viewModel.apply {
            // Problem 1: Leaking LiveData Observer in Fragment.
            // Here we pass an Fragment as LifecycleOwner of a new Observer.
            // This makes Observer not to be cleared when configuration or navigation occurs.
            movies.observe(this@HomeFragment) {
                adapter.submitData(viewLifecycleOwner.lifecycle, it)
            }
            genres.observe(viewLifecycleOwner) {
                addGenres(it)
            }
        }
    }

    private fun addGenres(genres: List<Genre>) {
        binding.genreGroup.removeAllViews()
        genres.forEachIndexed { index, genre ->
            val chip = layoutInflater.inflate(
                R.layout.layout_genre_item, binding.genreGroup, false
            ) as Chip
            chip.text = genre.name
            chip.id = index
            chip.setOnClickListener {
                hideKeyboard()
                chip.requestFocus()
                binding.searchTextInput.setText(genre.name)
                if (binding.searchTextInput.text?.trim() != genre.name) {
                    viewModel.showMoviesOfCategory(genre)
                }
            }
            binding.genreGroup.addView(chip)
        }
        binding.genreGroup.check(viewModel.checkId)
    }
}
