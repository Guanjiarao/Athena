package com.whu.software.athena.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章缓存数据库操作工具类（单例模式 + 安全的数据库操作）
 */
public class BlogCacheDBHelper extends SQLiteOpenHelper {
    private static final String TAG = "BlogCacheDBHelper";
    private static final String DB_NAME = "BlogCache.db";
    private static final int DB_VERSION = 4;
    private static final String TABLE_NAME = "blog_cache";
    
    private static BlogCacheDBHelper instance;
    private SQLiteDatabase database;

    private BlogCacheDBHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized BlogCacheDBHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (BlogCacheDBHelper.class) {
                if (instance == null) {
                    // ✨ 这里必须是 getApplicationContext()
                    instance = new BlogCacheDBHelper(context.getApplicationContext()); 
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableSql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_name TEXT NOT NULL," +
                "blog_id TEXT NOT NULL UNIQUE," +
                "title TEXT NOT NULL," +
                "image_url TEXT NOT NULL," +
                "image_id TEXT NOT NULL UNIQUE," +
                "like_number INTEGER DEFAULT 0," +
                "type INTEGER DEFAULT 1," + // 1=图文，2=视频
                "video_url TEXT," + // 视频链接（type=2 时有值）
                "cache_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "is_loaded INTEGER DEFAULT 0" + // 0=未加载 1=已加载（核心标记位）
                ");";

        String createBlogIndexSql = "CREATE INDEX IF NOT EXISTS idx_blog_id ON " + TABLE_NAME + "(blog_id);";
        String createImageIndexSql = "CREATE INDEX IF NOT EXISTS idx_image_id ON " + TABLE_NAME + "(image_id);";
        String createLoadedIndexSql = "CREATE INDEX IF NOT EXISTS idx_is_loaded ON " + TABLE_NAME + "(is_loaded);";

        db.execSQL(createTableSql);
        db.execSQL(createBlogIndexSql);
        db.execSQL(createImageIndexSql);
        db.execSQL(createLoadedIndexSql);

        Log.d(TAG, "文章缓存表创建成功");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN is_loaded INTEGER DEFAULT 0;");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_is_loaded ON " + TABLE_NAME + "(is_loaded);");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN type INTEGER DEFAULT 1;");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN video_url TEXT;");
        }
        Log.d(TAG, "数据库升级至版本" + newVersion);
    }

    /**
     * 获取可写数据库（线程安全）
     */
    private synchronized SQLiteDatabase getDatabase() {
        if (database == null || !database.isOpen()) {
            database = getWritableDatabase();
        }
        return database;
    }

    /**
     * 批量添加博客数据（线程安全，不关闭数据库）
     */
    public synchronized boolean batchAddBlogs(List<BlogCacheBean> blogList) {
        if (blogList == null || blogList.isEmpty()) {
            Log.w(TAG, "批量添加数据为空，无需操作");
            return true;
        }

        SQLiteDatabase db = null;
        try {
            db = getDatabase();
            if (db == null || !db.isOpen()) {
                Log.e(TAG, "数据库未打开，无法执行批量添加");
                return false;
            }

            db.beginTransaction();
            try {
                String insertSql = "INSERT OR REPLACE INTO " + TABLE_NAME +
                        " (user_name, blog_id, title, image_url, image_id, like_number, type, video_url, is_loaded) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";
                SQLiteStatement statement = db.compileStatement(insertSql);

                for (BlogCacheBean blog : blogList) {
                    statement.bindString(1, blog.getUserName());
                    statement.bindString(2, blog.getBlogId());
                    statement.bindString(3, blog.getTitle());
                    statement.bindString(4, blog.getImageUrl());
                    statement.bindString(5, blog.getImageId());
                    statement.bindLong(6, blog.getLikeNumber());
                    statement.bindLong(7, blog.getType());
                    statement.bindString(8, blog.getVideoUrl() != null ? blog.getVideoUrl() : "");
                    statement.executeInsert();
                    statement.clearBindings();
                }

                db.setTransactionSuccessful();
                Log.d(TAG, "批量添加" + blogList.size() + "条博客缓存数据成功");
                return true;
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "批量添加博客缓存失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取未加载的博客（分页，按缓存时间升序，确保顺序加载）
     */
    public synchronized List<BlogCacheBean> getUnloadedBlogs(int limit) {
        List<BlogCacheBean> blogList = new ArrayList<>();
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getDatabase();
            if (db == null || !db.isOpen()) {
                Log.e(TAG, "数据库未打开，无法获取未加载博客");
                return blogList;
            }

            cursor = db.query(
                    TABLE_NAME,
                    new String[]{"user_name", "blog_id", "title", "image_url", "image_id", "like_number", "type", "video_url"},
                    "is_loaded = ?",
                    new String[]{"0"},
                    null, null,
                    "cache_time ASC",
                    String.valueOf(limit)
            );

            while (cursor.moveToNext()) {
                BlogCacheBean bean = new BlogCacheBean(
                        cursor.getString(cursor.getColumnIndex("user_name")),
                        cursor.getString(cursor.getColumnIndex("blog_id")),
                        cursor.getString(cursor.getColumnIndex("title")),
                        cursor.getString(cursor.getColumnIndex("image_url")),
                        cursor.getString(cursor.getColumnIndex("image_id")),
                        cursor.getInt(cursor.getColumnIndex("like_number"))
                );
                bean.setType(cursor.getInt(cursor.getColumnIndex("type")));
                bean.setVideoUrl(cursor.getString(cursor.getColumnIndex("video_url")));
                blogList.add(bean);
            }

            markBlogsAsLoaded(blogList);
            return blogList;
        } catch (Exception e) {
            Log.e(TAG, "获取未加载博客失败: " + e.getMessage(), e);
            return blogList;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * 标记博客为已加载
     */
    private synchronized boolean markBlogsAsLoaded(List<BlogCacheBean> blogList) {
        if (blogList.isEmpty()) return true;

        SQLiteDatabase db = null;
        try {
            db = getDatabase();
            if (db == null || !db.isOpen()) {
                Log.e(TAG, "数据库未打开，无法标记博客");
                return false;
            }

            db.beginTransaction();
            try {
                String updateSql = "UPDATE " + TABLE_NAME + " SET is_loaded = 1 WHERE blog_id = ?";
                SQLiteStatement statement = db.compileStatement(updateSql);

                for (BlogCacheBean blog : blogList) {
                    statement.bindString(1, blog.getBlogId());
                    statement.executeUpdateDelete();
                    statement.clearBindings();
                }

                db.setTransactionSuccessful();
                return true;
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "标记博客为已加载失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查是否还有未加载的博客
     */
    public synchronized boolean hasUnloadedBlogs() {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getDatabase();
            if (db == null || !db.isOpen()) {
                Log.e(TAG, "数据库未打开，无法检查未加载博客");
                return false;
            }

            cursor = db.query(
                    TABLE_NAME,
                    new String[]{"COUNT(*) as count"},
                    "is_loaded = ?",
                    new String[]{"0"},
                    null, null, null
            );
            if (cursor.moveToFirst()) {
                int count = cursor.getInt(cursor.getColumnIndex("count"));
                return count > 0;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "检查未加载博客失败: " + e.getMessage(), e);
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * 清空所有博客缓存（退出应用时调用）
     */
    public synchronized boolean clearAllBlogCache() {
        try {
            SQLiteDatabase db = getDatabase();
            if (db == null || !db.isOpen()) {
                Log.e(TAG, "数据库未打开，无法清空缓存");
                return false;
            }

            db.execSQL("DELETE FROM " + TABLE_NAME);
            Log.d(TAG, "所有博客缓存数据已清空");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "清空缓存失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 重置缓存博客的加载状态，便于刷新或离线时重新从第一页读取缓存。
     */
    public synchronized boolean resetLoadedState() {
        try {
            SQLiteDatabase db = getDatabase();
            if (db == null || !db.isOpen()) {
                Log.e(TAG, "数据库未打开，无法重置博客加载状态");
                return false;
            }

            db.execSQL("UPDATE " + TABLE_NAME + " SET is_loaded = 0");
            Log.d(TAG, "已重置所有博客缓存的加载状态");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "重置博客加载状态失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 根据image_id查询图片URL（供图片缓存使用）
     */
    public synchronized String getImageUrlByImageId(String imageId) {
        if (imageId == null || imageId.isEmpty()) {
            return null;
        }

        Cursor cursor = null;
        try {
            SQLiteDatabase db = getDatabase();
            if (db == null || !db.isOpen()) {
                Log.e(TAG, "数据库未打开，无法查询图片URL");
                return null;
            }

            cursor = db.query(
                    TABLE_NAME,
                    new String[]{"image_url"},
                    "image_id = ?",
                    new String[]{imageId},
                    null, null, null
            );
            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex("image_url");
                return cursor.getString(columnIndex);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "查询图片URL失败: " + e.getMessage(), e);
            return null;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * 关闭数据库连接（仅在应用退出时调用）
     */
    public synchronized void closeDatabase() {
        if (database != null && database.isOpen()) {
            database.close();
            database = null;
            Log.d(TAG, "数据库连接已关闭");
        }
    }
}

