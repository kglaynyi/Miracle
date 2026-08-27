package com.miracle.kglaynyi.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.adapter.ContinueWatchingAdapter;
import com.miracle.kglaynyi.adapter.HomeMediaAdapter;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.player.PlayerActivity;
import com.miracle.kglaynyi.utils.IndexUtils;
import com.miracle.kglaynyi.utils.MediaClassificationUtils;
import com.miracle.kglaynyi.utils.ResumeUtils;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends BaseFragment {

    private static final int HOME_LIMIT = 20;

    private View sourceStatusContainer;
    private TextView sourceStatusText;
    private View continueSection;
    private View recentSection;
    private View moviesSection;
    private View showsSection;
    private View animeSection;

    private RecyclerView continueRecycler;
    private RecyclerView recentRecycler;
    private RecyclerView moviesRecycler;
    private RecyclerView showsRecycler;
    private RecyclerView animeRecycler;

    private ContinueWatchingAdapter continueAdapter;
    private HomeMediaAdapter recentAdapter;
    private HomeMediaAdapter moviesAdapter;
    private HomeMediaAdapter showsAdapter;
    private HomeMediaAdapter animeAdapter;

    private List<Movie> recentMovies = new ArrayList<>();
    private List<Movie> movies = new ArrayList<>();
    private List<TVShow> shows = new ArrayList<>();
    private List<MyMedia> anime = new ArrayList<>();

    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private boolean scanWasRunning;
    private final Runnable statusObserver = new Runnable() {
        @Override public void run() {
            if (!isAdded() || getView() == null) return;
            boolean running = IndexUtils.isAnyScanRunning();
            sourceStatusContainer.setVisibility(running ? View.VISIBLE : View.GONE);
            if (running) {
                scanWasRunning = true;
                sourceStatusText.setText("Updating media source from cache…");
            } else if (scanWasRunning) {
                scanWasRunning = false;
                refreshHomeData();
            }
            statusHandler.postDelayed(this, 1200L);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sourceStatusContainer = view.findViewById(R.id.sourceStatusContainer);
        sourceStatusText = view.findViewById(R.id.sourceStatusText);
        continueSection = view.findViewById(R.id.continueSection);
        recentSection = view.findViewById(R.id.recentSection);
        moviesSection = view.findViewById(R.id.moviesSection);
        showsSection = view.findViewById(R.id.showsSection);
        animeSection = view.findViewById(R.id.animeSection);

        continueRecycler = view.findViewById(R.id.continueWatchingRecycler);
        recentRecycler = view.findViewById(R.id.recentlyAddedRecycler);
        moviesRecycler = view.findViewById(R.id.homeMoviesRecycler);
        showsRecycler = view.findViewById(R.id.homeShowsRecycler);
        animeRecycler = view.findViewById(R.id.homeAnimeRecycler);

        setupHorizontal(continueRecycler);
        setupHorizontal(recentRecycler);
        setupHorizontal(moviesRecycler);
        setupHorizontal(showsRecycler);
        setupHorizontal(animeRecycler);

        continueAdapter = new ContinueWatchingAdapter(mActivity, new ArrayList<>(), item -> {
            if (item == null || item.resume == null || item.resume.url == null) return;
            Intent intent = new Intent(mActivity, PlayerActivity.class);
            intent.putExtra("url", item.resume.url);
            startActivity(intent);
        });
        continueRecycler.setAdapter(continueAdapter);

        recentAdapter = new HomeMediaAdapter(mActivity, recentMovies,
                position -> openMovie(recentMovies, position));
        moviesAdapter = new HomeMediaAdapter(mActivity, movies,
                position -> openMovie(movies, position));
        showsAdapter = new HomeMediaAdapter(mActivity, shows,
                position -> openShow(shows, position));
        animeAdapter = new HomeMediaAdapter(mActivity, anime,
                position -> openMedia(anime, position));

        recentRecycler.setAdapter(recentAdapter);
        moviesRecycler.setAdapter(moviesAdapter);
        showsRecycler.setAdapter(showsAdapter);
        animeRecycler.setAdapter(animeAdapter);

        view.findViewById(R.id.homeSearchButton).setOnClickListener(v -> openFragment(new SearchFragment()));
        view.findViewById(R.id.viewAllRecent).setOnClickListener(v -> openLibraryTab(0));
        view.findViewById(R.id.viewAllMovies).setOnClickListener(v -> openLibraryTab(0));
        view.findViewById(R.id.viewAllShows).setOnClickListener(v -> openLibraryTab(1));
        view.findViewById(R.id.viewAllAnime).setOnClickListener(v -> openLibraryTab(2));
        view.findViewById(R.id.viewAllContinue).setOnClickListener(v -> openLibraryTab(0));

        refreshHomeData();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() == null) return;
        refreshHomeData();
        statusHandler.removeCallbacks(statusObserver);
        statusHandler.post(statusObserver);
    }

    @Override
    public void onPause() {
        statusHandler.removeCallbacks(statusObserver);
        super.onPause();
    }

    private void setupHorizontal(RecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(mActivity, RecyclerView.HORIZONTAL, false));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
    }

    private void refreshHomeData() {
        new Thread(() -> {
            DatabaseClient db = DatabaseClient.getInstance(mActivity);

            List<Movie> recent = safeMovies(db.getAppDatabase().movieDao().getrecentlyadded());
            List<Movie> allMovies = safeMovies(db.getAppDatabase().movieDao().getAll());
            List<TVShow> allShows = safeShows(db.getAppDatabase().tvShowDao().getAllByTitles());

            List<Movie> normalMovies = new ArrayList<>();
            List<MyMedia> animeItems = new ArrayList<>();
            for (Movie movie : allMovies) {
                if (MediaClassificationUtils.isAnime(movie)) animeItems.add(movie);
                else normalMovies.add(movie);
            }

            List<TVShow> normalShows = new ArrayList<>();
            for (TVShow show : allShows) {
                if (MediaClassificationUtils.isAnime(show)) animeItems.add(show);
                else normalShows.add(show);
            }

            List<ContinueWatchingAdapter.Item> resumeItems = new ArrayList<>();
            for (ResumeUtils.Entry entry : ResumeUtils.getEntries(mActivity)) {
                if (resumeItems.size() >= 12) break;
                Movie movie = db.getAppDatabase().movieDao().findByUrl(entry.url);
                if (movie != null) {
                    resumeItems.add(new ContinueWatchingAdapter.Item(movie, entry, year(movie.release_date)));
                    continue;
                }

                Episode episode = db.getAppDatabase().episodeDao().findByUrl(entry.url);
                if (episode == null) continue;
                TVShow show = db.getAppDatabase().tvShowDao().find(episode.getShow_id());
                if (show == null) continue;
                String ep = String.format("S%02d • E%02d",
                        Math.max(0, episode.getSeason_number()), Math.max(0, episode.getEpisode_number()));
                resumeItems.add(new ContinueWatchingAdapter.Item(show, entry, ep));
            }

            recentMovies = limitMovies(recent, HOME_LIMIT);
            movies = limitMovies(normalMovies, HOME_LIMIT);
            shows = limitShows(normalShows, HOME_LIMIT);
            anime = limitMedia(animeItems, HOME_LIMIT);

            mActivity.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;

                continueAdapter.submitList(resumeItems);
                recentAdapter.submitList(recentMovies);
                moviesAdapter.submitList(movies);
                showsAdapter.submitList(shows);
                animeAdapter.submitList(anime);

                continueSection.setVisibility(resumeItems.isEmpty() ? View.GONE : View.VISIBLE);
                recentSection.setVisibility(recentMovies.isEmpty() ? View.GONE : View.VISIBLE);
                moviesSection.setVisibility(movies.isEmpty() ? View.GONE : View.VISIBLE);
                showsSection.setVisibility(shows.isEmpty() ? View.GONE : View.VISIBLE);
                animeSection.setVisibility(anime.isEmpty() ? View.GONE : View.VISIBLE);
            });
        }, "MiracleHomeLoader").start();
    }

    private void openMovie(List<Movie> list, int position) {
        if (position < 0 || position >= list.size()) return;
        Movie movie = list.get(position);
        openFragment(movie.getId() != 0
                ? new MovieDetailsFragment(movie.getId())
                : new MovieDetailsFragment(movie.getFileName()));
    }

    private void openShow(List<TVShow> list, int position) {
        if (position < 0 || position >= list.size()) return;
        TVShow show = list.get(position);
        openFragment(new TvShowDetailsFragment(show.getId()));
    }

    private void openMedia(List<MyMedia> list, int position) {
        if (position < 0 || position >= list.size()) return;
        MyMedia media = list.get(position);
        if (media instanceof Movie) {
            Movie movie = (Movie) media;
            openFragment(movie.getId() != 0
                    ? new MovieDetailsFragment(movie.getId())
                    : new MovieDetailsFragment(movie.getFileName()));
        } else if (media instanceof TVShow) {
            openFragment(new TvShowDetailsFragment(((TVShow) media).getId()));
        }
    }

    private void openLibraryTab(int tab) {
        openFragment(LibraryFragment.newInstance(tab));
    }

    private void openFragment(androidx.fragment.app.Fragment fragment) {
        if (!isAdded()) return;
        mActivity.getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private String year(String date) {
        if (date == null || date.isEmpty()) return "Movie";
        int dash = date.indexOf('-');
        return dash > 0 ? date.substring(0, dash) : date;
    }

    private List<Movie> safeMovies(List<Movie> input) {
        return input == null ? new ArrayList<>() : input;
    }

    private List<TVShow> safeShows(List<TVShow> input) {
        return input == null ? new ArrayList<>() : input;
    }

    private List<Movie> limitMovies(List<Movie> input, int max) {
        return new ArrayList<>(input.subList(0, Math.min(max, input.size())));
    }

    private List<TVShow> limitShows(List<TVShow> input, int max) {
        return new ArrayList<>(input.subList(0, Math.min(max, input.size())));
    }

    private List<MyMedia> limitMedia(List<MyMedia> input, int max) {
        return new ArrayList<>(input.subList(0, Math.min(max, input.size())));
    }
}
