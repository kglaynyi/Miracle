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
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.utils.GenreFilterUtils;
import com.miracle.kglaynyi.utils.IndexUtils;
import com.miracle.kglaynyi.utils.MediaClassificationUtils;

import java.util.ArrayList;
import java.util.List;

public class TvShowsLibraryFragment extends BaseFragment {
    private RecyclerView recyclerViewTVShows;
    private Spinner genreSpinner;
    private MediaAdapter mediaAdapter;
    private List<TVShow> allShows = new ArrayList<>();
    private List<TVShow> tvShowList = new ArrayList<>();
    private String selectedGenre = GenreFilterUtils.ALL_GENRES;
    private boolean spinnerReady;

    private final Handler libraryRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable libraryRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (!isAdded() || getView() == null) return;
            showLibraryTVShows();
            if (IndexUtils.isAnyScanRunning()) libraryRefreshHandler.postDelayed(this, 1600);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tv_shows_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerViewTVShows = view.findViewById(R.id.recyclerLibraryTVShows);
        genreSpinner = view.findViewById(R.id.genreFilterTVShows);

        DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();
        int columns = Math.max(1, (int) ((metrics.widthPixels / metrics.density) / 120));
        recyclerViewTVShows.setLayoutManager(new GridLayoutManager(mActivity, columns));
        recyclerViewTVShows.setHasFixedSize(true);
        recyclerViewTVShows.setItemAnimator(null);
        recyclerViewTVShows.setAlpha(0f);

        MediaAdapter.OnItemClickListener listener = (v, position) -> {
            if (position < 0 || position >= tvShowList.size()) return;
            TvShowDetailsFragment details = new TvShowDetailsFragment(tvShowList.get(position).getId());
            mActivity.getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.container, details).addToBackStack(null).commit();
        };
        mediaAdapter = new MediaAdapter(mActivity, new ArrayList<MyMedia>(), listener);
        recyclerViewTVShows.setAdapter(mediaAdapter);
        showLibraryTVShows();
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

    private void showLibraryTVShows() {
        new Thread(() -> {
            List<TVShow> all = DatabaseClient.getInstance(mActivity).getAppDatabase().tvShowDao().getAllByTitles();
            List<TVShow> filtered = new ArrayList<>();
            for (TVShow show : all) if (!MediaClassificationUtils.isAnime(show)) filtered.add(show);
            allShows = filtered;
            applyGenreOnUi();
        }).start();
    }

    private void applyGenreOnUi() {
        mActivity.runOnUiThread(() -> {
            if (!isAdded() || getView() == null) return;
            List<String> genres = GenreFilterUtils.collectGenres((List<? extends MyMedia>)(List<?>) allShows);
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(mActivity, R.layout.item_genre_filter, genres);
            spinnerAdapter.setDropDownViewResource(R.layout.item_genre_filter);
            int selectedIndex = Math.max(0, genres.indexOf(selectedGenre));

            spinnerReady = false;
            genreSpinner.setAdapter(spinnerAdapter);
            genreSpinner.setSelection(selectedIndex, false);
            genreSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (!spinnerReady) { spinnerReady = true; return; }
                    selectedGenre = genres.get(position);
                    updateVisibleList();
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
            updateVisibleList();
        });
    }

    private void updateVisibleList() {
        tvShowList = GenreFilterUtils.filter(allShows, selectedGenre);
        mediaAdapter.submitList((List<? extends MyMedia>)(List<?>) tvShowList);
        if (recyclerViewTVShows.getAlpha() == 0f) {
            recyclerViewTVShows.animate().alpha(1f).setDuration(180).start();
        }
    }
}
