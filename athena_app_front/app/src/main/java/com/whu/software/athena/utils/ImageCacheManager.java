package com.whu.software.athena.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 图片缓存管理类（Java 版）
 * 负责根据image_id管理本地图片缓存（查询、下载、存储）
 */
public class ImageCacheManager {
    private static final String TAG = "ImageCacheManager";
    private static ImageCacheManager instance;
    private final File cacheDir;

    // 私有构造函数，单例模式
    private ImageCacheManager(Context context) {
        // 图片缓存目录：应用私有图片目录下的blog_image_cache文件夹
        cacheDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "blog_image_cache");
        // 创建目录（不存在则创建）
        if (!cacheDir.exists()) {
            boolean isCreated = cacheDir.mkdirs();
            Log.d(TAG, "图片缓存目录创建：" + (isCreated ? "成功" : "失败"));
        }
    }

    /**
     * 获取单例实例
     * @param context 上下文（建议传Application Context）
     * @return ImageCacheManager实例
     */
    public static ImageCacheManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ImageCacheManager.class) {
                if (instance == null) {
                    instance = new ImageCacheManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * 根据image_id获取本地缓存图片
     * 本地无则从阿里云下载并缓存，返回Bitmap
     * @param imageId 图片ID
     * @param dbHelper 数据库帮助类（用于查询图片URL）
     * @return 图片Bitmap，失败则返回null
     */
    public Bitmap getImageByImageId(String imageId, BlogCacheDBHelper dbHelper) {
        if (imageId == null || imageId.isEmpty() || dbHelper == null) {
            Log.w(TAG, "参数异常：imageId或dbHelper为空");
            return null;
        }

        // 1. 先查询本地是否有该图片
        File imageFile = new File(cacheDir, imageId + ".jpg");
        if (imageFile.exists()) {
            Log.d(TAG, "本地存在图片缓存：" + imageId);
            return BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        }

        // 2. 本地无则查询数据库获取图片URL
        String imageUrl = dbHelper.getImageUrlByImageId(imageId);
        if (imageUrl == null || imageUrl.isEmpty()) {
            Log.w(TAG, "数据库中未找到imageId=" + imageId + "对应的URL");
            return null;
        }

        // 3. 从阿里云下载图片并缓存到本地
        try {
            Bitmap bitmap = downloadImageFromUrl(imageUrl);
            if (bitmap != null) {
                saveImageToLocal(bitmap, imageFile);
                Log.d(TAG, "图片下载并缓存成功：" + imageId);
                return bitmap;
            } else {
                Log.w(TAG, "图片下载失败：" + imageUrl);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "图片下载/缓存异常：" + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从URL下载图片
     * @param imageUrl 图片URL
     * @return 下载的Bitmap
     * @throws Exception 下载异常
     */
    private Bitmap downloadImageFromUrl(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        // 设置超时时间
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod("GET");

        try {
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                return BitmapFactory.decodeStream(inputStream);
            } else {
                Log.w(TAG, "图片下载响应码异常：" + connection.getResponseCode());
                return null;
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 将Bitmap保存到本地缓存目录
     * @param bitmap 图片Bitmap
     * @param file 保存的文件
     * @throws Exception 保存异常
     */
    private void saveImageToLocal(Bitmap bitmap, File file) throws Exception {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            // 压缩图片（质量80，平衡体积和清晰度）
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
            outputStream.flush();
        }
    }

    /**
     * 清空所有图片缓存
     * @return 是否清空成功
     */
    public boolean clearAllImageCache() {
        try {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        boolean isDeleted = file.delete();
                        Log.d(TAG, "删除图片缓存：" + file.getName() + " - " + (isDeleted ? "成功" : "失败"));
                    }
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "清空图片缓存失败：" + e.getMessage(), e);
            return false;
        }
    }
}