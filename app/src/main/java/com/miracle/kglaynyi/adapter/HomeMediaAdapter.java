package com.miracle.kglaynyi.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeMediaAdapter extends RecyclerView.Adapter<HomeMediaAdapter.Holder> {

    public interface OnItemClickListener {
        void onClick(int position);
    }

    private final Context context;
    private final OnItemClickListener listener;
    private final List<MyMedia> items = new ArrayList<>();

    public HomeMediaAdapter(Context context, List<? extends MyMedia> initial,
                            OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
        if (initial != null) items.addAll(initial);
        setHasStableIds(true);
    }

    public void submitList(List<? extends MyMedia> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) {
        MyMedia media = items.get(position);
        if (media instanceof Movie) {
            Movie movie = (Movie) media;
            if (movie.gd_id != null && !movie.gd_id.isEmpty()) return ("m:" + movie.gd_id).hashCode();
            return 0x100000000L + movie.fileidForDB;
        }
        if (media instanceof TVShow) return 0x200000000L + ((TVShow) media).id;
        return position;
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.home_media_item, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        MyMedia media = items.get(position);
        String title = "Unknown";
        String subtitle = "";
        String poster = null;
        double rating = 0;

        if (media instanceof Movie) {
            Movie movie = (Movie) media;
            title = movie.title == null || movie.title.trim().isEmpty() ? movie.fileName : movie.title;
            subtitle = year(movie.release_date);
            poster = movie.poster_path;
            rating = movie.vote_average;
        } else if (media instanceof TVShow) {
            TVShow show = (TVShow) media;
            title = show.name == null ? "Unknown Show" : show.name;
            subtitle = show.number_of_seasons > 0
                    ? show.number_of_seasons + (show.number_of_seasons == 1 ? " Season" : " Seasons")
                    : year(show.first_air_date);
            poster = show.poster_path;
            rating = show.vote_average;
        }

        holder.title.setText(title == null ? "Unknown" : title);
        holder.subtitle.setText(subtitle);
        holder.rating.setVisibility(rating > 0 ? View.VISIBLE : View.GONE);
        if (rating > 0) holder.rating.setText(String.format(Locale.US, "%.1f", rating));

        holder.poster.setImageResource(R.drawable.dummyposter);
        if (poster != null && !poster.trim().isEmpty()) {
            Glide.with(context)
                    .load(Constants.TMDB_IMAGE_BASE_URL + poster)
                    .placeholder(R.drawable.dummyposter)
                    .error(R.drawable.dummyposter)
                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(16)))
                    .into(holder.poster);
        }
    }

    private String year(String date) {
        if (date == null || date.trim().isEmpty()) return "";
        int dash = date.indexOf('-');
        return dash > 0 ? date.substring(0, dash) : date;
    }

    @Override public int getItemCount() { return items.size(); }

    class Holder extends RecyclerView.ViewHolder {
        final ImageView poster;
        final TextView title;
        final TextView subtitle;
        final TextView rating;

        Holder(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.homePoster);
            title = itemView.findViewById(R.id.homeTitle);
            subtitle = itemView.findViewById(R.id.homeSubtitle);
            rating = itemView.findViewById(R.id.homeRating);
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || listener == null) return;
                v.animate().scaleX(.96f).scaleY(.96f).setDuration(60)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90)
                                .withEndAction(() -> listener.onClick(position)).start()).start();
            });
        }
    }
}
