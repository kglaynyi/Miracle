package com.miracle.kglaynyi.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.adapter.HomeMediaAdapter;
import com.miracle.kglaynyi.adapter.HomeResumeAdapter;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.player.PlayerActivity;
import com.miracle.kglaynyi.utils.IndexUtils;
import com.miracle.kglaynyi.utils.ResumeUtils;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends BaseFragment {

    private HomeResumeAdapter continueWatchingAdapter;
    private HomeMediaAdapter recentlyAddedAdapter;
    private HomeMediaAdapter recentlyReleasedAdapter;
    private HomeMediaAdapter moviesAdapter;
    private HomeMediaAdapter lastPlayedAdapter;
    private HomeMediaAdapter watchlistAdapter;
    private HomeMediaAdapter newSeasonAdapter;
    private HomeMediaAdapter topRatedShowsAdapter;

    private List<MyMedia> continueWatchingMedia = new ArrayList<>();
    private List<ResumeUtils.Entry> continueWatchingEntries = new ArrayList<>();
    private List<Movie> recentlyAddedMovies = new ArrayList<>();
    private List<Movie> recentlyReleasedMovies = new ArrayList<>();
    private List<Movie> topRatedMovies = new ArrayList<>();
    private List<Movie> lastPlayedList = new ArrayList<>();
    private List<MyMedia> watchlist = new ArrayList<>();
    private List<TVShow> newSeason = new ArrayList<>();
    private List<TVShow> topRatedShows = new ArrayList<>();

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean sawStartupScan;

    private final Runnable scanStatusRunnable = new Runnable() {
        @Override public void run() {
            if (!isAdded() || getView() == null) return;

            boolean scanning = IndexUtils.isAnyScanRunning();
            ProgressBar spinner = getView().findViewById(R.id.homeScanSpinner);
            TextView status = getView().findViewById(R.id.homeScanStatus);
            spinner.setVisibility(scanning ? View.VISIBLE : View.GONE);
            status.setVisibility(scanning ? View.VISIBLE : View.GONE);

            if (scanning) sawStartupScan = true;
            if (!scanning && sawStartupScan) {
                sawStartupScan = false;
                refreshHomeSections();
            }

            uiHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupTopBar(view);
        setupViewAll(view);
        refreshHomeSections();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() == null) return;
        refreshHomeSections();
        uiHandler.removeCallbacks(scanStatusRunnable);
        uiHandler.post(scanStatusRunnable);
    }

    @Override
    public void onPause() {
        uiHandler.removeCallbacks(scanStatusRunnable);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        uiHandler.removeCallbacks(scanStatusRunnable);
        super.onDestroyView();
    }

    private void setupTopBar(View view) {
        view.findViewById(R.id.homeSearchButton).setOnClickListener(v ->
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.container, new SearchFragment())
                        .addToBackStack(null)
                        .commit());

        view.findViewById(R.id.homeMoreButton).setOnClickListener(v ->
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.container, new SettingsFragment())
                        .addToBackStack(null)
                        .commit());
    }

    private void setupViewAll(View view) {
        View.OnClickListener openLibrary = v ->
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.container, new LibraryFragment())
                        .addToBackStack(null)
                        .commit();

        view.findViewById(R.id.continueWatchingViewAll).setOnClickListener(openLibrary);
        view.findViewById(R.id.recentlyAddedViewAll).setOnClickListener(openLibrary);
        view.findViewById(R.id.moviesViewAll).setOnClickListener(openLibrary);
    }

    private void refreshHomeSections() {
        loadContinueWatching();
        loadRecentlyAddedMovies();
        loadRecentlyReleasedMovies();
        loadMovies();
        loadLastPlayedMovies();
        loadWatchlist();
        loadNewSeason();
        loadTopRatedShows();
    }

    private void configureRow(RecyclerView recyclerView) {
        if (recyclerView.getLayoutManager() == null) {
            recyclerView.setLayoutManager(
                    new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
            recyclerView.setHasFixedSize(true);
            recyclerView.setItemAnimator(null);
        }
    }

    private void loadContinueWatching() {
        new Thread(() -> {
            List<ResumeUtils.Entry> savedEntries = ResumeUtils.getEntries(mActivity);
            List<MyMedia> media = new ArrayList<>();
            List<ResumeUtils.Entry> resolvedEntries = new ArrayList<>();

            for (ResumeUtils.Entry entry : savedEntries) {
                if (entry == null || entry.url == null) continue;

                Movie movie = DatabaseClient.getInstance(mActivity)
                        .getAppDatabase().movieDao().findByUrl(entry.url);
                if (movie != null) {
                    media.add(movie);
                    resolvedEntries.add(entry);
                    continue;
                }

                Episode episode = DatabaseClient.getInstance(mActivity)
                        .getAppDatabase().episodeDao().findByUrl(entry.url);
                if (episode != null) {
                    TVShow show = DatabaseClient.getInstance(mActivity)
                            .getAppDatabase().tvShowDao().find(episode.getShow_id());
                    if (show != null) {
                        media.add(show);
                        resolvedEntries.add(entry);
                    }
                }
            }

            continueWatchingMedia = media;
            continueWatchingEntries = resolvedEntries;

            mActivity.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;

                TextView title = getView().findViewById(R.id.continueWatchingTitle);
                TextView viewAll = getView().findViewById(R.id.continueWatchingViewAll);
                RecyclerView recycler = getView().findViewById(R.id.continueWatchingRecycler);

                boolean visible = !continueWatchingMedia.isEmpty();
                title.setVisibility(visible ? View.VISIBLE : View.GONE);
                viewAll.setVisibility(visible ? View.VISIBLE : View.GONE);
                recycler.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (!visible) return;

                configureRow(recycler);
                HomeResumeAdapter.OnItemClickListener listener = (view, position) -> {
                    if (position < 0 || position >= continueWatchingEntries.size()) return;
                    ResumeUtils.Entry entry = continueWatchingEntries.get(position);
                    Intent intent = new Intent(mActivity, PlayerActivity.class);
                    intent.putExtra("url", entry.url);
                    startActivity(intent);
                };

                if (continueWatchingAdapter == null) {
                    continueWatchingAdapter =
                            new HomeResumeAdapter(getContext(), continueWatchingMedia, listener);
                } else {
                    continueWatchingAdapter.submitList(continueWatchingMedia);
                }
                if (recycler.getAdapter() != continueWatchingAdapter) {
                    recycler.setAdapter(continueWatchingAdapter);
                }
            });
        }).start();
    }

    private void loadRecentlyAddedMovies() {
        new Thread(() -> {
            List<Movie> list = DatabaseClient.getInstance(mActivity)
                    .getAppDatabase().movieDao().getrecentlyadded();
            recentlyAddedMovies = list == null ? new ArrayList<>() : list;

            mActivity.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                TextView title = getView().findViewById(R.id.recentlyAdded);
                TextView viewAll = getView().findViewById(R.id.recentlyAddedViewAll);
                RecyclerView recycler = getView().findViewById(R.id.recentlyAddedRecycler);

                boolean visible = !recentlyAddedMovies.isEmpty();
                title.setVisibility(visible ? View.VISIBLE : View.GONE);
                viewAll.setVisibility(visible ? View.VISIBLE : View.GONE);
                recycler.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (!visible) return;

                configureRow(recycler);
                HomeMediaAdapter.OnItemClickListener listener = (view, position) ->
                        openMovie(recentlyAddedMovies.get(position));
                if (recentlyAddedAdapter == null) {
                    recentlyAddedAdapter = new HomeMediaAdapter(getContext(), recentlyAddedMovies, listener);
                } else {
                    recentlyAddedAdapter.submitList(recentlyAddedMovies);
                }
                if (recycler.getAdapter() != recentlyAddedAdapter) {
                    recycler.setAdapter(recentlyAddedAdapter);
                }
            });
        }).start();
    }

    private void loadMovies() {
        new Thread(() -> {
            List<Movie> list = DatabaseClient.getInstance(mActivity)
                    .getAppDatabase().movieDao().getTopRated();
            topRatedMovies = list == null ? new ArrayList<>() : list;

            mActivity.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                TextView title = getView().findViewById(R.id.topRatedMovies);
                TextView viewAll = getView().findViewById(R.id.moviesViewAll);
                RecyclerView recycler = getView().findViewById(R.id.topRatedMoviesRecycler);

                boolean visible = !topRatedMovies.isEmpty();
                title.setVisibility(visible ? View.VISIBLE : View.GONE);
                viewAll.setVisibility(visible ? View.VISIBLE : View.GONE);
                recycler.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (!visible) return;

                configureRow(recycler);
                HomeMediaAdapter.OnItemClickListener listener = (view, position) ->
                        openMovie(topRatedMovies.get(position));
                if (moviesAdapter == null) {
                    moviesAdapter = new HomeMediaAdapter(getContext(), topRatedMovies, listener);
                } else {
                    moviesAdapter.submitList(topRatedMovies);
                }
                if (recycler.getAdapter() != moviesAdapter) {
                    recycler.setAdapter(moviesAdapter);
                }
            });
        }).start();
    }

    private void loadRecentlyReleasedMovies() {
        new Thread(() -> {
            List<Movie> list = DatabaseClient.getInstance(mActivity)
                    .getAppDatabase().movieDao().getrecentreleases();
            recentlyReleasedMovies = list == null ? new ArrayList<>() : list;

            mActivity.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                TextView title = getView().findViewById(R.id.newReleasesMovies);
                RecyclerView recycler = getView().findViewById(R.id.recentlyReleasedMoviesRecycler);
                boolean visible = !recentlyReleasedMovies.isEmpty();
                title.setVisibility(visible ? View.VISIBLE : View.GONE);
                recycler.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (!visible) return;

                configureRow(recycler);
                HomeMediaAdapter.OnItemClickListener listener = (view, position) ->
                        openMovie(recentlyReleasedMovies.get(position));
                if (recentlyReleasedAdapter == null) {
                    recentlyReleasedAdapter =
                            new HomeMediaAdapter(getContext(), recentlyReleasedMovies, listener);
                } else {
                    recentlyReleasedAdapter.submitList(recentlyReleasedMovies);
                }
                if (recycler.getAdapter() != recentlyReleasedAdapter) {
                    recycler.setAdapter(recentlyReleasedAdapter);
                }
            });
        }).start();
    }

    private void loadLastPlayedMovies() {
        new Thread(() -> {
            List<Movie> list = DatabaseClient.getInstance(mActivity)
                    .getAppDatabase().movieDao().getPlayed();
            lastPlayedList = list == null ? new ArrayList<>() : list;

            mActivity.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                TextView title = getView().findViewById(R.id.lastPlayedMovies);
                RecyclerView recycler = getView().findViewById(R.id.lastPlayedMoviesRecycler);
                boolean visible = !lastPlayedList.isEmpty();
                title.setVisibility(visible ? View.VISIBLE : View.GONE);
                recycler.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (!visible) return;

                configureRow(recycler);
                HomeMediaAdapter.OnItemClickListener listener = (view, position) ->
                        openMovie(lastPlayedList.get(position));
                if (lastPlayedAdapter == null) {
                    lastPlayedAdapter = new HomeMediaAdapter(getContext(), lastPlayedList, listener);
                } else {
                    lastPlayedAdapter.submitList(lastPlayedList);
                }
                if (recycler.getAdapter() != lastPlayedAdapter) {
                    recycler.setAdapter(lastPlayedAdapter);
                }
            });
        }).start();
    }

    private void loadWatchlist() {
        new Thread(() -> {
            List<Movie> movies = DatabaseClient.getInstance(mActivity)
                    .getAppDatabase().movieDao().getWatchlisted();
            List<TVShow> shows = DatabaseClient.getInstance(mActivity)
                    .getAppDatabase().tvShowDao().getWatchlisted();

            List<MyMedia> result = new ArrayList<>();
            if (movies != null) result.addAll(movies);
            if (shows != null) result.addAll(shows);
            watchlist = result;

            mActivity.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                TextView title = getView().findViewById(R.id.watchListMedia);
                RecyclerView recycler = getView().findViewById(R.id.watchListMediaRecycler);
                boolean visible = !watchlist.isEmpty();
                title.setVisibility(visible ? View.VISIBLE : View.GONE);
                recycler.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (!visible) return;

                configureRow(recycler);
                HomeMediaAdapter.OnItemClickListener listener = (view, position) ->
                        openMedia(watchlist.get(position));
                if (watchlistAdapter == null) {
                    watchlistAdapter = new HomeMediaAdapter(getContext(), watchlist, listener);
                } else {
                    watchlistAdapter.submitList(watchlist);
                }
                if (recycler.getAdapter() != watchlistAdapter) {
                    recycler.setAdapter(watchlistAdapter);
                }
            });
        }).start();
    }

    private void loadNewSeason() {
        new Thread(() -> {
            List<TVShow> list = DatabaseClient.getInstance(mActivity)
                    .getAppDatabase().tvShowDao().getNewShows();
            newSeason = list == null ? new ArrayList<>() : list;

            mActivity.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                TextView title = getView().findViewById(R.id.newSeason);
                RecyclerView recycler = getView().findViewById(R.id.newSeasonRecycler);
                boolean visible = !newSeason.isEmpty();
                title.setVisibility(visible ? View.VISIBLE : View.GONE);
                recycler.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (!visible) return;

                configureRow(recycler);
                HomeMediaAdapter.OnItemClickListener listener = (view, position) ->
                        openShow(newSeason.get(position));
                if (newSeasonAdapter == null) {
                    newSeasonAdapter = new HomeMediaAdapter(getContext(), newSeason, listener);
                } else {
                    newSeasonAdapter.submitList(newSeason);
                }
                if (recycler.getAdapter() != newSeasonAdapter) {
                    recycler.setAdapter(newSeasonAdapter);
                }
            });
        }).start();
    }

    private void loadTopRatedShows() {
        new Thread(() -> {
            List<TVShow> list = DatabaseClient.getInstance(mActivity)
                    .getAppDatabase().tvShowDao().getTopRated();
            topRatedShows = list == null ? new ArrayList<>() : list;

            mActivity.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                TextView title = getView().findViewById(R.id.topRatedTVShows);
                RecyclerView recycler = getView().findViewById(R.id.topRatedTVShowsRecycler);
                boolean visible = !topRatedShows.isEmpty();
                title.setVisibility(visible ? View.VISIBLE : View.GONE);
                recycler.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (!visible) return;

                configureRow(recycler);
                HomeMediaAdapter.OnItemClickListener listener = (view, position) ->
                        openShow(topRatedShows.get(position));
                if (topRatedShowsAdapter == null) {
                    topRatedShowsAdapter = new HomeMediaAdapter(getContext(), topRatedShows, listener);
                } else {
                    topRatedShowsAdapter.submitList(topRatedShows);
                }
                if (recycler.getAdapter() != topRatedShowsAdapter) {
                    recycler.setAdapter(topRatedShowsAdapter);
                }
            });
        }).start();
    }

    private void openMovie(Movie movie) {
        if (movie == null) return;
        MovieDetailsFragment fragment = movie.getId() != 0
                ? new MovieDetailsFragment(movie.getId())
                : new MovieDetailsFragment(movie.getFileName());
        mActivity.getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void openShow(TVShow show) {
        if (show == null) return;
        mActivity.getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.container, new TvShowDetailsFragment(show.getId()))
                .addToBackStack(null)
                .commit();
    }

    private void openMedia(MyMedia media) {
        if (media instanceof Movie) openMovie((Movie) media);
        else if (media instanceof TVShow) openShow((TVShow) media);
    }
}
