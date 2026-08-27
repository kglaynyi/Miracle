package com.miracle.kglaynyi.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.miracle.kglaynyi.Constants;
import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.model.TVShowInfo.TVShowSeasonDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaAdapterHolder> {
    private final Context context;
    private final List<MyMedia> mediaList = new ArrayList<>();
    private final OnItemClickListener listener;

    public MediaAdapter(Context context, List<MyMedia> mediaList, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
        if (mediaList != null) this.mediaList.addAll(mediaList);
        setHasStableIds(true);
    }

    public void submitList(List<? extends MyMedia> newItems) {
        final List<MyMedia> next = new ArrayList<>();
        if (newItems != null) next.addAll(newItems);
        final List<MyMedia> old = new ArrayList<>(mediaList);

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return next.size(); }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return stableId(old.get(oldItemPosition)) == stableId(next.get(newItemPosition));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                MyMedia a = old.get(oldItemPosition);
                MyMedia b = next.get(newItemPosition);
                return Objects.equals(displayName(a), displayName(b))
                        && Objects.equals(posterPath(a), posterPath(b))
                        && Objects.equals(yearValue(a), yearValue(b));
            }
        });

        mediaList.clear();
        mediaList.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return stableId(mediaList.get(position));
    }

    @NonNull
    @Override
    public MediaAdapterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MediaAdapterHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.media_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MediaAdapterHolder holder, int position) {
        holder.name.setText("");
        holder.movieYear.setText("");
        holder.movieYear.setVisibility(View.GONE);
        Glide.with(context).clear(holder.poster);
        holder.poster.setImageResource(R.drawable.dummyposter);

        MyMedia media = mediaList.get(position);
        holder.name.setText(displayName(media));
        loadPoster(holder.poster, posterPath(media));

        String year = yearValue(media);
        if (year != null && !year.trim().isEmpty()) {
            holder.movieYear.setVisibility(View.VISIBLE);
            int dash = year.indexOf('-');
            holder.movieYear.setText(dash > 0 ? year.substring(0, dash) : year);
        }
    }

    private String displayName(MyMedia media) {
        if (media instanceof Movie) {
            Movie movie = (Movie) media;
            String name = movie.getTitle();
            if (name == null || name.trim().isEmpty()) name = movie.getFileName();
            return name == null ? "Unknown" : name;
        }
        if (media instanceof TVShow) {
            TVShow show = (TVShow) media;
            return show.getName() == null ? "Unknown Show" : show.getName();
        }
        if (media instanceof TVShowSeasonDetails) {
            TVShowSeasonDetails season = (TVShowSeasonDetails) media;
            return season.getName() == null ? "Season" : season.getName();
        }
        return "Unknown";
    }

    private String posterPath(MyMedia media) {
        if (media instanceof Movie) return ((Movie) media).getPoster_path();
        if (media instanceof TVShow) return ((TVShow) media).getPoster_path();
        if (media instanceof TVShowSeasonDetails) return ((TVShowSeasonDetails) media).getPoster_path();
        return null;
    }

    private String yearValue(MyMedia media) {
        if (media instanceof Movie) return ((Movie) media).getRelease_date();
        if (media instanceof TVShow) return ((TVShow) media).getFirst_air_date();
        return null;
    }

    private long stableId(MyMedia media) {
        if (media instanceof Movie) {
            Movie movie = (Movie) media;
            String gd = movie.getGd_id();
            if (gd != null && !gd.isEmpty()) return ("movie:" + gd).hashCode();
            if (movie.getId() != 0) return 0x100000000L + movie.getId();
            String file = movie.getFileName();
            return ("movie-file:" + (file == null ? movie.getFileidForDB() : file)).hashCode();
        }
        if (media instanceof TVShow) {
            return 0x200000000L + ((TVShow) media).getId();
        }
        if (media instanceof TVShowSeasonDetails) {
            return 0x300000000L + ((TVShowSeasonDetails) media).getId();
        }
        return System.identityHashCode(media);
    }

    private void loadPoster(ImageView view, String path) {
        if (path == null || path.trim().isEmpty()) return;
        Glide.with(context)
                .load(Constants.TMDB_IMAGE_BASE_URL + path)
                .placeholder(new ColorDrawable(Color.BLACK))
                .error(R.drawable.dummyposter)
                .apply(RequestOptions.bitmapTransform(new RoundedCorners(14)))
                .into(view);
    }

    @Override
    public int getItemCount() {
        return mediaList.size();
    }

    public class MediaAdapterHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final ImageView poster;
        final TextView movieYear;

        MediaAdapterHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.nameInMediaItem);
            poster = itemView.findViewById(R.id.posterInMediaItem);
            movieYear = itemView.findViewById(R.id.yearInMediaItem);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (listener == null || position == RecyclerView.NO_POSITION) return;
                v.animate().cancel();
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100)
                                .withEndAction(() -> {
                                    int current = getBindingAdapterPosition();
                                    if (current != RecyclerView.NO_POSITION) listener.onClick(v, current);
                                }).start())
                        .start();
            });
        }
    }

    public interface OnItemClickListener {
        void onClick(View view, int position);
    }
}
