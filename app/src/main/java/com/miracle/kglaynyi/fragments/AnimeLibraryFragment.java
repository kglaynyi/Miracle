package com.miracle.kglaynyi.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
import com.miracle.kglaynyi.utils.MediaClassificationUtils;
import com.miracle.kglaynyi.utils.IndexUtils;

import java.util.ArrayList;
import java.util.List;

public class AnimeLibraryFragment extends BaseFragment {
    private final List<MyMedia> animeList = new ArrayList<>();

    private final Handler libraryRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable libraryRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (!isAdded() || getView() == null) return;
            loadAnime();
            if (IndexUtils.isAnyScanRunning()) {
                libraryRefreshHandler.postDelayed(this, 1200);
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_anime_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadAnime();
    }

    @Override
    public void onResume() {
        super.onResume();
        libraryRefreshHandler.removeCallbacks(libraryRefreshRunnable);
        libraryRefreshHandler.post(libraryRefreshRunnable);
    }

    @Override
    public void onPause() {
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
            synchronized (animeList) {
                animeList.clear();
                animeList.addAll(result);
            }
            showRecycler(new ArrayList<>(result));
        }).start();
    }

    private void showRecycler(List<MyMedia> list) {
        mActivity.runOnUiThread(() -> {
            RecyclerView recycler = mActivity.findViewById(R.id.recyclerLibraryAnime);
            if (recycler == null) return;
            DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();
            int columns = Math.max(1, (int) ((metrics.widthPixels / metrics.density) / 120));
            recycler.setLayoutManager(new GridLayoutManager(mActivity, columns));
            recycler.setHasFixedSize(true);
            MediaAdapter.OnItemClickListener listener = (view, position) -> {
                MyMedia media;
                synchronized (animeList) {
                    if (position < 0 || position >= animeList.size()) return;
                    media = animeList.get(position);
                }
                if (media instanceof Movie) {
                    Movie movie = (Movie) media;
                    MovieDetailsFragment details = movie.getId() != 0
                            ? new MovieDetailsFragment(movie.getId())
                            : new MovieDetailsFragment(movie.getFileName());
                    mActivity.getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                            .add(R.id.container, details).addToBackStack(null).commit();
                } else if (media instanceof TVShow) {
                    TvShowDetailsFragment details = new TvShowDetailsFragment(((TVShow) media).getId());
                    mActivity.getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                            .add(R.id.container, details).addToBackStack(null).commit();
                }
            };
            recycler.setAdapter(new MediaAdapter(mActivity, list, listener));
        });
    }
}
