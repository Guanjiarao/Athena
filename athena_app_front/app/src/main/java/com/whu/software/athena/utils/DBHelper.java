package com.whu.software.athena.utils;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {
    // 数据库名称
    private static final String DB_NAME = "com.whu.software.athena.db";
    // 数据库版本（v2：新增 user_id 字段）
    private static final int DB_VERSION = 2;

    // 表名
    public static final String TABLE_USER = "user_info";

    // 列名
    public static final String COLUMN_ID           = "_id";
    public static final String COLUMN_LOGIN_STATUS  = "login_status"; // 0/1
    public static final String COLUMN_PHONE         = "phone";        // 11位手机号
    public static final String COLUMN_TOKEN         = "token";        // JWT token
    public static final String COLUMN_USER_ID       = "user_id";      // 后端返回的用户ID

    // 创建表的SQL语句
    private static final String CREATE_TABLE_USER =
            "CREATE TABLE " + TABLE_USER + " ("
                    + COLUMN_ID           + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_LOGIN_STATUS + " INTEGER NOT NULL, "
                    + COLUMN_PHONE        + " TEXT(11) NOT NULL, "
                    + COLUMN_TOKEN        + " TEXT NOT NULL, "
                    + COLUMN_USER_ID      + " TEXT DEFAULT '')";

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_USER);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // 仅添加新列，保留已有登录状态和 token，不丢数据
            db.execSQL("ALTER TABLE " + TABLE_USER
                    + " ADD COLUMN " + COLUMN_USER_ID + " TEXT DEFAULT ''");
        }
    }
}
