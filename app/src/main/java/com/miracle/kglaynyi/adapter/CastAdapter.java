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
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.miracle.kglaynyi.Constants;
import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.model.CreditPerson;

import java.util.List;

public class CastAdapter extends RecyclerView.Adapter<CastAdapter.Holder> {
    private final Context context;
    private final List<CreditPerson> items;

    public CastAdapter(Context context, List<CreditPerson> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.cast_item, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        CreditPerson item = items.get(position);
        holder.name.setText(item.getName());
        holder.role.setText(item.getRole());
        holder.photo.setImageDrawable(new ColorDrawable(Color.DKGRAY));
        if (item.getProfilePath() != null && !item.getProfilePath().trim().isEmpty()) {
            Glide.with(context)
                    .load(Constants.TMDB_IMAGE_BASE_URL + item.getProfilePath())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(new ColorDrawable(Color.DKGRAY))
                    .into(holder.photo);
        }
    }

    @Override public int getItemCount() { return items == null ? 0 : items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView photo;
        final TextView name;
        final TextView role;
        Holder(View itemView) {
            super(itemView);
            photo = itemView.findViewById(R.id.castPhoto);
            name = itemView.findViewById(R.id.castName);
            role = itemView.findViewById(R.id.castRole);
        }
    }
}
