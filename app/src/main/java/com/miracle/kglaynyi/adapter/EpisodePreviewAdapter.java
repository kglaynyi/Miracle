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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.miracle.kglaynyi.Constants;
import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;
import com.miracle.kglaynyi.player.PlayerActivity;
import com.miracle.kglaynyi.utils.MediaSourceDeduplicator;
import com.miracle.kglaynyi.utils.MovieQualityExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EpisodePreviewAdapter extends RecyclerView.Adapter<EpisodePreviewAdapter.Holder> {

    private final Context context;
    private final List<Episode> episodes;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public EpisodePreviewAdapter(Context context, List<Episode> episodes) {
        this.context = context;
        this.episodes = episodes;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        Episode item = episodes.get(position);
        return item.getId() > 0 ? item.getId() : item.getIdForDB();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.episode_preview_item, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Episode episode = episodes.get(position);
        String title = episode.getEpisode_number() + ". "
                + (episode.getName() == null || episode.getName().trim().isEmpty()
                ? "Episode " + episode.getEpisode_number() : episode.getName());
        holder.title.setText(title);
        holder.meta.setText(String.format(Locale.US, "S%02dE%02d",
                episode.getSeason_number(), episode.getEpisode_number()));
        holder.still.setImageDrawable(new ColorDrawable(Color.DKGRAY));
        if (episode.getStill_path() != null && !episode.getStill_path().trim().isEmpty()) {
            Glide.with(context)
                    .load(Constants.TMDB_IMAGE_BASE_URL + episode.getStill_path())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(new ColorDrawable(Color.DKGRAY))
                    .into(holder.still);
        }
        holder.itemView.setOnClickListener(v -> playEpisode(episode, position));
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
            Episode next = position + 1 < episodes.size() ? episodes.get(position + 1)
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
                boolean external = prefs.getBoolean("EXTERNAL_SETTING", false);
                if (external) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(source.getUrlString()));
                    intent.setDataAndType(Uri.parse(source.getUrlString()), "video/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(intent);
                    return;
                }

                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("url", source.getUrlString());
                intent.putExtra(PlayerActivity.EXTRA_RESUME_KEY, "episode:" + episode.getId());
                intent.putExtra(PlayerActivity.EXTRA_MEDIA_GROUP_KEY, "show:" + episode.getShow_id());

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

                if (next != null && next.getUrlString() != null) {
                    intent.putExtra(PlayerActivity.EXTRA_NEXT_URL, next.getUrlString());
                    intent.putExtra(PlayerActivity.EXTRA_NEXT_RESUME_KEY,
                            "episode:" + next.getId());
                    intent.putExtra(PlayerActivity.EXTRA_NEXT_TITLE,
                            next.getName() == null ? "Next Episode" : next.getName());
                }
                context.startActivity(intent);
                new Thread(() -> DatabaseClient.getInstance(context).getAppDatabase()
                        .episodeDao().updatePlayed(episode.getId())).start();
            });
        }, "MiracleEpisodePreviewPlay").start();
    }

    @Override public int getItemCount() {
        return episodes == null ? 0 : episodes.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView still;
        final TextView title;
        final TextView meta;
        Holder(View itemView) {
            super(itemView);
            still = itemView.findViewById(R.id.episodePreviewStill);
            title = itemView.findViewById(R.id.episodePreviewTitle);
            meta = itemView.findViewById(R.id.episodePreviewMeta);
        }
    }
}
