package com.miracle.kglaynyi.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
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
import com.miracle.kglaynyi.utils.StringUtils;

import java.util.List;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.EpisodeAdapterHolder> {

    private final Context context;
    private final List<Episode> episodeList;
    private final OnItemClickListener listener;

    public EpisodeAdapter(Context context, List<Episode> episodeList, OnItemClickListener listener) {
        this.context = context;
        this.episodeList = episodeList;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        Episode episode = episodeList.get(position);
        String gdId = episode.getGd_id();
        if (gdId != null && !gdId.isEmpty()) return gdId.hashCode();
        return episode.getId();
    }

    @NonNull
    @Override
    public EpisodeAdapterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.episode_item, parent, false);
        return new EpisodeAdapterHolder(view);
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

        if (episode.getRuntime() != 0) {
            holder.runtime.setVisibility(View.VISIBLE);
            holder.runtime.setText(StringUtils.runtimeIntegerToString(episode.getRuntime()));
        } else {
            holder.runtime.setVisibility(View.GONE);
            holder.runtime.setText("");
        }

        holder.play.setOnClickListener(view -> holder.playEpisode(episode));
    }

    @Override
    public int getItemCount() {
        return episodeList == null ? 0 : episodeList.size();
    }

    public class EpisodeAdapterHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView episodeName;
        ImageView episodeStill;
        TextView seasonNumber;
        TextView episodeNumber;
        TextView runtime;
        TextView overview;
        Button play;

        public EpisodeAdapterHolder(@NonNull View itemView) {
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

        @Override
        public void onClick(View v) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION || listener == null) return;

            v.animate().cancel();
            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(65)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(95)
                            .withEndAction(() -> {
                                int current = getBindingAdapterPosition();
                                if (current != RecyclerView.NO_POSITION) {
                                    listener.onClick(v, current);
                                }
                            }).start())
                    .start();
        }

        private void playEpisode(Episode episode) {
            SharedPreferences sharedPreferences = itemView.getContext()
                    .getSharedPreferences("Settings", Context.MODE_PRIVATE);
            boolean savedEXT = sharedPreferences.getBoolean("EXTERNAL_SETTING", false);

            addToLastPlayed(episode.getId());
            Uri uri = Uri.parse(episode.getUrlString());
            if (savedEXT) {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.setDataAndType(uri, "video/*");
                itemView.getContext().startActivity(intent);
            } else {
                Intent in = new Intent(itemView.getContext(), PlayerActivity.class);
                in.putExtra("url", episode.getUrlString());
                itemView.getContext().startActivity(in);
                Toast.makeText(itemView.getContext(), "Play", Toast.LENGTH_SHORT).show();
            }
        }

        private void addToLastPlayed(int id) {
            new Thread(() -> DatabaseClient.getInstance(itemView.getContext())
                    .getAppDatabase().episodeDao().updatePlayed(id)).start();
        }
    }

    public interface OnItemClickListener {
        void onClick(View view, int position);
    }
}
