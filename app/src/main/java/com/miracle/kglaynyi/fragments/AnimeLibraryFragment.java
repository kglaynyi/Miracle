package com.miracle.kglaynyi.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.adapter.MediaAdapter;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.utils.GenreFilterUtils;
import com.miracle.kglaynyi.utils.IndexUtils;
import com.miracle.kglaynyi.utils.MediaClassificationUtils;

import java.util.ArrayList;
import java.util.List;

public class AnimeLibraryFragment extends BaseFragment {
    private RecyclerView recycler;
    private Spinner genreSpinner;
    private MediaAdapter mediaAdapter;
    private List<MyMedia> allAnime = new ArrayList<>();
    private List<MyMedia> animeList = new ArrayList<>();
    private String selectedGenre = GenreFilterUtils.ALL_GENRES;
    private List<String> genreOptions = new ArrayList<>();

    private final Handler libraryRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable libraryRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (!isAdded() || getView() == null) return;
            loadAnime();
            if (IndexUtils.isAnyScanRunning()) libraryRefreshHandler.postDelayed(this, 1600);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_anime_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recycler = view.findViewById(R.id.recyclerLibraryAnime);
        genreSpinner = view.findViewById(R.id.genreFilterAnime);

        DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();
        int columns = Math.max(1, (int) ((metrics.widthPixels / metrics.density) / 120));
        recycler.setLayoutManager(new GridLayoutManager(mActivity, columns));
        recycler.setHasFixedSize(true);
        recycler.setItemAnimator(null);
        recycler.setAlpha(0f);

        MediaAdapter.OnItemClickListener listener = (v, position) -> {
            if (position < 0 || position >= animeList.size()) return;
            MyMedia media = animeList.get(position);
            if (media instanceof Movie) {
                Movie movie = (Movie) media;
                MovieDetailsFragment details = movie.getId() != 0
                        ? new MovieDetailsFragment(movie.getId())
                        : new MovieDetailsFragment(movie.getFileName());
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.container, details).addToBackStack(null).commit();
            } else if (media instanceof TVShow) {
                TvShowDetailsFragment details = new TvShowDetailsFragment(((TVShow) media).getId());
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.container, details).addToBackStack(null).commit();
            }
        };
        mediaAdapter = new MediaAdapter(mActivity, new ArrayList<MyMedia>(), listener);
        recycler.setAdapter(mediaAdapter);
        loadAnime();
    }

    @Override public void onResume() {
        super.onResume();
        libraryRefreshHandler.removeCallbacks(libraryRefreshRunnable);
        libraryRefreshHandler.post(libraryRefreshRunnable);
    }

    @Override public void onPause() {
        libraryRefreshHandler.removeCallbacks(libraryRefreshRunnable);
        super.onPause();
    }

    private void loadAnime() {
        new Thread(() -> {
            List<MyMedia> result = new ArrayList<>();
            for (Movie movie : DatabaseClient.getInstance(mActivity).getAppDatabase().movieDao().getAll()) {
                if (MediaClassificationUtils.isAnime(movie)) result.add(movie);
            }
            for (TVShow show : DatabaseClient.getInstance(mActivity).getAppDatabase().tvShowDao().getAllByTitles()) {
                if (MediaClassificationUtils.isAnime(show)) result.add(show);
            }
            allAnime = result;
            applyGenreOnUi();
        }).start();
    }

    private void applyGenreOnUi() {
        mActivity.runOnUiThread(() -> {
            if (!isAdded() || getView() == null) return;
            List<String> genres = GenreFilterUtils.collectGenres(allAnime);

            if (!genreOptions.equals(genres)) {
                genreOptions = new ArrayList<>(genres);
                ArrayAdapter<String> spinnerAdapter =
                        new ArrayAdapter<>(mActivity, R.layout.item_genre_filter, genreOptions);
                spinnerAdapter.setDropDownViewResource(R.layout.item_genre_filter);

                genreSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                         int position, long itemId) {
                        if (position < 0 || position >= genreOptions.size()) return;
                        selectedGenre = genreOptions.get(position);
                        updateVisibleList();
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });
                genreSpinner.setAdapter(spinnerAdapter);
                int selectedIndex = Math.max(0, genreOptions.indexOf(selectedGenre));
                genreSpinner.setSelection(selectedIndex, false);
            }
            updateVisibleList();
        });
    }

    private void updateVisibleList() {
        animeList = GenreFilterUtils.filter(allAnime, selectedGenre);
        mediaAdapter.submitList(animeList);
        if (recycler.getAlpha() == 0f) recycler.animate().alpha(1f).setDuration(180).start();
    }
}
