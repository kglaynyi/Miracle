package com.miracle.kglaynyi.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.miracle.kglaynyi.Constants;
import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.utils.ResumeUtils;

import java.util.ArrayList;
import java.util.List;

public class ContinueWatchingAdapter extends RecyclerView.Adapter<ContinueWatchingAdapter.Holder> {

    public static class Item {
        public final MyMedia media;
        public final ResumeUtils.Entry resume;
        public final String subtitle;

        public Item(MyMedia media, ResumeUtils.Entry resume, String subtitle) {
            this.media = media;
            this.resume = resume;
            this.subtitle = subtitle;
        }
    }

    public interface OnItemClickListener {
        void onClick(Item item);
    }

    private final Context context;
    private final OnItemClickListener listener;
    private final List<Item> items = new ArrayList<>();

    public ContinueWatchingAdapter(Context context, List<Item> initial,
                                   OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
        if (initial != null) items.addAll(initial);
        setHasStableIds(true);
    }

    public void submitList(List<Item> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) {
        Item item = items.get(position);
        return item.resume == null || item.resume.url == null
                ? position : item.resume.url.hashCode();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.home_resume_item, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Item item = items.get(position);
        String title = "Continue Watching";
        String image = null;

        if (item.media instanceof Movie) {
            Movie movie = (Movie) item.media;
            title = movie.title == null || movie.title.trim().isEmpty() ? movie.fileName : movie.title;
            image = movie.backdrop_path == null || movie.backdrop_path.isEmpty()
                    ? movie.poster_path : movie.backdrop_path;
        } else if (item.media instanceof TVShow) {
            TVShow show = (TVShow) item.media;
            title = show.name == null ? "TV Show" : show.name;
            image = show.backdrop_path == null || show.backdrop_path.isEmpty()
                    ? show.poster_path : show.backdrop_path;
        }

        holder.title.setText(title == null ? "Continue Watching" : title);
        holder.subtitle.setText(item.subtitle == null ? "Resume playback" : item.subtitle);
        holder.progress.setProgress(item.resume == null ? 0 : item.resume.progressPercent());
        holder.backdrop.setImageResource(R.drawable.dummyposter);

        if (image != null && !image.trim().isEmpty()) {
            Glide.with(context)
                    .load(Constants.TMDB_IMAGE_BASE_URL + image)
                    .placeholder(R.drawable.dummyposter)
                    .error(R.drawable.dummyposter)
                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(16)))
                    .into(holder.backdrop);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    class Holder extends RecyclerView.ViewHolder {
        final ImageView backdrop;
        final TextView title;
        final TextView subtitle;
        final ProgressBar progress;

        Holder(@NonNull android.view.View itemView) {
            super(itemView);
            backdrop = itemView.findViewById(R.id.resumeBackdrop);
            title = itemView.findViewById(R.id.resumeTitle);
            subtitle = itemView.findViewById(R.id.resumeSubtitle);
            progress = itemView.findViewById(R.id.resumeProgress);
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onClick(items.get(position));
                }
            });
        }
    }
}
