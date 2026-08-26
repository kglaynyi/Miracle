package com.miracle.kglaynyi.fragments;

import android.os.Bundle;
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
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.utils.MediaClassificationUtils;

import java.util.ArrayList;
import java.util.List;

public class TvShowsLibraryFragment extends BaseFragment {
    private RecyclerView recyclerViewTVShows;
    private List<TVShow> tvShowList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tv_shows_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        showLibraryTVShows();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) showLibraryTVShows();
    }

    private void showLibraryTVShows() {
        new Thread(() -> {
            List<TVShow> all = DatabaseClient.getInstance(mActivity).getAppDatabase().tvShowDao().getAllByTitles();
            List<TVShow> filtered = new ArrayList<>();
            for (TVShow show : all) if (!MediaClassificationUtils.isAnime(show)) filtered.add(show);
            tvShowList = filtered;
            showRecycler(filtered);
        }).start();
    }

    private void showRecycler(List<TVShow> list) {
        mActivity.runOnUiThread(() -> {
            DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();
            int columns = Math.max(1, (int) ((metrics.widthPixels / metrics.density) / 120));
            recyclerViewTVShows = mActivity.findViewById(R.id.recyclerLibraryTVShows);
            if (recyclerViewTVShows == null) return;
            recyclerViewTVShows.setLayoutManager(new GridLayoutManager(mActivity, columns));
            recyclerViewTVShows.setHasFixedSize(true);
            MediaAdapter.OnItemClickListener listener = (view, position) -> {
                if (position < 0 || position >= tvShowList.size()) return;
                TvShowDetailsFragment details = new TvShowDetailsFragment(tvShowList.get(position).getId());
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .add(R.id.container, details).addToBackStack(null).commit();
            };
            recyclerViewTVShows.setAdapter(new MediaAdapter(mActivity, (List<MyMedia>)(List<?>) list, listener));
        });
    }
}
