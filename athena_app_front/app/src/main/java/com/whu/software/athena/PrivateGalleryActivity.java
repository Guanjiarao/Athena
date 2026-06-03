package com.whu.software.athena;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.whu.software.athena.utils.LocalPhotoManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 私密大肚照墙：本地沙盒图片，支持管理态批量删除与全屏预览。
 */
public class PrivateGalleryActivity extends AppCompatActivity {

    private TextView tvManage;
    private LinearLayout llBottomAction;
    private TextView tvDelete;
    private GalleryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_gallery);

        ImageButton btnBack = findViewById(R.id.btn_nav_back);
        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        tvManage = findViewById(R.id.tv_manage);
        llBottomAction = findViewById(R.id.ll_bottom_action);
        tvDelete = findViewById(R.id.tv_delete);

        RecyclerView rv = findViewById(R.id.rv_gallery);
        rv.setLayoutManager(new GridLayoutManager(this, 3));

        List<File> photos = new ArrayList<>(LocalPhotoManager.getInstance().listBellyPhotos(this));
        adapter = new GalleryAdapter(photos, this::showFullScreenPreview);
        rv.setAdapter(adapter);

        tvManage.setOnClickListener(v -> toggleEditMode());
        tvDelete.setOnClickListener(v -> confirmDeleteSelected());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adapter.isEditMode()) {
                    toggleEditMode();
                } else {
                    finish();
                }
            }
        });
    }

    private void toggleEditMode() {
        if (adapter.isEditMode()) {
            adapter.setEditMode(false);
            tvManage.setText("管理");
            llBottomAction.setVisibility(View.GONE);
            adapter.clearSelection();
            adapter.notifyDataSetChanged();
            updateDeleteLabel();
        } else {
            adapter.setEditMode(true);
            tvManage.setText("取消");
            llBottomAction.setVisibility(View.VISIBLE);
            adapter.clearSelection();
            adapter.notifyDataSetChanged();
            updateDeleteLabel();
        }
    }

    private void updateDeleteLabel() {
        int n = adapter.getSelectedCount();
        tvDelete.setText("删除(" + n + ")");
    }

    private void onAdapterSelectionChanged() {
        updateDeleteLabel();
    }

    private void confirmDeleteSelected() {
        List<String> selected = adapter.getSelectedPathsSnapshot();
        if (selected.isEmpty()) {
            return;
        }
        int x = selected.size();
        new AlertDialog.Builder(this)
                .setTitle("删除照片")
                .setMessage("确认删除选中的 " + x + " 张照片吗？此操作无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    LocalPhotoManager.getInstance().deletePhotos(selected);
                    reloadPhotosAndExitEditMode();
                })
                .show();
    }

    private void reloadPhotosAndExitEditMode() {
        List<File> photos = new ArrayList<>(LocalPhotoManager.getInstance().listBellyPhotos(this));
        adapter.setFiles(photos);
        if (adapter.isEditMode()) {
            adapter.setEditMode(false);
            tvManage.setText("管理");
            llBottomAction.setVisibility(View.GONE);
        }
        adapter.clearSelection();
        adapter.notifyDataSetChanged();
        updateDeleteLabel();
    }

    private void showFullScreenPreview(File file) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_photo_preview, null, false);
        dialog.setContentView(content);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        ImageView iv = content.findViewById(R.id.iv_preview_full);
        Glide.with(this).load(file).fitCenter().into(iv);
        content.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.VH> {

        private final List<File> files = new ArrayList<>();
        private final java.util.function.Consumer<File> onPreview;
        private boolean isEditMode;
        private final List<String> selectedPaths = new ArrayList<>();

        GalleryAdapter(List<File> initial, java.util.function.Consumer<File> onPreview) {
            this.files.addAll(initial);
            this.onPreview = onPreview;
        }

        void setFiles(List<File> list) {
            files.clear();
            if (list != null) {
                files.addAll(list);
            }
        }

        boolean isEditMode() {
            return isEditMode;
        }

        void setEditMode(boolean editMode) {
            this.isEditMode = editMode;
        }

        void clearSelection() {
            selectedPaths.clear();
        }

        int getSelectedCount() {
            return selectedPaths.size();
        }

        List<String> getSelectedPathsSnapshot() {
            return new ArrayList<>(selectedPaths);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_private_gallery_photo, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            File f = files.get(position);
            String path = f.getAbsolutePath();

            Glide.with(h.itemView.getContext())
                    .load(f)
                    .centerCrop()
                    .into(h.ivPhoto);

            if (isEditMode) {
                h.ivCheckbox.setVisibility(View.VISIBLE);
                boolean selected = selectedPaths.contains(path);
                h.viewOverlay.setVisibility(selected ? View.VISIBLE : View.GONE);
                h.ivCheckbox.setImageDrawable(ContextCompat.getDrawable(
                        h.itemView.getContext(),
                        selected ? R.drawable.ic_gallery_checkbox_on : R.drawable.ic_gallery_checkbox_off));
            } else {
                h.ivCheckbox.setVisibility(View.GONE);
                h.viewOverlay.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> {
                if (isEditMode) {
                    if (selectedPaths.contains(path)) {
                        selectedPaths.remove(path);
                    } else {
                        selectedPaths.add(path);
                    }
                    notifyItemChanged(position);
                    onAdapterSelectionChanged();
                } else if (onPreview != null) {
                    onPreview.accept(f);
                }
            });
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView ivPhoto;
            final View viewOverlay;
            final ImageView ivCheckbox;

            VH(@NonNull View itemView) {
                super(itemView);
                ivPhoto = itemView.findViewById(R.id.iv_photo);
                viewOverlay = itemView.findViewById(R.id.view_select_overlay);
                ivCheckbox = itemView.findViewById(R.id.iv_checkbox);
            }
        }
    }
}
