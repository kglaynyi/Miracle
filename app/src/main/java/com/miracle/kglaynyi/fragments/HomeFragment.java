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
import com.miracle.kglaynyi.adapter.BannerRecyclerAdapter;
import com.miracle.kglaynyi.adapter.MediaAdapter;
import com.miracle.kglaynyi.adapter.ScaleCenterItemLayoutManager;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.player.PlayerActivity;
import com.miracle.kglaynyi.utils.ResumeUtils;
import com.miracle.kglaynyi.utils.IndexUtils;

import java.util.ArrayList;
import java.util.List;


public class HomeFragment extends BaseFragment {

    BannerRecyclerAdapter recentlyAddedRecyclerAdapter;
    MediaAdapter recentlyReleasedRecyclerViewAdapter;
    MediaAdapter topRatedMoviesRecyclerViewAdapter;
    MediaAdapter lastPlayedMoviesRecyclerViewAdapter;
    MediaAdapter continueWatchingRecyclerViewAdapter;
    MediaAdapter watchlistRecyclerViewAdapter;

    MediaAdapter topRatedShowsRecyclerAdapter;
    MediaAdapter newSeasonRecyclerAdapter;



    TextView continueWatchingRecyclerViewTitle;
    RecyclerView continueWatchingRecyclerView;

    TextView recentlyAddedRecyclerViewTitle;
    RecyclerView recentlyAddedRecyclerView;

    TextView recentlyReleasedRecyclerViewTitle;
    RecyclerView recentlyReleasedRecyclerView;

    TextView topRatedMoviesRecyclerViewTitle;
    RecyclerView topRatedMoviesRecyclerView;

    TextView lastPlayedMoviesRecyclerViewTitle;
    RecyclerView lastPlayedMoviesRecyclerView;

    TextView watchlistRecyclerViewTitle;
    RecyclerView watchlistRecyclerView;

    TextView topRatedShowsRecyclerViewTitle;
    RecyclerView topRatedShowsRecyclerView;

    TextView newSeasonRecyclerViewTitle;
    RecyclerView newSeasonRecyclerView;

    List<MyMedia> continueWatchingMedia = new ArrayList<>();
    List<ResumeUtils.Entry> continueWatchingEntries = new ArrayList<>();

    List<Movie> recentlyAddedMovies;
    List<Movie> recentlyReleasedMovies;
    List<Movie> topRatedMovies;
    List<Movie> lastPlayedList;
    List<MyMedia> watchlist;
    List<TVShow> newSeason;
    List<TVShow> topRatedShows;

    BannerRecyclerAdapter.OnItemClickListener recentlyAddedListener;
    MediaAdapter.OnItemClickListener recentlyReleasedListener;
    MediaAdapter.OnItemClickListener topRatedMoviesListener;
    MediaAdapter.OnItemClickListener lastPlayedListener;
    MediaAdapter.OnItemClickListener watchlistListener;
    MediaAdapter.OnItemClickListener topRatedShowsListener;
    MediaAdapter.OnItemClickListener newSeasonListener;

    private final Handler startupRefreshHandler = new Handler(Looper.getMainLooper());
    private boolean sawStartupScan;
    private final Runnable startupRefreshObserver = new Runnable() {
        @Override public void run() {
            if (!isAdded() || getView() == null) return;

            if (IndexUtils.isAnyScanRunning()) {
                sawStartupScan = true;
                startupRefreshHandler.postDelayed(this, 1500L);
                return;
            }

            if (sawStartupScan) {
                sawStartupScan = false;
                refreshHomeSections();
            }
        }
    };

//    List<PairMovies> pairMoviesList;
//    List<PairTvShows> pairTvShowsList;

    public HomeFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        refreshHomeSections();
        setOnClickListner();


//        getLists();
//        loadRecyclerViews();

    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() == null) return;
        refreshHomeSections();
        startupRefreshHandler.removeCallbacks(startupRefreshObserver);
        startupRefreshHandler.postDelayed(startupRefreshObserver, 1200L);
    }

    @Override
    public void onPause() {
        startupRefreshHandler.removeCallbacks(startupRefreshObserver);
        super.onPause();
    }

    private void refreshHomeSections() {
        loadContinueWatching();
        loadRecentlyAddedMovies();
        loadRecentlyReleasedMovies();
        loadTopRatedMovies();
        loadLastPlayedMovies();
        loadWatchlist();
        loadNewSeason();
        loadTopRatedShows();
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

                continueWatchingRecyclerViewTitle =
                        mActivity.findViewById(R.id.continueWatchingTitle);
                continueWatchingRecyclerView =
                        mActivity.findViewById(R.id.continueWatchingRecycler);

                if (continueWatchingMedia.isEmpty()) {
                    continueWatchingRecyclerViewTitle.setVisibility(View.GONE);
                    continueWatchingRecyclerView.setVisibility(View.GONE);
                    return;
                }

                continueWatchingRecyclerViewTitle.setVisibility(View.VISIBLE);
                continueWatchingRecyclerView.setVisibility(View.VISIBLE);
                continueWatchingRecyclerView.setLayoutManager(
                        new ScaleCenterItemLayoutManager(getContext(),
                                LinearLayoutManager.HORIZONTAL, false));
                continueWatchingRecyclerView.setHasFixedSize(true);
                continueWatchingRecyclerView.setItemAnimator(null);

                MediaAdapter.OnItemClickListener listener = (view, position) -> {
                    if (position < 0 || position >= continueWatchingEntries.size()) return;
                    ResumeUtils.Entry entry = continueWatchingEntries.get(position);
                    Intent intent = new Intent(mActivity, PlayerActivity.class);
                    intent.putExtra("url", entry.url);
                    startActivity(intent);
                };

                if (continueWatchingRecyclerViewAdapter == null) {
                    continueWatchingRecyclerViewAdapter =
                            new MediaAdapter(getContext(), continueWatchingMedia, listener);
                    continueWatchingRecyclerView.setAdapter(continueWatchingRecyclerViewAdapter);
                } else {
                    continueWatchingRecyclerViewAdapter.submitList(continueWatchingMedia);
                }
            });
        }).start();
    }

    private void loadWatchlist() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                List<Movie> watchlistMovies = DatabaseClient
                        .getInstance(mActivity)
                        .getAppDatabase()
                        .movieDao()
                        .getWatchlisted();

                List<TVShow> watchlistShows = DatabaseClient
                        .getInstance(mActivity)
                        .getAppDatabase()
                        .tvShowDao()
                        .getWatchlisted();

                watchlist = new ArrayList<>();
                watchlist.addAll(watchlistMovies);
                watchlist.addAll(watchlistShows);
                if(watchlist!=null && watchlist.size()>0){
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ScaleCenterItemLayoutManager linearLayoutManager3 = new ScaleCenterItemLayoutManager(getContext() , LinearLayoutManager.HORIZONTAL , false);
                            watchlistRecyclerViewTitle = mActivity.findViewById(R.id.watchListMedia);
                            watchlistRecyclerViewTitle.setVisibility(View.VISIBLE);
                            watchlistRecyclerView = mActivity.findViewById(R.id.watchListMediaRecycler);
                            watchlistRecyclerView.setVisibility(View.VISIBLE);
                            watchlistRecyclerView.setLayoutManager(linearLayoutManager3);
                            watchlistRecyclerView.setHasFixedSize(true);
                            watchlistRecyclerViewAdapter = new MediaAdapter(getContext() ,(List<MyMedia>)(List<?>) watchlist , watchlistListener);
                            watchlistRecyclerView.setAdapter(watchlistRecyclerViewAdapter);
                        }
                    });

                }
            }});
        thread.start();
    }

    private void  loadRecentlyAddedMovies() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                recentlyAddedMovies = DatabaseClient
                        .getInstance(mActivity)
                        .getAppDatabase()
                        .movieDao()
                        .getrecentlyadded();
                if(recentlyAddedMovies!=null && recentlyAddedMovies.size()>0){
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            recentlyAddedRecyclerViewTitle = mActivity.findViewById(R.id.recentlyAdded);
                            recentlyAddedRecyclerViewTitle.setVisibility(View.VISIBLE);
                            recentlyAddedRecyclerView = mActivity.findViewById(R.id.recentlyAddedRecycler);
                            recentlyAddedRecyclerView.setLayoutManager(new ScaleCenterItemLayoutManager(getContext() , RecyclerView.HORIZONTAL , false));
                            recentlyAddedRecyclerView.setHasFixedSize(true);
                            recentlyAddedRecyclerAdapter = new BannerRecyclerAdapter(getContext(), recentlyAddedMovies , recentlyAddedListener);
                            recentlyAddedRecyclerView.setAdapter(recentlyAddedRecyclerAdapter);
                        }
                    });
                }
            }});
        thread.start();

    }

    private void loadRecentlyReleasedMovies () {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                recentlyReleasedMovies  = DatabaseClient
                        .getInstance(mActivity)
                        .getAppDatabase()
                        .movieDao()
                        .getrecentreleases();
                if(recentlyReleasedMovies!=null && recentlyReleasedMovies.size()>0){
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ScaleCenterItemLayoutManager linearLayoutManager1 = new ScaleCenterItemLayoutManager(getContext() , LinearLayoutManager.HORIZONTAL , false);
                            recentlyReleasedRecyclerViewTitle = mActivity.findViewById(R.id.newReleasesMovies);
                            recentlyReleasedRecyclerViewTitle.setVisibility(View.VISIBLE);
                            recentlyReleasedRecyclerView = mActivity.findViewById(R.id.recentlyReleasedMoviesRecycler);
                            recentlyReleasedRecyclerView.setLayoutManager(linearLayoutManager1);
                            recentlyReleasedRecyclerView.setHasFixedSize(true);
                            recentlyReleasedRecyclerViewAdapter = new MediaAdapter(getContext(),(List<MyMedia>)(List<?>) recentlyReleasedMovies, recentlyReleasedListener);
                            recentlyReleasedRecyclerView.setAdapter(recentlyReleasedRecyclerViewAdapter);
                        }
                    });

                }
            }});
        thread.start();
    }

    private void loadTopRatedMovies() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                topRatedMovies = DatabaseClient
                        .getInstance(mActivity)
                        .getAppDatabase()
                        .movieDao()
                        .getTopRated();
                if(topRatedMovies!=null && topRatedMovies.size()>0){
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ScaleCenterItemLayoutManager linearLayoutManager2 = new ScaleCenterItemLayoutManager(getContext() , LinearLayoutManager.HORIZONTAL , false);
                            topRatedMoviesRecyclerViewTitle = mActivity.findViewById(R.id.topRatedMovies);
                            topRatedMoviesRecyclerViewTitle.setVisibility(View.VISIBLE);
                            topRatedMoviesRecyclerView = mActivity.findViewById(R.id.topRatedMoviesRecycler);
                            topRatedMoviesRecyclerView.setLayoutManager(linearLayoutManager2);
                            topRatedMoviesRecyclerView.setHasFixedSize(true);
                            topRatedMoviesRecyclerViewAdapter = new MediaAdapter(getContext() ,(List<MyMedia>)(List<?>) topRatedMovies , topRatedMoviesListener);
                            topRatedMoviesRecyclerView.setAdapter(topRatedMoviesRecyclerViewAdapter);
                        }
                    });
                }
            }});
        thread.start();
    }

    private void loadLastPlayedMovies() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                lastPlayedList = DatabaseClient
                        .getInstance(mActivity)
                        .getAppDatabase()
                        .movieDao()
                        .getPlayed();

                if(lastPlayedList!=null && lastPlayedList.size()>0){
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ScaleCenterItemLayoutManager linearLayoutManager3 = new ScaleCenterItemLayoutManager(getContext() , LinearLayoutManager.HORIZONTAL , false);
                            lastPlayedMoviesRecyclerViewTitle = mActivity.findViewById(R.id.lastPlayedMovies);
                            lastPlayedMoviesRecyclerViewTitle.setVisibility(View.VISIBLE);
                            lastPlayedMoviesRecyclerView = mActivity.findViewById(R.id.lastPlayedMoviesRecycler);
                            lastPlayedMoviesRecyclerView.setVisibility(View.VISIBLE);
                            lastPlayedMoviesRecyclerView.setLayoutManager(linearLayoutManager3);
                            lastPlayedMoviesRecyclerView.setHasFixedSize(true);
                            lastPlayedMoviesRecyclerViewAdapter = new MediaAdapter(getContext() ,(List<MyMedia>)(List<?>) lastPlayedList , lastPlayedListener);
                            lastPlayedMoviesRecyclerView.setAdapter(lastPlayedMoviesRecyclerViewAdapter);
                        }
                    });

                }
            }});
        thread.start();
    }

    private void loadNewSeason(){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                newSeason = DatabaseClient
                        .getInstance(mActivity)
                        .getAppDatabase()
                        .tvShowDao()
                        .getNewShows();
                if(newSeason!=null && newSeason.size()>0){
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ScaleCenterItemLayoutManager linearLayoutManager3 = new ScaleCenterItemLayoutManager(getContext() , LinearLayoutManager.HORIZONTAL , false);
                            newSeasonRecyclerViewTitle = mActivity.findViewById(R.id.newSeason);
                            newSeasonRecyclerViewTitle.setVisibility(View.VISIBLE);
                            newSeasonRecyclerView = mActivity.findViewById(R.id.newSeasonRecycler);
                            newSeasonRecyclerView.setVisibility(View.VISIBLE);
                            newSeasonRecyclerView = mActivity.findViewById(R.id.newSeasonRecycler);
                            newSeasonRecyclerView.setLayoutManager(linearLayoutManager3);
                            newSeasonRecyclerView.setHasFixedSize(true);
                            newSeasonRecyclerAdapter = new MediaAdapter(getContext() ,(List<MyMedia>)(List<?>) newSeason , newSeasonListener);
                            newSeasonRecyclerView.setAdapter(newSeasonRecyclerAdapter);
                        }
                    });
                }
            }});
        thread.start();
    }

    private void loadTopRatedShows(){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                topRatedShows = DatabaseClient
                        .getInstance(mActivity)
                        .getAppDatabase()
                        .tvShowDao()
                        .getTopRated();
                if(topRatedShows!=null && topRatedShows.size()>0){
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ScaleCenterItemLayoutManager linearLayoutManager3 = new ScaleCenterItemLayoutManager(getContext() , LinearLayoutManager.HORIZONTAL , false);
                            topRatedShowsRecyclerViewTitle = mActivity.findViewById(R.id.topRatedTVShows);
                            topRatedShowsRecyclerViewTitle.setVisibility(View.VISIBLE);
                            topRatedShowsRecyclerView = mActivity.findViewById(R.id.topRatedTVShowsRecycler);
                            topRatedShowsRecyclerView.setLayoutManager(linearLayoutManager3);
                            topRatedShowsRecyclerView.setHasFixedSize(true);
                            topRatedShowsRecyclerAdapter = new MediaAdapter(getContext() , (List<MyMedia>)(List<?>) topRatedShows , topRatedShowsListener);
                            topRatedShowsRecyclerView.setAdapter(topRatedShowsRecyclerAdapter);
                        }
                    });
                }
            }});
        thread.start();
    }



    private void setOnClickListner() {
        recentlyAddedListener = new BannerRecyclerAdapter.OnItemClickListener() {
            @Override
            public void onClick(View view, int position) {
                MovieDetailsFragment movieDetailsFragment = new MovieDetailsFragment(recentlyAddedMovies.get(position).getId());
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in,R.anim.fade_out,R.anim.fade_in,R.anim.fade_out)
                        .add(R.id.container,movieDetailsFragment).addToBackStack(null).commit();
            }
        };
        recentlyReleasedListener = new MediaAdapter.OnItemClickListener() {
            @Override
            public void onClick(View view, int position) {
                MovieDetailsFragment movieDetailsFragment = new MovieDetailsFragment(recentlyReleasedMovies.get(position).getId());
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in,R.anim.fade_out,R.anim.fade_in,R.anim.fade_out)
                        .add(R.id.container,movieDetailsFragment).addToBackStack(null).commit();

            }
        };
        topRatedMoviesListener = new MediaAdapter.OnItemClickListener() {
            @Override
            public void onClick(View view, int position) {
                MovieDetailsFragment movieDetailsFragment = new MovieDetailsFragment(topRatedMovies.get(position).getId());
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in,R.anim.fade_out,R.anim.fade_in,R.anim.fade_out)
                        .add(R.id.container,movieDetailsFragment).addToBackStack(null).commit();
            }
        };
        lastPlayedListener = new MediaAdapter.OnItemClickListener() {
            @Override
            public void onClick(View view, int position) {
                MovieDetailsFragment movieDetailsFragment = new MovieDetailsFragment(lastPlayedList.get(position).getId());
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in,R.anim.fade_out,R.anim.fade_in,R.anim.fade_out)
                        .add(R.id.container,movieDetailsFragment).addToBackStack(null).commit();
            }
        };

        watchlistListener = new MediaAdapter.OnItemClickListener() {
            @Override
            public void onClick(View view , int position) {
                int id =0;
                if(watchlist.get(position) instanceof Movie){
                    id = ((Movie) watchlist.get(position)).getId();
                    MovieDetailsFragment movieDetailsFragment = new MovieDetailsFragment(id);
                    mActivity.getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(R.anim.fade_in,R.anim.fade_out,R.anim.fade_in,R.anim.fade_out)
                            .add(R.id.container,movieDetailsFragment).addToBackStack(null).commit();
                }else{
                    id = ((TVShow)(watchlist.get(position))).getId();
                    TvShowDetailsFragment tvShowDetailsFragment = new TvShowDetailsFragment(id);
                    mActivity.getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(R.anim.fade_in,R.anim.fade_out,R.anim.fade_in,R.anim.fade_out)
                            .add(R.id.container,tvShowDetailsFragment).addToBackStack(null).commit();
                }

            }
        };

        newSeasonListener = new MediaAdapter.OnItemClickListener() {
            @Override
            public void onClick(View view, int position) {
                TvShowDetailsFragment tvShowDetailsFragment = new TvShowDetailsFragment(newSeason.get(position).getId());
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in,R.anim.fade_out)
                        .add(R.id.container,tvShowDetailsFragment).addToBackStack(null).commit();
            }
        };
        topRatedShowsListener = new MediaAdapter.OnItemClickListener() {
            @Override
            public void onClick(View view, int position) {
                TvShowDetailsFragment tvShowDetailsFragment = new TvShowDetailsFragment(topRatedShows.get(position).getId());
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in,R.anim.fade_out)
                        .add(R.id.container,tvShowDetailsFragment).addToBackStack(null).commit();
            }
        };
    }

//    private void loadRecentlyAdded(){
////        setOnClickListner();
//        //                pairMoviesList = new ArrayList<>();
////                pairMoviesList.add(new PairMovies("RecentlyAdded",recentlyAddedList));
////                pairMoviesList.add(new PairMovies("NewReleases",newmediaList));
////                pairMoviesList.add(new PairMovies("TopRated",topRatedList));
////                pairMoviesList.add(new PairMovies("RecentlyPlayed",lastPlayedList));
////
////
////
////                pairTvShowsList= new ArrayList<>();
////                pairTvShowsList.add(new PairTvShows("NewShows",newShows));
////                pairTvShowsList.add(new PairTvShows("TopRated",topRatedShows));
////
////
////                List<Pair> items = new ArrayList<>();
////                items.addAll(pairMoviesList);
////                int cutListIndex = items.size();
////                items.addAll(pairTvShowsList);
////
////                mActivity.runOnUiThread(new Runnable() {
////                    @Override
////                    public void run() {
////                recyclerView = mActivity.findViewById(R.id.nestedRecycler);
////                recyclerView.setLayoutManager(new LinearLayoutManager(getContext(),RecyclerView.VERTICAL,false));
////                recyclerView.setHasFixedSize(true);
////                HomeItemAdapter homeItemAdapter = new HomeItemAdapter(getContext(),items,cutListIndex);
////                recyclerView.setAdapter(homeItemAdapter);
////                homeItemAdapter.notifyDataSetChanged();
////                    }
////                });
//    }
//    private void loadRecyclerViews(){}
//    private void getLists(){
//        Thread thread = new Thread(new Runnable() {
//            @Override
//            public void run() {
////                recentlyAddedMovies = DatabaseClient
////                        .getInstance(mActivity)
////                        .getAppDatabase()
////                        .movieDao()
////                        .getrecentlyadded();
////
////                recentlyReleasedMovies  = DatabaseClient
////                        .getInstance(mActivity)
////                        .getAppDatabase()
////                        .movieDao()
////                        .getrecentreleases();
////
////                topRatedMovies = DatabaseClient
////                        .getInstance(mActivity)
////                        .getAppDatabase()
////                        .movieDao()
////                        .getTopRated();
////
////                lastPlayedList = DatabaseClient
////                        .getInstance(mActivity)
////                        .getAppDatabase()
////                        .movieDao()
////                        .getPlayed();
//
////                newSeason = DatabaseClient
////                        .getInstance(mActivity)
////                        .getAppDatabase()
////                        .tvShowDao()
////                        .getNewShows();
////
////                topRatedShows = DatabaseClient
////                        .getInstance(mActivity)
////                        .getAppDatabase()
////                        .tvShowDao()
////                        .getTopRated();
//            }});
//        thread.start();
//    }
}