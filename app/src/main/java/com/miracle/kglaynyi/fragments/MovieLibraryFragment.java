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
import com.miracle.kglaynyi.utils.MediaClassificationUtils;
import com.miracle.kglaynyi.utils.IndexUtils;

import java.util.ArrayList;
import java.util.List;

public class MovieLibraryFragment extends BaseFragment {
    private RecyclerView recyclerViewMovies;
    private List<Movie> movieList = new ArrayList<>();

    private final Handler libraryRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable libraryRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (!isAdded() || getView() == null) return;
            showLibraryMovies();
            if (IndexUtils.isAnyScanRunning()) {
                libraryRefreshHandler.postDelayed(this, 1200);
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_movie_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        showLibraryMovies();
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

    private void showLibraryMovies() {
        new Thread(() -> {
            List<Movie> all = DatabaseClient.getInstance(mActivity).getAppDatabase().movieDao().getAll();
            List<Movie> filtered = new ArrayList<>();
            for (Movie movie : all) if (!MediaClassificationUtils.isAnime(movie)) filtered.add(movie);
            movieList = filtered;
            showRecyclerMovies(filtered);
        }).start();
    }

    private void showRecyclerMovies(List<Movie> list) {
        mActivity.runOnUiThread(() -> {
            DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();
            int columns = Math.max(1, (int) ((metrics.widthPixels / metrics.density) / 120));
            recyclerViewMovies = mActivity.findViewById(R.id.recyclerLibraryMovies);
            if (recyclerViewMovies == null) return;
            recyclerViewMovies.setLayoutManager(new GridLayoutManager(mActivity, columns));
            recyclerViewMovies.setHasFixedSize(true);
            MediaAdapter.OnItemClickListener listener = (view, position) -> {
                if (position < 0 || position >= movieList.size()) return;
                Movie movie = movieList.get(position);
                MovieDetailsFragment details = movie.getId() != 0
                        ? new MovieDetailsFragment(movie.getId())
                        : new MovieDetailsFragment(movie.getFileName());
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .add(R.id.container, details).addToBackStack(null).commit();
            };
            recyclerViewMovies.setAdapter(new MediaAdapter(mActivity, (List<MyMedia>)(List<?>) list, listener));
        });
    }
}
