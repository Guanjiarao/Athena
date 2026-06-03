package com.whu.software.athena.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 大肚照等私密图片：仅读写 {@link Context#getExternalFilesDir(String)} 下目录，与云端物理隔离。
 */
public final class LocalPhotoManager {

    private static final String SUB_FOLDER = "belly_private";
    private static volatile LocalPhotoManager instance;

    private LocalPhotoManager() {}

    public static LocalPhotoManager getInstance() {
        if (instance == null) {
            synchronized (LocalPhotoManager.class) {
                if (instance == null) {
                    instance = new LocalPhotoManager();
                }
            }
        }
        return instance;
    }

    /** 应用私有图片目录（PICTURES/belly_private）。 */
    public File getBellyPhotoDir(Context context) {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (base == null) {
            base = context.getFilesDir();
        }
        File dir = new File(base, SUB_FOLDER);
        if (!dir.exists() && !dir.mkdirs()) {
            return base;
        }
        return dir;
    }

    /** 新建待拍摄 JPEG 文件。 */
    public File createNewPhotoFile(Context context) {
        String name = "BELLY_" + System.currentTimeMillis() + ".jpg";
        return new File(getBellyPhotoDir(context), name);
    }

    /** 供相机写入的 content Uri（FileProvider）。 */
    public Uri getUriForCamera(Context context, File file) {
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
        );
    }

    /** 按修改时间倒序列出 JPEG。 */
    public List<File> listBellyPhotos(Context context) {
        File dir = getBellyPhotoDir(context);
        File[] arr = dir.listFiles((d, name) ->
                name != null && name.toLowerCase().endsWith(".jpg"));
        if (arr == null || arr.length == 0) {
            return Collections.emptyList();
        }
        List<File> list = new ArrayList<>(Arrays.asList(arr));
        list.sort(Comparator.comparingLong(File::lastModified).reversed());
        return list;
    }

    /** 批量物理删除本地文件（仅沙盒路径，不上传）。 */
    public void deletePhotos(List<String> filePaths) {
        if (filePaths == null) {
            return;
        }
        for (String path : filePaths) {
            if (path == null) {
                continue;
            }
            File file = new File(path);
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
}
