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

public class HomeMediaAdapter extends RecyclerView.Adapter<HomeMediaAdapter.Holder> {

    public interface OnItemClickListener {
        void onClick(View view, int position);
    }

    private final Context context;
    private final List<MyMedia> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public HomeMediaAdapter(Context context, List<? extends MyMedia> media,
                            OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
        if (media != null) items.addAll(media);
        setHasStableIds(true);
    }

    public void submitList(List<? extends MyMedia> media) {
        items.clear();
        if (media != null) items.addAll(media);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        MyMedia media = items.get(position);
        if (media instanceof Movie) {
            Movie movie = (Movie) media;
            if (movie.getGd_id() != null) return ("m:" + movie.getGd_id()).hashCode();
            return 100000L + movie.getId();
        }
        if (media instanceof TVShow) return 200000L + ((TVShow) media).getId();
        return position;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.home_media_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MyMedia media = items.get(position);
        String title = "";
        String subtitle = "";
        String poster = null;

        if (media instanceof Movie) {
            Movie movie = (Movie) media;
            title = movie.getTitle();
            if (title == null || title.trim().isEmpty()) title = movie.getFileName();
            String date = movie.getRelease_date();
            if (date != null && !date.isEmpty()) {
                int dash = date.indexOf('-');
                subtitle = dash > 0 ? date.substring(0, dash) : date;
            }
            poster = movie.getPoster_path();
        } else if (media instanceof TVShow) {
            TVShow show = (TVShow) media;
            title = show.getName();
            String date = show.getFirst_air_date();
            if (date != null && !date.isEmpty()) {
                int dash = date.indexOf('-');
                subtitle = dash > 0 ? date.substring(0, dash) : date;
            }
            poster = show.getPoster_path();
        }

        holder.title.setText(title == null ? "" : title);
        holder.subtitle.setText(subtitle);
        Glide.with(context).clear(holder.poster);
        holder.poster.setImageDrawable(new ColorDrawable(Color.LTGRAY));
        if (poster != null && !poster.isEmpty()) {
            Glide.with(context)
                    .load(Constants.TMDB_IMAGE_BASE_URL + poster)
                    .placeholder(new ColorDrawable(Color.LTGRAY))
                    .error(R.drawable.dummyposter)
                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(12)))
                    .into(holder.poster);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class Holder extends RecyclerView.ViewHolder {
        final ImageView poster;
        final TextView title;
        final TextView subtitle;

        Holder(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.homePoster);
            title = itemView.findViewById(R.id.homeTitle);
            subtitle = itemView.findViewById(R.id.homeSubtitle);
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || listener == null) return;
                v.animate().cancel();
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(60)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                                .setDuration(90).start()).start();
                listener.onClick(v, position);
            });
        }
    }
}
