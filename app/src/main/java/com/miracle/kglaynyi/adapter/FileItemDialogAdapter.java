package com.miracle.kglaynyi.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;
import com.miracle.kglaynyi.utils.MovieQualityExtractor;

import java.util.List;

public class FileItemDialogAdapter extends RecyclerView.Adapter<FileItemDialogAdapter.FileItemDialogAdapterViewHolder> {

    List<MyMedia> mediaList;
    private FileItemDialogAdapter.OnItemClickListener listener;

    public FileItemDialogAdapter(List<MyMedia> mediaList , FileItemDialogAdapter.OnItemClickListener listener) {
        this.mediaList = mediaList;
        this.listener= listener;
    }


    @NonNull
    @Override
    public FileItemDialogAdapterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.dialog_filename_item, parent, false);
        return new FileItemDialogAdapterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileItemDialogAdapterViewHolder holder , int position) {
        if (mediaList.get(position) instanceof Movie) {
            if (((Movie)mediaList.get(position)).getUrlString() != null) {
                holder.fileName.setText(((Movie)mediaList.get(position)).getFileName());
            }
            String qualityStr = MovieQualityExtractor.extractQualtiy(((Movie)mediaList.get(position)).getFileName());
            if(qualityStr!=null){
                holder.quality.setVisibility(View.VISIBLE);
                holder.quality.setText(qualityStr);
            }
        }

        if (mediaList.get(position) instanceof Episode) {
            if (((Episode)mediaList.get(position)).getUrlString() != null) {
                holder.fileName.setText(((Episode)mediaList.get(position)).getFileName());
            }
            String qualityStr = MovieQualityExtractor.extractQualtiy(((Episode)mediaList.get(position)).getFileName());
            if(qualityStr!=null){
                holder.quality.setVisibility(View.VISIBLE);
                holder.quality.setText(qualityStr);
            }

    }

    }



    @Override
    public int getItemCount () {
        return mediaList.size();
    }


    public class FileItemDialogAdapterViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        TextView fileName;
        TextView quality;

        public FileItemDialogAdapterViewHolder(@NonNull View itemView) {
            super(itemView);



            fileName = itemView.findViewById(R.id.fileName);
            quality = itemView.findViewById(R.id.quality);
                itemView.setOnClickListener(this);
        }



        @Override
        public void onClick(View v) {listener.onClick(v,getAbsoluteAdapterPosition());}


    }
    public interface OnItemClickListener {
        void onClick(View view , int position);
    }

}

