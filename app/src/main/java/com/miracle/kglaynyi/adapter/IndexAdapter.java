package com.miracle.kglaynyi.adapter;

import static com.miracle.kglaynyi.utils.IndexUtils.deleteIndex;
import static com.miracle.kglaynyi.utils.IndexUtils.disableIndex;
import static com.miracle.kglaynyi.utils.IndexUtils.enableIndex;
import static com.miracle.kglaynyi.utils.IndexUtils.getNoOfMedia;
import static com.miracle.kglaynyi.utils.IndexUtils.refreshIndex;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.model.IndexLink;

import java.util.List;

public class IndexAdapter extends RecyclerView.Adapter<IndexAdapter.IndexViewHolder> {

    private final Context mCtx;
    private final List<IndexLink> indexLinkList;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public IndexAdapter(Context mCtx, List<IndexLink> indexLinkList) {
        this.mCtx = mCtx;
        this.indexLinkList = indexLinkList;
    }

    @Override
    public IndexViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mCtx).inflate(R.layout.index_item, parent, false);
        return new IndexViewHolder(view);
    }

    @Override
    public void onBindViewHolder(IndexViewHolder holder, int position) {
        IndexLink t = indexLinkList.get(position);
        holder.textViewLink.setText(t.getLink());
        holder.indexType.setText(t.getIndexType());
        holder.folderType.setText(t.getFolderType());

        int noOfMedia = getNoOfMedia(holder.itemView.getContext(), t);
        holder.noOfMedia.setText(noOfMedia + " " + t.getFolderType());
        holder.refreshIndex.setEnabled(true);

        holder.refreshIndex.setOnClickListener(view -> {
            Context context = holder.itemView.getContext();
            holder.refreshIndex.setEnabled(false);
            holder.noOfMedia.setText("Connecting…");

            refreshIndex(context, t, progress -> {
                if (progress.finished && !progress.error) {
                    int finalCount = getNoOfMedia(context, t);
                    mainHandler.post(() -> {
                        if (holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                            holder.noOfMedia.setText(finalCount + " " + t.getFolderType());
                            holder.refreshIndex.setEnabled(true);
                        }
                        Toast.makeText(context,
                                "Scan complete: " + finalCount + " " + t.getFolderType(),
                                Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                mainHandler.post(() -> {
                    if (holder.getBindingAdapterPosition() == RecyclerView.NO_POSITION) return;
                    holder.noOfMedia.setText(progress.message);
                    if (progress.error) {
                        holder.refreshIndex.setEnabled(true);
                        Toast.makeText(context, progress.message, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });

        holder.delete.setOnClickListener(view -> {
            if (!deleteIndex(holder.itemView.getContext(), t)) {
                Toast.makeText(view.getContext(), "Deleted", Toast.LENGTH_LONG).show();
            }
        });

        holder.enableIndex.setOnCheckedChangeListener(null);
        holder.enableIndex.setChecked(t.getDisabled() != 1);
        holder.enableIndex.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                disableIndex(holder.itemView.getContext(), t);
            } else {
                enableIndex(holder.itemView.getContext(), t);
            }
        });
    }

    @Override
    public int getItemCount() {
        return indexLinkList == null ? 0 : indexLinkList.size();
    }

    protected class IndexViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        TextView textViewLink, indexType, folderType, noOfMedia;
        ImageButton refreshIndex;
        ImageButton delete;
        SwitchCompat enableIndex;

        public IndexViewHolder(View itemView) {
            super(itemView);
            textViewLink = itemView.findViewById(R.id.textViewLink);
            indexType = itemView.findViewById(R.id.indexTypeInIndexAdapter);
            folderType = itemView.findViewById(R.id.folderTypeInIndexAdapter);
            noOfMedia = itemView.findViewById(R.id.noOfMedia);
            refreshIndex = itemView.findViewById(R.id.refreshButton);
            delete = itemView.findViewById(R.id.deletebutton);
            enableIndex = itemView.findViewById(R.id.enableIndexToggle);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
        }
    }
}
