package com.miracle.kglaynyi.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.miracle.kglaynyi.Constants;
import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;
import com.miracle.kglaynyi.player.PlayerActivity;
import com.miracle.kglaynyi.utils.MediaSourceDeduplicator;
import com.miracle.kglaynyi.utils.MovieQualityExtractor;
import com.miracle.kglaynyi.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.EpisodeAdapterHolder> {

    private final Context context;
    private final List<Episode> episodeList;
    private final OnItemClickListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public EpisodeAdapter(Context context, List<Episode> episodeList, OnItemClickListener listener) {
        this.context = context;
        this.episodeList = episodeList;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        Episode episode = episodeList.get(position);
        return episode.getId() > 0 ? episode.getId() : episode.getIdForDB();
    }

    @NonNull @Override
    public EpisodeAdapterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new EpisodeAdapterHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.episode_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeAdapterHolder holder, int position) {
        Episode episode = episodeList.get(position);
        holder.episodeNumber.setText(String.format("E%02d", episode.getEpisode_number()));
        holder.seasonNumber.setText(String.format("S%02d", episode.getSeason_number()));
        holder.episodeName.setText(episode.getName() == null ? "" : episode.getName());
        holder.overview.setText(episode.getOverview() == null ? "" : episode.getOverview());

        Glide.with(context).clear(holder.episodeStill);
        holder.episodeStill.setImageDrawable(new ColorDrawable(Color.BLACK));
        if (episode.getStill_path() != null && !episode.getStill_path().isEmpty()) {
            Glide.with(context)
                    .load(Constants.TMDB_IMAGE_BASE_URL + episode.getStill_path())
                    .placeholder(new ColorDrawable(Color.BLACK))
                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(14)))
                    .into(holder.episodeStill);
        }

        if (episode.getRuntime() > 0) {
            holder.runtime.setVisibility(View.VISIBLE);
            holder.runtime.setText(StringUtils.runtimeIntegerToString(episode.getRuntime()));
        } else {
            holder.runtime.setVisibility(View.GONE);
        }

        holder.play.setOnClickListener(v -> playEpisode(episode, position));
    }

    private void playEpisode(Episode episode, int position) {
        new Thread(() -> {
            Episode best = DatabaseClient.getInstance(context).getAppDatabase()
                    .episodeDao().byEpisodeIdLargest(episode.getId());
            if (best == null) best = episode;
            final Episode source = best;

            List<Episode> sources = MediaSourceDeduplicator.deduplicateEpisodes(
                    DatabaseClient.getInstance(context).getAppDatabase()
                            .episodeDao().byEpisodeId(episode.getId()));
            Episode next = position + 1 < episodeList.size()
                    ? episodeList.get(position + 1)
                    : DatabaseClient.getInstance(context).getAppDatabase().episodeDao()
                    .getFollowingEpisode(episode.getShow_id(),
                            episode.getSeason_number(), episode.getEpisode_number());

            mainHandler.post(() -> {
                if (source.getUrlString() == null || source.getUrlString().trim().isEmpty()) {
                    Toast.makeText(context, "Episode source is unavailable", Toast.LENGTH_SHORT).show();
                    return;
                }

                SharedPreferences prefs =
                        context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
                if (prefs.getBoolean("EXTERNAL_SETTING", false)) {
                    Intent external = new Intent(Intent.ACTION_VIEW, Uri.parse(source.getUrlString()));
                    external.setDataAndType(Uri.parse(source.getUrlString()), "video/*");
                    external.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(external);
                    return;
                }

                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("url", source.getUrlString());
                intent.putExtra(PlayerActivity.EXTRA_RESUME_KEY, "episode:" + episode.getId());
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
                            "episode:" + next.getId());
                    intent.putExtra(PlayerActivity.EXTRA_NEXT_TITLE,
                            next.getName() == null ? "Next Episode" : next.getName());
                }
                context.startActivity(intent);
            });
        }, "MiracleEpisodePlay").start();
    }

    @Override public int getItemCount() {
        return episodeList == null ? 0 : episodeList.size();
    }

    public class EpisodeAdapterHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView episodeName;
        final ImageView episodeStill;
        final TextView seasonNumber;
        final TextView episodeNumber;
        final TextView runtime;
        final TextView overview;
        final Button play;

        EpisodeAdapterHolder(@NonNull View itemView) {
            super(itemView);
            episodeName = itemView.findViewById(R.id.episodeNameInItem);
            episodeStill = itemView.findViewById(R.id.episodeStill);
            seasonNumber = itemView.findViewById(R.id.seasonNumberInItem);
            episodeNumber = itemView.findViewById(R.id.episodeNumberInItem);
            runtime = itemView.findViewById(R.id.RuntimeInItem);
            overview = itemView.findViewById(R.id.overviewDescInItem);
            play = itemView.findViewById(R.id.playInEpisodeItem);
            itemView.setOnClickListener(this);
        }

        @Override public void onClick(View v) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION || listener == null) return;
            listener.onClick(v, position);
        }
    }

    public interface OnItemClickListener {
        void onClick(View view, int position);
    }
}
