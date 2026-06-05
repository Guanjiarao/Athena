package com.whu.software.athena.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class SearchHistoryDBHelper extends SQLiteOpenHelper {
    // 数据库名称
    public static final String DB_NAME_KNOWLEDGE = "search_history_knowledge.db";
    public static final String DB_NAME_SQUARE = "search_history_square.db";
    
    // 表名
    private static final String TABLE_NAME = "search_history";
    
    // 列名
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_KEYWORD = "keyword";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    
    // 数据库版本
    private static final int DB_VERSION = 1;
    
    // 最大历史记录数
    private static final int MAX_HISTORY_COUNT = 20;
    
    /**
     * 构造函数
     * @param context 上下文
     * @param dbName 数据库名称
     */
    public SearchHistoryDBHelper(Context context, String dbName) {
        super(context, dbName, null, DB_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建表
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_KEYWORD + " TEXT NOT NULL, " +
                COLUMN_TIMESTAMP + " INTEGER NOT NULL" +
                ");";
        db.execSQL(createTableSQL);
        Log.d("SearchHistoryDB", "Table created successfully");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 升级数据库
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
    
    /**
     * 插入搜索历史
     * @param keyword 搜索关键词
     */
    public void insertSearchHistory(String keyword) {
        if (keyword == null || keyword.isEmpty()) return;
        
        SQLiteDatabase db = getWritableDatabase();
        try {
            // 先删除相同的关键词
            db.delete(TABLE_NAME, COLUMN_KEYWORD + " = ?", new String[]{keyword});
            
            // 插入新的搜索历史
            ContentValues values = new ContentValues();
            values.put(COLUMN_KEYWORD, keyword);
            values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
            db.insert(TABLE_NAME, null, values);
            
            // 检查并删除超出限制的历史记录
            checkAndDeleteOldHistory(db);
        } finally {
            db.close();
        }
    }
    
    /**
     * 获取搜索历史列表
     * @return 搜索历史列表
     */
    public List<String> getSearchHistoryList() {
        List<String> historyList = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        
        try {
            // 按时间戳倒序查询
            cursor = db.query(
                    TABLE_NAME, 
                    new String[]{COLUMN_KEYWORD}, 
                    null, 
                    null, 
                    null, 
                    null, 
                    COLUMN_TIMESTAMP + " DESC"
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String keyword = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_KEYWORD));
                    historyList.add(keyword);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
        
        return historyList;
    }
    
    /**
     * 删除指定的搜索历史
     * @param keyword 搜索关键词
     */
    public void deleteSearchHistory(String keyword) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.delete(TABLE_NAME, COLUMN_KEYWORD + " = ?", new String[]{keyword});
        } finally {
            db.close();
        }
    }
    
    /**
     * 清空所有搜索历史
     */
    public void clearAllSearchHistory() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.delete(TABLE_NAME, null, null);
        } finally {
            db.close();
        }
    }
    
    /**
     * 检查并删除超出限制的历史记录
     * @param db SQLiteDatabase
     */
    private void checkAndDeleteOldHistory(SQLiteDatabase db) {
        Cursor cursor = null;
        try {
            // 获取历史记录总数
            cursor = db.query(
                    TABLE_NAME, 
                    new String[]{"COUNT(*)"}, 
                    null, 
                    null, 
                    null, 
                    null, 
                    null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                if (count > MAX_HISTORY_COUNT) {
                    // 计算需要删除的记录数
                    int deleteCount = count - MAX_HISTORY_COUNT;
                    
                    // 获取最早的记录的ID
                    Cursor oldCursor = db.query(
                            TABLE_NAME, 
                            new String[]{COLUMN_ID}, 
                            null, 
                            null, 
                            null, 
                            null, 
                            COLUMN_TIMESTAMP + " ASC", 
                            String.valueOf(deleteCount)
                    );
                    
                    if (oldCursor != null) {
                        while (oldCursor.moveToNext()) {
                            long id = oldCursor.getLong(oldCursor.getColumnIndexOrThrow(COLUMN_ID));
                            db.delete(TABLE_NAME, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
                        }
                        oldCursor.close();
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    
    /**
     * 根据搜索类型获取对应的数据库名称
     * @param searchType 搜索类型
     * @return 数据库名称
     */
    public static String getDBNameByType(int searchType) {
        return searchType == 0 ? DB_NAME_KNOWLEDGE : DB_NAME_SQUARE;
    }
}