package com.miracle.kglaynyi.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
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
import com.miracle.kglaynyi.model.TVShowInfo.TVShowSeasonDetails;

import java.util.List;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaAdapterHolder> {
    private final Context context;
    private final List<MyMedia> mediaList;
    private final OnItemClickListener listener;

    public MediaAdapter(Context context, List<MyMedia> mediaList, OnItemClickListener listener) {
        this.context = context;
        this.mediaList = mediaList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MediaAdapterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MediaAdapterHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.media_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MediaAdapterHolder holder, int position) {
        holder.name.setText("");
        holder.movieYear.setText("");
        holder.movieYear.setVisibility(View.GONE);
        Glide.with(context).clear(holder.poster);
        holder.poster.setImageResource(R.drawable.dummyposter);

        MyMedia media = mediaList.get(position);
        if (media instanceof Movie) {
            Movie movie = (Movie) media;
            String name = movie.getTitle();
            if (name == null || name.trim().isEmpty()) name = movie.getFileName();
            holder.name.setText(name == null ? "Unknown" : name);
            loadPoster(holder.poster, movie.getPoster_path());
            String year = movie.getRelease_date();
            if (year != null && !year.trim().isEmpty()) {
                holder.movieYear.setVisibility(View.VISIBLE);
                int dash = year.indexOf('-');
                holder.movieYear.setText(dash > 0 ? year.substring(0, dash) : year);
            }
        } else if (media instanceof TVShow) {
            TVShow show = (TVShow) media;
            holder.name.setText(show.getName() == null ? "Unknown Show" : show.getName());
            loadPoster(holder.poster, show.getPoster_path());
            String year = show.getFirst_air_date();
            if (year != null && !year.isEmpty()) {
                holder.movieYear.setVisibility(View.VISIBLE);
                int dash = year.indexOf('-');
                holder.movieYear.setText(dash > 0 ? year.substring(0, dash) : year);
            }
        } else if (media instanceof TVShowSeasonDetails) {
            TVShowSeasonDetails season = (TVShowSeasonDetails) media;
            holder.name.setText(season.getName() == null ? "Season" : season.getName());
            loadPoster(holder.poster, season.getPoster_path());
        }
        holder.itemView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.pop_in));
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
        return mediaList == null ? 0 : mediaList.size();
    }

    public class MediaAdapterHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView name;
        final ImageView poster;
        final TextView movieYear;

        MediaAdapterHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.nameInMediaItem);
            poster = itemView.findViewById(R.id.posterInMediaItem);
            movieYear = itemView.findViewById(R.id.yearInMediaItem);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getBindingAdapterPosition();
            if (listener != null && position != RecyclerView.NO_POSITION) listener.onClick(v, position);
        }
    }

    public interface OnItemClickListener {
        void onClick(View view, int position);
    }
}
