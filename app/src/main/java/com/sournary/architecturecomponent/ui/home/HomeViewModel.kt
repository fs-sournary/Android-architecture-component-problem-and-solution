package com.sournary.architecturecomponent.ui.home

import androidx.lifecycle.*
import androidx.paging.cachedIn
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sournary.architecturecomponent.model.Genre
import com.sournary.architecturecomponent.model.Movie
import com.sournary.architecturecomponent.repository.MovieRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

/**
 * The view model contains all logic of home screen.
 */
@FlowPreview
@ExperimentalCoroutinesApi
class HomeViewModel(
    private val movieRepository: MovieRepository,
    // Problem 6: Use SavedStateHandle to handle in case OS kills our app.
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Problem 3: Leaking ViewModels
    // We create a reference of SwipeRefreshLayout (View) in ViewModels. This might lead to leak our
    // ViewModel
    private val swipeRefreshList = SwipeRefreshLayout.OnRefreshListener {
        // Fixme: delete me
    }

    // The Boolean determines whether the movie_recycler should scroll to 0.
    private var resetScroll: Boolean = false

    // The Integer stores the last checked id of the chip in the genre_group chip group.
    var checkId = 0

    private val _savedGenre = savedStateHandle.getLiveData<Genre>(KEY_GENRE)
    val savedGenre: LiveData<Genre> = _savedGenre
    val movies = _savedGenre.switchMap { movieRepository.getMovies(it).cachedIn(viewModelScope) }

    val genres: LiveData<List<Genre>> = movieRepository.genres

    // Problem 4: Exposing LiveData as Mutable to views
    // This isn't an error but recommended do not expose LiveData to View (Fragment, View).
    // Fixme: use private MutableLiveData and public LiveData.
    val game = MutableLiveData<String>()

    // Problem 5: Using LiveData to implement events
    // Use MutableLiveData in order to handle one-times make them re-trigger.
    // Fixme: Use [SingleLiveEvent] or [Event].
    private val _openMovieDetailEvent = MutableLiveData<Movie>()
    val openMovieDetailEvent: LiveData<Movie> = _openMovieDetailEvent

    fun loadNowPlayingMovie() {
        movieRepository.getMovies(Genre.SAVED_GENRES[0]).cachedIn(viewModelScope)
    }

    init {
        // Save now playing genre into disk for the first time.
        if (!savedStateHandle.contains(KEY_GENRE)) {
            savedStateHandle.set(KEY_GENRE, Genre.SAVED_GENRES[0])
        }
    }

    fun showMoviesOfCategory(genre: Genre) {
        if (savedStateHandle.get<Genre>(KEY_GENRE) == genre) return
        savedStateHandle.set(KEY_GENRE, genre)
        resetScroll = true
    }

    fun scrollToInitPosition(action: () -> Unit) {
        if (resetScroll) {
            action.invoke()
            resetScroll = false
        }
    }

    fun retryGetMovies() {
        val savedGenre = savedStateHandle.get<Genre>(KEY_GENRE)
        savedStateHandle.set(KEY_GENRE, savedGenre)
    }

    companion object {

        private const val KEY_GENRE = "movie_genre"
    }
}
