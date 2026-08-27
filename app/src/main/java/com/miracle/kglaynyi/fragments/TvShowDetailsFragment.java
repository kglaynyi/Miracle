package com.miracle.kglaynyi.fragments;

import static com.miracle.kglaynyi.Constants.TMDB_BACKDROP_IMAGE_BASE_URL;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.adapter.CastAdapter;
import com.miracle.kglaynyi.adapter.EpisodePreviewAdapter;
import com.miracle.kglaynyi.adapter.MediaAdapter;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.CreditPerson;
import com.miracle.kglaynyi.model.Genre;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.model.TVShowInfo.TVShowSeasonDetails;
import com.miracle.kglaynyi.player.PlayerActivity;
import com.miracle.kglaynyi.utils.MediaSourceDeduplicator;
import com.miracle.kglaynyi.utils.MovieQualityExtractor;
import com.miracle.kglaynyi.utils.PlaybackHistoryUtils;
import com.miracle.kglaynyi.utils.ResumeUtils;
import com.miracle.kglaynyi.utils.TmdbCreditsClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TvShowDetailsFragment extends BaseFragment {

    private int tvShowId;
    private TextView tvShowTitleText;
    private TextView numberOfSeasons;
    private TextView numberOfEpisodes;
    private TextView foundEpisodesText;
    private TextView overview;
    private TextView genresText;
    private TextView continueWatching;
    private TextView episodeTitle;
    private TextView ratingsText;
    private TextView castTitle;
    private TextView episodePreviewViewAll;
    private View episodePreviewHeader;
    private ImageView logo;
    private ImageView dot3;
    private ImageView dot1;
    private ImageView backdrop;
    private Button play;
    private ImageButton changeTMDB;
    private ImageButton addToList;

    private RecyclerView recyclerViewSeasons;
    private RecyclerView episodePreviewRecycler;
    private RecyclerView castRecycler;
    private List<TVShowSeasonDetails> seasonsList = new ArrayList<>();
    private List<Episode> availableEpisodes = new ArrayList<>();
    private MediaAdapter mediaAdapter;
    private MediaAdapter.OnItemClickListener listenerSeasonItem;

    private TVShow tvShowDetails;
    private Episode continueEpisode;

    public TvShowDetailsFragment() {}

    public TvShowDetailsFragment(int tvShowId) {
        this.tvShowId = tvShowId;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_show_details_new, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initWidgets(view);
        loadDetails();
    }

    private void initWidgets(View view) {
        tvShowTitleText = view.findViewById(R.id.tvShowTitle);
        logo = view.findViewById(R.id.tvLogo);
        numberOfSeasons = view.findViewById(R.id.noOfSeasons);
        numberOfEpisodes = view.findViewById(R.id.noOfEpisodes);
        foundEpisodesText = view.findViewById(R.id.foundEpisodesText);
        overview = view.findViewById(R.id.overviewDescTVShow);
        backdrop = view.findViewById(R.id.tvShowBackdrop);
        genresText = view.findViewById(R.id.tvShowGenresText);
        continueWatching = view.findViewById(R.id.continueWatchingText);
        dot3 = view.findViewById(R.id.dot3);
        dot1 = view.findViewById(R.id.dot);
        episodeTitle = view.findViewById(R.id.episodeNameInTv);
        ratingsText = view.findViewById(R.id.ratingsTVText);
        play = view.findViewById(R.id.heroPlayButtonTV);
        changeTMDB = view.findViewById(R.id.changeShowTMDBId);
        addToList = view.findViewById(R.id.addToListButtonTV);
        recyclerViewSeasons = view.findViewById(R.id.recyclerSeasons);
        episodePreviewRecycler = view.findViewById(R.id.episodePreviewRecycler);
        episodePreviewHeader = view.findViewById(R.id.episodePreviewHeader);
        episodePreviewViewAll = view.findViewById(R.id.episodePreviewViewAll);
        castTitle = view.findViewById(R.id.castTitleTV);
        castRecycler = view.findViewById(R.id.castRecyclerTV);
    }

    private void loadDetails() {
        new Thread(() -> {
            tvShowDetails = DatabaseClient.getInstance(mActivity)
                    .getAppDatabase().tvShowDao().find(tvShowId);
            if (tvShowDetails == null) {
                mActivity.runOnUiThread(() ->
                        Toast.makeText(mActivity, "Show details are unavailable", Toast.LENGTH_LONG).show());
                return;
            }

            availableEpisodes = DatabaseClient.getInstance(mActivity)
                    .getAppDatabase().episodeDao().getAvailableEpisodesForShow(tvShowDetails.getId());
            if (availableEpisodes == null) availableEpisodes = new ArrayList<>();

            seasonsList = DatabaseClient.getInstance(mActivity)
                    .getAppDatabase().tvShowSeasonDetailsDao().findByShowId(tvShowDetails.getId());
            if (seasonsList == null) seasonsList = new ArrayList<>();

            continueEpisode = chooseContinueEpisode(availableEpisodes);
            List<CreditPerson> credits = TmdbCreditsClient.fetch(true, tvShowDetails.getId());

            mActivity.runOnUiThread(() -> {
                if (!isAdded()) return;
                bindShowDetails();
                bindEpisodePreview();
                bindSeasons();
                bindCast(credits);
                bindActions();
            });
        }, "MiracleShowDetails").start();
    }

    private Episode chooseContinueEpisode(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return null;

        for (Episode episode : episodes) {
            long position = ResumeUtils.getPositionForMedia(
                    mActivity, episodeResumeKey(episode), episode.getUrlString());
            if (position >= 10_000L) return episode;
        }

        for (Episode episode : episodes) {
            if (!PlaybackHistoryUtils.isCompleted(mActivity, episodeResumeKey(episode))) {
                return episode;
            }
        }
        return episodes.get(0);
    }

    private void bindShowDetails() {
        String logoLink = tvShowDetails.getLogo_path();
        if (logoLink != null && !logoLink.trim().isEmpty()) {
            logo.setVisibility(View.VISIBLE);
            Glide.with(mActivity)
                    .load(logoLink)
                    .apply(new RequestOptions().fitCenter().override(Target.SIZE_ORIGINAL))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(new ColorDrawable(Color.TRANSPARENT))
                    .into(logo);
        } else {
            logo.setVisibility(View.INVISIBLE);
        }

        tvShowTitleText.setVisibility(View.VISIBLE);
        tvShowTitleText.setText(tvShowDetails.getName() == null
                ? "TV Show" : tvShowDetails.getName());

        if (tvShowDetails.getGenres() != null) {
            StringBuilder genres = new StringBuilder();
            for (Genre genre : tvShowDetails.getGenres()) {
                if (genre == null || genre.getName() == null) continue;
                if (genres.length() > 0) genres.append(", ");
                genres.append(genre.getName());
            }
            genresText.setText(genres.toString());
        }

        if (tvShowDetails.getVote_average() > 0) {
            dot1.setVisibility(View.VISIBLE);
            ratingsText.setVisibility(View.VISIBLE);
            ratingsText.setText(String.format(Locale.US, "%.1f", tvShowDetails.getVote_average()));
        }

        numberOfSeasons.setText(tvShowDetails.getNumber_of_seasons() + " Seasons");
        numberOfEpisodes.setText(tvShowDetails.getNumber_of_episodes() + " Episodes");
        foundEpisodesText.setVisibility(View.VISIBLE);
        foundEpisodesText.setText(tvShowDetails.getNumber_of_episodes()
                + " Episodes (Found " + availableEpisodes.size() + " episodes)");

        overview.setText(tvShowDetails.getOverview() == null ? "" : tvShowDetails.getOverview());

        String image = tvShowDetails.getBackdrop_path();
        if ((image == null || image.trim().isEmpty())
                && tvShowDetails.getPoster_path() != null) {
            image = tvShowDetails.getPoster_path();
        }
        if (image != null && !image.trim().isEmpty()) {
            Glide.with(mActivity)
                    .load(TMDB_BACKDROP_IMAGE_BASE_URL + image)
                    .placeholder(new ColorDrawable(Color.BLACK))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(backdrop);
        }

        updateContinueUi();
    }

    private void updateContinueUi() {
        if (continueEpisode == null) {
            play.setEnabled(false);
            play.setText("No playable episodes");
            continueWatching.setVisibility(View.GONE);
            episodeTitle.setVisibility(View.GONE);
            dot3.setVisibility(View.GONE);
            return;
        }

        String code = String.format(Locale.US, "S%02dE%02d",
                continueEpisode.getSeason_number(), continueEpisode.getEpisode_number());
        continueWatching.setVisibility(View.VISIBLE);
        continueWatching.setText(code);

        if (continueEpisode.getName() != null && !continueEpisode.getName().trim().isEmpty()) {
            dot3.setVisibility(View.VISIBLE);
            episodeTitle.setVisibility(View.VISIBLE);
            episodeTitle.setText(continueEpisode.getName());
        }

        long position = ResumeUtils.getPositionForMedia(
                mActivity, episodeResumeKey(continueEpisode), continueEpisode.getUrlString());
        if (position >= 10_000L) {
            play.setText("Resume " + code + " • " + formatResumeTime(position));
        } else {
            play.setText("Play " + code);
        }
    }

    private void bindEpisodePreview() {
        if (availableEpisodes.isEmpty()) {
            episodePreviewHeader.setVisibility(View.GONE);
            episodePreviewRecycler.setVisibility(View.GONE);
            return;
        }

        episodePreviewHeader.setVisibility(View.VISIBLE);
        episodePreviewRecycler.setVisibility(View.VISIBLE);
        episodePreviewRecycler.setLayoutManager(
                new LinearLayoutManager(mActivity, RecyclerView.HORIZONTAL, false));
        episodePreviewRecycler.setAdapter(
                new EpisodePreviewAdapter(mActivity, availableEpisodes));

        episodePreviewViewAll.setOnClickListener(v -> {
            if (seasonsList == null || seasonsList.isEmpty()) return;
            openSeason(seasonsList.get(0));
        });
    }

    private void bindSeasons() {
        listenerSeasonItem = (view, position) -> {
            if (position < 0 || position >= seasonsList.size()) return;
            openSeason(seasonsList.get(position));
        };

        recyclerViewSeasons.setLayoutManager(
                new LinearLayoutManager(mActivity, RecyclerView.HORIZONTAL, false));
        recyclerViewSeasons.setHasFixedSize(true);
        mediaAdapter = new MediaAdapter(mActivity,
                (List<MyMedia>) (List<?>) seasonsList, listenerSeasonItem);
        recyclerViewSeasons.setAdapter(mediaAdapter);
    }

    private void openSeason(TVShowSeasonDetails season) {
        SeasonDetailsFragment fragment = new SeasonDetailsFragment(tvShowDetails, season);
        mActivity.getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out,
                        R.anim.fade_in, R.anim.fade_out)
                .add(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void bindCast(List<CreditPerson> credits) {
        if (credits == null || credits.isEmpty()) return;
        castTitle.setVisibility(View.VISIBLE);
        castRecycler.setVisibility(View.VISIBLE);
        castRecycler.setLayoutManager(
                new LinearLayoutManager(mActivity, RecyclerView.HORIZONTAL, false));
        castRecycler.setAdapter(new CastAdapter(mActivity, credits));
    }

    private void bindActions() {
        play.setOnClickListener(v -> {
            if (continueEpisode == null) return;
            startEpisode(continueEpisode);
        });

        addToList.setOnClickListener(v -> {
            boolean add = tvShowDetails.getAddToList() != 1;
            new Thread(() -> {
                if (add) {
                    DatabaseClient.getInstance(mActivity).getAppDatabase()
                            .tvShowDao().updateAddToList(tvShowId);
                } else {
                    DatabaseClient.getInstance(mActivity).getAppDatabase()
                            .tvShowDao().updateRemoveFromList(tvShowId);
                }
                tvShowDetails.setAddToList(add ? 1 : 0);
            }, "MiracleShowWatchlist").start();
            Toast.makeText(mActivity,
                    add ? "Added To List" : "Removed From List", Toast.LENGTH_SHORT).show();
        });

        changeTMDB.setOnClickListener(v -> {
            ChangeTMDBFragment fragment = new ChangeTMDBFragment(tvShowDetails);
            mActivity.getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .add(R.id.container, fragment)
                    .addToBackStack(null)
                    .commit();
        });
    }

    private void startEpisode(Episode episode) {
        new Thread(() -> {
            Episode best = DatabaseClient.getInstance(mActivity).getAppDatabase()
                    .episodeDao().byEpisodeIdLargest(episode.getId());
            if (best == null) best = episode;
            final Episode source = best;

            List<Episode> sources = MediaSourceDeduplicator.deduplicateEpisodes(
                    DatabaseClient.getInstance(mActivity).getAppDatabase()
                            .episodeDao().byEpisodeId(episode.getId()));
            Episode next = DatabaseClient.getInstance(mActivity).getAppDatabase()
                    .episodeDao().getFollowingEpisode(
                            episode.getShow_id(), episode.getSeason_number(), episode.getEpisode_number());

            mActivity.runOnUiThread(() -> {
                if (source.getUrlString() == null || source.getUrlString().trim().isEmpty()) {
                    Toast.makeText(mActivity, "Episode source is unavailable", Toast.LENGTH_SHORT).show();
                    return;
                }

                SharedPreferences prefs =
                        mActivity.getSharedPreferences("Settings", Context.MODE_PRIVATE);
                if (prefs.getBoolean("EXTERNAL_SETTING", false)) {
                    Intent external = new Intent(Intent.ACTION_VIEW, Uri.parse(source.getUrlString()));
                    external.setDataAndType(Uri.parse(source.getUrlString()), "video/*");
                    external.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(external);
                    return;
                }

                Intent intent = new Intent(mActivity, PlayerActivity.class);
                intent.putExtra("url", source.getUrlString());
                intent.putExtra(PlayerActivity.EXTRA_RESUME_KEY, episodeResumeKey(episode));
                intent.putExtra(PlayerActivity.EXTRA_MEDIA_GROUP_KEY,
                        "show:" + episode.getShow_id());

                if (sources != null && sources.size() > 1) {
                    ArrayList<String> urls = new ArrayList<>();
                    ArrayList<String> labels = new ArrayList<>();
                    for (Episode item : sources) {
                        if (item == null || item.getUrlString() == null
                                || item.getUrlString().trim().isEmpty()) continue;
                        urls.add(item.getUrlString());
                        String quality = MovieQualityExtractor.extractQualtiy(item.getFileName());
                        labels.add((quality == null ? "Source " + urls.size() : quality)
                                + " • GDI-JS");
                    }
                    if (urls.size() > 1) {
                        intent.putExtra(PlayerActivity.EXTRA_QUALITY_URLS,
                                urls.toArray(new String[0]));
                        intent.putExtra(PlayerActivity.EXTRA_QUALITY_LABELS,
                                labels.toArray(new String[0]));
                    }
                }

                if (next != null && next.getUrlString() != null
                        && !next.getUrlString().trim().isEmpty()) {
                    intent.putExtra(PlayerActivity.EXTRA_NEXT_URL, next.getUrlString());
                    intent.putExtra(PlayerActivity.EXTRA_NEXT_RESUME_KEY,
                            episodeResumeKey(next));
                    intent.putExtra(PlayerActivity.EXTRA_NEXT_TITLE,
                            next.getName() == null ? "Next Episode" : next.getName());
                }
                startActivity(intent);
            });
        }, "MiracleShowPlay").start();
    }

    private String episodeResumeKey(Episode episode) {
        return episode == null ? "episode:unknown" : "episode:" + episode.getId();
    }

    private String formatResumeTime(long ms) {
        long seconds = Math.max(0, ms / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        return hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.US, "%02d:%02d", minutes, secs);
    }
}
