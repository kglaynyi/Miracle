package com.miracle.kglaynyi.adapter;

import static com.miracle.kglaynyi.utils.IndexUtils.deleteIndex;
import static com.miracle.kglaynyi.utils.IndexUtils.disableIndex;
import static com.miracle.kglaynyi.utils.IndexUtils.enableIndex;
import static com.miracle.kglaynyi.utils.IndexUtils.getNoOfMedia;
import static com.miracle.kglaynyi.utils.IndexUtils.getScanProgress;
import static com.miracle.kglaynyi.utils.IndexUtils.refreshIndex;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.fragment.app.FragmentActivity;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.model.IndexLink;
import com.miracle.kglaynyi.fragments.SelectIndexFoldersFragment;
import com.miracle.kglaynyi.utils.IndexFolderSelectionUtils;
import com.miracle.kglaynyi.utils.ScanCheckpointStore;

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
        holder.indexType.setText("GDI-JS");
        List<String> selectedFolders = IndexFolderSelectionUtils.parse(t.getSelectedFoldersJson());
        if (selectedFolders == null) {
            holder.folderType.setText("Root folder (legacy)");
        } else if (selectedFolders.isEmpty()) {
            holder.folderType.setText("No folders selected");
        } else if (selectedFolders.size() == 1 && "/".equals(selectedFolders.get(0))) {
            holder.folderType.setText("Root folder");
        } else {
            holder.folderType.setText(selectedFolders.size() + " folders");
        }

        // A scan belongs to the index, not to this ViewHolder. Rebind the shared
        // progress state whenever Settings/Manage Indexes is recreated.
        bindProgress(holder, t);

        holder.foldersButton.setOnClickListener(view -> openFolderSelector(holder, t));

        holder.refreshIndex.setOnClickListener(view -> {
            Context context = holder.itemView.getContext();
            List<String> folders = IndexFolderSelectionUtils.parse(t.getSelectedFoldersJson());
            if (folders != null && folders.isEmpty()) {
                Toast.makeText(context, "Select folders before scanning", Toast.LENGTH_SHORT).show();
                openFolderSelector(holder, t);
                return;
            }
            holder.refreshIndex.setEnabled(false);
            holder.noOfMedia.setText("Connecting…");
            if (holder.progressRunnable != null) mainHandler.postDelayed(holder.progressRunnable, 200);

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

    private void openFolderSelector(IndexViewHolder holder, IndexLink indexLink) {
        Context context = holder.itemView.getContext();
        if (!(context instanceof FragmentActivity)) {
            Toast.makeText(context, "Could not open folder selector", Toast.LENGTH_SHORT).show();
            return;
        }
        FragmentActivity activity = (FragmentActivity) context;
        SelectIndexFoldersFragment fragment = new SelectIndexFoldersFragment(indexLink);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.containersettings, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void bindProgress(IndexViewHolder holder, IndexLink indexLink) {
    if (holder.progressRunnable != null) mainHandler.removeCallbacks(holder.progressRunnable);
    holder.boundIndexId = indexLink.getId();
    holder.progressRunnable = new Runnable() {
        @Override public void run() {
            if (holder.getBindingAdapterPosition() == RecyclerView.NO_POSITION || holder.boundIndexId != indexLink.getId()) return;
            com.miracle.kglaynyi.utils.GdiJsIndexClient.Progress progress = getScanProgress(indexLink.getId());
            if (progress != null && !progress.finished) {
                holder.noOfMedia.setText(progress.message);
                holder.refreshIndex.setEnabled(false);
                mainHandler.postDelayed(this, 500);
            } else if (progress != null && progress.error) {
                holder.noOfMedia.setText(progress.message);
                holder.refreshIndex.setEnabled(true);
            } else {
                int count = getNoOfMedia(holder.itemView.getContext(), indexLink);
                boolean resumable = ScanCheckpointStore.hasCheckpoint(
                        holder.itemView.getContext(), indexLink.getId());
                holder.noOfMedia.setText(count + " media"
                        + (resumable ? " • Resume available" : " • Cached"));
                holder.refreshIndex.setEnabled(true);
            }
        }
    };
    holder.progressRunnable.run();
}

    @Override public void onViewRecycled(IndexViewHolder holder) {
    if (holder.progressRunnable != null) mainHandler.removeCallbacks(holder.progressRunnable);
    super.onViewRecycled(holder);
}

    @Override
    public int getItemCount() {
        return indexLinkList == null ? 0 : indexLinkList.size();
    }

    protected class IndexViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        TextView textViewLink, indexType, folderType, noOfMedia;
        ImageButton refreshIndex;
        ImageButton delete;
        Button foldersButton;
        SwitchCompat enableIndex;
        Runnable progressRunnable;
        int boundIndexId;

        public IndexViewHolder(View itemView) {
            super(itemView);
            textViewLink = itemView.findViewById(R.id.textViewLink);
            indexType = itemView.findViewById(R.id.indexTypeInIndexAdapter);
            folderType = itemView.findViewById(R.id.folderTypeInIndexAdapter);
            noOfMedia = itemView.findViewById(R.id.noOfMedia);
            refreshIndex = itemView.findViewById(R.id.refreshButton);
            delete = itemView.findViewById(R.id.deletebutton);
            foldersButton = itemView.findViewById(R.id.foldersButton);
            enableIndex = itemView.findViewById(R.id.enableIndexToggle);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
        }
    }
}
