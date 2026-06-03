package com.whu.software.athena.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class UserDao {
    private final DBHelper dbHelper;
    private SQLiteDatabase db;

    public UserDao(Context context) {
        dbHelper = new DBHelper(context);
    }

    public void open() {
        db = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    /**
     * 插入或更新用户信息（兼容旧调用，不含 userId）
     */
    public long insertOrUpdateUser(int loginStatus, String phone, String token) {
        return insertOrUpdateUser(loginStatus, phone, token, "");
    }

    /**
     * 插入或更新用户信息（含 userId）
     */
    public long insertOrUpdateUser(int loginStatus, String phone, String token, String userId) {
        ContentValues values = new ContentValues();
        values.put(DBHelper.COLUMN_LOGIN_STATUS, loginStatus);
        values.put(DBHelper.COLUMN_PHONE, phone);
        values.put(DBHelper.COLUMN_TOKEN, token);
        values.put(DBHelper.COLUMN_USER_ID, userId != null ? userId : "");

        Cursor cursor = db.query(
                DBHelper.TABLE_USER, null,
                DBHelper.COLUMN_PHONE + " = ?",
                new String[]{phone},
                null, null, null
        );

        long result;
        if (cursor != null && cursor.moveToFirst()) {
            // 若 userId 为空则保留旧值，不覆盖
            if (userId == null || userId.isEmpty()) {
                values.remove(DBHelper.COLUMN_USER_ID);
            }
            result = db.update(
                    DBHelper.TABLE_USER, values,
                    DBHelper.COLUMN_PHONE + " = ?",
                    new String[]{phone}
            );
            cursor.close();
        } else {
            result = db.insert(DBHelper.TABLE_USER, null, values);
        }
        return result;
    }

    /**
     * 根据手机号查询用户信息
     * @return [loginStatus, phone, token, userId]，查询失败返回 null
     */
    public String[] getUserByPhone(String phone) {
        Cursor cursor = db.query(
                DBHelper.TABLE_USER,
                new String[]{
                        DBHelper.COLUMN_LOGIN_STATUS,
                        DBHelper.COLUMN_PHONE,
                        DBHelper.COLUMN_TOKEN,
                        DBHelper.COLUMN_USER_ID
                },
                DBHelper.COLUMN_PHONE + " = ?",
                new String[]{phone},
                null, null, null
        );

        if (cursor != null && cursor.moveToFirst()) {
            String[] userInfo = new String[4];
            userInfo[0] = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_LOGIN_STATUS));
            userInfo[1] = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_PHONE));
            userInfo[2] = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_TOKEN));
            userInfo[3] = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_USER_ID));
            cursor.close();
            return userInfo;
        }
        return null;
    }

    /**
     * 获取当前登录用户
     * @return [loginStatus, phone, token, userId]，查询失败返回 null
     */
    public String[] getCurrentLoginUser() {
        Cursor cursor = db.query(
                DBHelper.TABLE_USER,
                new String[]{
                        DBHelper.COLUMN_LOGIN_STATUS,
                        DBHelper.COLUMN_PHONE,
                        DBHelper.COLUMN_TOKEN,
                        DBHelper.COLUMN_USER_ID
                },
                DBHelper.COLUMN_LOGIN_STATUS + " = 1",
                null, null, null, null
        );

        if (cursor != null && cursor.moveToFirst()) {
            String[] userInfo = new String[4];
            userInfo[0] = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_LOGIN_STATUS));
            userInfo[1] = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_PHONE));
            userInfo[2] = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_TOKEN));
            userInfo[3] = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_USER_ID));
            cursor.close();
            return userInfo;
        }
        return null;
    }

    /**
     * 更新登录状态
     */
    public int updateLoginStatus(String phone, int loginStatus) {
        ContentValues values = new ContentValues();
        values.put(DBHelper.COLUMN_LOGIN_STATUS, loginStatus);
        return db.update(
                DBHelper.TABLE_USER, values,
                DBHelper.COLUMN_PHONE + " = ?",
                new String[]{phone}
        );
    }

    /**
     * 删除用户信息
     */
    public int deleteUser(String phone) {
        return db.delete(
                DBHelper.TABLE_USER,
                DBHelper.COLUMN_PHONE + " = ?",
                new String[]{phone}
        );
    }
}
