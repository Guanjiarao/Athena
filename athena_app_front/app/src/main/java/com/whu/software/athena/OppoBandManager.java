package com.whu.software.athena;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.heytap.databaseengine.HeytapHealthApi;
import com.heytap.databaseengine.apiv2.HResponse;
import com.heytap.databaseengine.apiv2.auth.AuthResult;
import com.heytap.databaseengine.apiv3.DataReadRequest;
import com.heytap.databaseengine.apiv3.data.DataPoint;
import com.heytap.databaseengine.apiv3.data.DataSet;
import com.heytap.databaseengine.apiv3.data.DataType;
import com.heytap.databaseengine.apiv3.data.Element;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class OppoBandManager {

    private static final String TAG = "OppoBandManager";
    private static volatile OppoBandManager sInstance;

    private OppoBandManager() {}

    public static OppoBandManager getInstance() {
        if (sInstance == null) {
            synchronized (OppoBandManager.class) {
                if (sInstance == null) sInstance = new OppoBandManager();
            }
        }
        return sInstance;
    }

    // ─────────────── 回调接口 ───────────────

    public interface DataCallback {
        void onSuccess(int steps, int calories);
        void onFailure(int code, String msg);
    }

    public interface HeartRateCallback {
        void onSuccess(int avgHeartRate);
        void onFailure(int code, String msg);
    }

    public interface SleepCallback {
        void onSuccess(int totalMinutes);
        void onFailure(int code, String msg);
    }

    public interface SpO2Callback {
        void onSuccess(int spo2);
        void onFailure(int code, String msg);
    }

    public interface StressCallback {
        void onSuccess(int stress);
        void onFailure(int code, String msg);
    }

    // ─────────────── 核心安全取值：按 element name 动态匹配 ───────────────

    /**
     * 绕过 Element 静态常量 indexOf 不匹配的问题。
     * 遍历 DataPoint 自带的 elements 列表，按 name 找到正确的 Element 实例再取值。
     */
    private Object getSafeValueByName(DataPoint dp, String targetName) {
        if (dp == null) return null;
        try {
            DataType dt = dp.getDataType();
            if (dt == null || dt.getElements() == null) return null;
            for (Element element : dt.getElements()) {
                if (targetName.equals(element.getName())) {
                    return dp.getValue(element);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getSafeValueByName('" + targetName + "') error: " + e.getMessage());
        }
        return null;
    }

    private int getIntByName(DataPoint dp, String name) {
        Object val = getSafeValueByName(dp, name);
        String str = val != null ? String.valueOf(val) : null;
        Log.d(TAG, "    getByName('" + name + "') = " + str
                + (val != null ? " (type=" + val.getClass().getSimpleName() + ")" : " (null)"));
        if (str == null) return 0;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e1) {
            try {
                return (int) Double.parseDouble(str);
            } catch (NumberFormatException e2) {
                Log.e(TAG, "解析 '" + name + "' 为 int 失败: " + str);
            }
        }
        return 0;
    }

    private double getDoubleByName(DataPoint dp, String name) {
        Object val = getSafeValueByName(dp, name);
        String str = val != null ? String.valueOf(val) : null;
        Log.d(TAG, "    getByName('" + name + "') = " + str
                + (val != null ? " (type=" + val.getClass().getSimpleName() + ")" : " (null)"));
        if (str == null) return 0;
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            Log.e(TAG, "解析 '" + name + "' 为 double 失败: " + str);
        }
        return 0;
    }

    // ─────────────── 调试 dump ───────────────

    private void dumpDataPoint(String prefix, DataPoint dp) {
        Log.d(TAG, prefix + "toString=" + dp.toString());
        try {
            DataType dt = dp.getDataType();
            if (dt != null && dt.getElements() != null) {
                for (Element el : dt.getElements()) {
                    String name = el.getName();
                    try {
                        Object val = dp.getValue(el);
                        Log.d(TAG, prefix + "  '" + name + "' = " + val
                                + (val != null ? " (" + val.getClass().getSimpleName() + ")" : ""));
                    } catch (Exception e) {
                        Log.w(TAG, prefix + "  '" + name + "' getValue error: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, prefix + "dump error: " + e.getMessage());
        }
    }

    private void dumpAllDataSets(String label, List<DataSet> dataSets) {
        Log.d(TAG, "[" + label + "] ====== BEGIN DUMP ======");
        if (dataSets == null) { Log.d(TAG, "[" + label + "] dataSets is null"); return; }
        Log.d(TAG, "[" + label + "] dataSets.size=" + dataSets.size());
        for (int i = 0; i < dataSets.size(); i++) {
            DataSet ds = dataSets.get(i);
            List<DataPoint> points = ds.getDataPoints();
            Log.d(TAG, "[" + label + "] ds[" + i + "] points=" + (points == null ? "null" : points.size()));
            if (points == null) continue;
            for (int j = 0; j < points.size(); j++) {
                dumpDataPoint("[" + label + "]   dp[" + j + "] ", points.get(j));
            }
        }
        Log.d(TAG, "[" + label + "] ====== END DUMP ======");
    }

    // ─────────────── 时间范围 ───────────────

    private long[] todayRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        long end = System.currentTimeMillis();
        Log.d(TAG, "todayRange: " + start + " ~ " + end
                + " (" + new Date(start) + " ~ " + new Date(end) + ")");
        return new long[]{start, end};
    }

    // ─────────────── SharedPreferences 常量 ───────────────

    static final String PREFS_NAME = "AthenaPrefs";
    static final String KEY_OPPO_AUTHORIZED = "is_oppo_authorized";

    /** 授权失效错误码：OPPO SDK 以 401 或 -2 表示 Token 过期 / 未授权 */
    private static boolean isAuthErrorCode(int code) {
        return code == 401 || code == -2;
    }

    // ─────────────── 1. 授权 ───────────────

    /**
     * 发起 OPPO 授权弹窗。授权成功后持久化 flag，避免重复弹窗。
     *
     * @param activity  当前 Activity（用于弹出授权界面 & 写 SharedPreferences）
     * @param onSuccess 授权 + 校验均通过后的回调（在 SDK 回调线程执行）
     */
    public void requestAuth(Activity activity, Runnable onSuccess) {
        Log.d(TAG, ">>> requestAuth");
        try {
            HeytapHealthApi.getInstance().authorityApi().request(activity, new HResponse<AuthResult>() {
                @Override
                public void onSuccess(AuthResult result) {
                    Log.i(TAG, "Auth success: " + result);
                    // 授权成功后持久化状态，下次进入页面直接跳过授权弹窗
                    SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit().putBoolean(KEY_OPPO_AUTHORIZED, true).apply();
                    Log.i(TAG, "OPPO 授权状态已持久化");
                    validateAuthThen(onSuccess);
                }
                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "Auth failed: code=" + errorCode);
                    // 授权失败时确保本地 flag 为 false，防止残留脏数据
                    SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit().putBoolean(KEY_OPPO_AUTHORIZED, false).apply();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "requestAuth exception", e);
        }
    }

    private void validateAuthThen(Runnable next) {
        Log.d(TAG, ">>> validateAuthThen");
        try {
            HeytapHealthApi.getInstance().authorityApi().valid(new HResponse<List<String>>() {
                @Override
                public void onSuccess(List<String> scopeList) {
                    Log.i(TAG, "Auth scope: " + scopeList);
                    if (next != null) next.run();
                }
                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "Auth valid failed: code=" + errorCode);
                    if (next != null) next.run();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "validateAuth exception", e);
            if (next != null) next.run();
        }
    }

    /**
     * 将 OPPO 授权状态重置为"未授权"。
     * 当数据接口返回授权失效错误码时调用，下次进入页面将重新弹出授权界面。
     */
    public void resetAuthFlag(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_OPPO_AUTHORIZED, false).apply();
        Log.w(TAG, "OPPO 授权状态已重置（Token 过期或授权被撤销）");
    }

    // ─────────────── 2. 活动数据（步数 + 卡路里） ───────────────

    public void getTodayActivityData(DataCallback callback) {
        Log.d(TAG, ">>> getTodayActivityData");
        try {
            long[] range = todayRange();
            DataReadRequest req = new DataReadRequest.Builder()
                    .read(DataType.TYPE_DAILY_ACTIVITY_COUNT)
                    .setTimeRange(range[0], range[1])
                    .build();

            HeytapHealthApi.getInstance().dataApi().read(req, new HResponse<List<DataSet>>() {
                @Override
                public void onSuccess(List<DataSet> dataSets) {
                    dumpAllDataSets("ACTIVITY", dataSets);

                    int steps = 0;
                    double caloriesD = 0;
                    if (dataSets != null) {
                        for (DataSet ds : dataSets) {
                            if (ds.getDataPoints() == null) continue;
                            for (DataPoint dp : ds.getDataPoints()) {
                                steps += getIntByName(dp, "step");
                                caloriesD += getDoubleByName(dp, "calorie");
                            }
                        }
                    }
                    // SDK 返回的 calorie 单位是卡（cal），UI 显示千卡（kcal）
                    int calories = (int) Math.round(caloriesD / 1000.0);
                    Log.i(TAG, "★ 活动: steps=" + steps + ", rawCal=" + caloriesD + ", kcal=" + calories);
                    callback.onSuccess(steps, calories);
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "活动数据失败: code=" + errorCode);
                    callback.onFailure(errorCode, "Error " + errorCode);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "getTodayActivityData exception", e);
            callback.onFailure(-1, e.getMessage());
        }
    }

    // ─────────────── 3. 心率 ───────────────
    // 此处获取的为今日平均心率（TYPE_HEART_RATE_COUNT → "average"）。
    // 如需最新实时心率，需改查 TYPE_HEART_RATE 明细数据并取最后一个 DataPoint。

    public void getTodayHeartRate(HeartRateCallback callback) {
        Log.d(TAG, ">>> getTodayHeartRate");
        try {
            long[] range = todayRange();
            DataReadRequest req = new DataReadRequest.Builder()
                    .read(DataType.TYPE_HEART_RATE_COUNT)
                    .setTimeRange(range[0], range[1])
                    .build();

            HeytapHealthApi.getInstance().dataApi().read(req, new HResponse<List<DataSet>>() {
                @Override
                public void onSuccess(List<DataSet> dataSets) {
                    dumpAllDataSets("HR", dataSets);

                    int avgHr = 0;
                    if (dataSets != null) {
                        for (DataSet ds : dataSets) {
                            if (ds.getDataPoints() == null) continue;
                            for (DataPoint dp : ds.getDataPoints()) {
                                int v = getIntByName(dp, "average");
                                if (v > 0) avgHr = v;
                                if (avgHr == 0) {
                                    v = getIntByName(dp, "avg");
                                    if (v > 0) avgHr = v;
                                }
                            }
                        }
                    }
                    Log.i(TAG, "★ 心率: avgHr=" + avgHr);
                    callback.onSuccess(avgHr);
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "心率失败: code=" + errorCode);
                    callback.onFailure(errorCode, "Error " + errorCode);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "getTodayHeartRate exception", e);
            callback.onFailure(-1, e.getMessage());
        }
    }

    // ─────────────── 4. 睡眠 ───────────────

    public void getTodaySleep(SleepCallback callback) {
        Log.d(TAG, ">>> getTodaySleep");
        try {
            long[] range = todayRange();
            DataReadRequest req = new DataReadRequest.Builder()
                    .read(DataType.TYPE_SLEEP_COUNT)
                    .setTimeRange(range[0], range[1])
                    .build();

            HeytapHealthApi.getInstance().dataApi().read(req, new HResponse<List<DataSet>>() {
                @Override
                public void onSuccess(List<DataSet> dataSets) {
                    dumpAllDataSets("SLEEP", dataSets);

                    int totalMin = 0;
                    if (dataSets != null) {
                        for (DataSet ds : dataSets) {
                            if (ds.getDataPoints() == null) continue;
                            for (DataPoint dp : ds.getDataPoints()) {
                                int v = getIntByName(dp, "total");
                                if (v > 0) totalMin = v;
                                if (totalMin == 0) {
                                    v = getIntByName(dp, "duration");
                                    if (v > 0) totalMin = v;
                                }
                                if (totalMin == 0) {
                                    v = getIntByName(dp, "total_sleep");
                                    if (v > 0) totalMin = v;
                                }
                            }
                        }
                    }
                    Log.i(TAG, "★ 睡眠: totalMin=" + totalMin);
                    callback.onSuccess(totalMin);
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "睡眠失败: code=" + errorCode);
                    callback.onFailure(errorCode, "Error " + errorCode);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "getTodaySleep exception", e);
            callback.onFailure(-1, e.getMessage());
        }
    }

    // ─────────────── 5. 血氧 ───────────────

    public void getTodaySpO2(SpO2Callback callback) {
        Log.d(TAG, ">>> getTodaySpO2");
        try {
            long[] range = todayRange();
            DataReadRequest req = new DataReadRequest.Builder()
                    .read(DataType.TYPE_BLOOD_OXYGEN_COUNT)
                    .setTimeRange(range[0], range[1])
                    .build();

            HeytapHealthApi.getInstance().dataApi().read(req, new HResponse<List<DataSet>>() {
                @Override
                public void onSuccess(List<DataSet> dataSets) {
                    dumpAllDataSets("SPO2", dataSets);

                    int spo2 = 0;
                    if (dataSets != null) {
                        for (DataSet ds : dataSets) {
                            if (ds.getDataPoints() == null) continue;
                            for (DataPoint dp : ds.getDataPoints()) {
                                int v = getIntByName(dp, "average");
                                if (v > 0) spo2 = v;
                                if (spo2 == 0) {
                                    v = getIntByName(dp, "spo2");
                                    if (v > 0) spo2 = v;
                                }
                                if (spo2 == 0) {
                                    v = getIntByName(dp, "avg");
                                    if (v > 0) spo2 = v;
                                }
                            }
                        }
                    }
                    Log.i(TAG, "★ 血氧: spo2=" + spo2);
                    callback.onSuccess(spo2);
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "血氧失败: code=" + errorCode);
                    callback.onFailure(errorCode, "Error " + errorCode);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "getTodaySpO2 exception", e);
            callback.onFailure(-1, e.getMessage());
        }
    }

    // ─────────────── 6. 压力 ───────────────

    public void getTodayStress(StressCallback callback) {
        Log.d(TAG, ">>> getTodayStress");
        try {
            long[] range = todayRange();
            DataReadRequest req = new DataReadRequest.Builder()
                    .read(DataType.TYPE_PRESSURE_COUNT)
                    .setTimeRange(range[0], range[1])
                    .build();

            HeytapHealthApi.getInstance().dataApi().read(req, new HResponse<List<DataSet>>() {
                @Override
                public void onSuccess(List<DataSet> dataSets) {
                    dumpAllDataSets("STRESS", dataSets);

                    int stress = 0;
                    if (dataSets != null) {
                        for (DataSet ds : dataSets) {
                            if (ds.getDataPoints() == null) continue;
                            for (DataPoint dp : ds.getDataPoints()) {
                                int v = getIntByName(dp, "average");
                                if (v > 0) stress = v;
                                if (stress == 0) {
                                    v = getIntByName(dp, "stress");
                                    if (v > 0) stress = v;
                                }
                                if (stress == 0) {
                                    v = getIntByName(dp, "pressure");
                                    if (v > 0) stress = v;
                                }
                                if (stress == 0) {
                                    v = getIntByName(dp, "avg");
                                    if (v > 0) stress = v;
                                }
                            }
                        }
                    }
                    Log.i(TAG, "★ 压力: stress=" + stress);
                    callback.onSuccess(stress);
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "压力失败: code=" + errorCode);
                    callback.onFailure(errorCode, "Error " + errorCode);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "getTodayStress exception", e);
            callback.onFailure(-1, e.getMessage());
        }
    }

    // ─────────────── 7. 个人信息 ───────────────

    public void readUserInfo() {
        Log.d(TAG, ">>> readUserInfo");
        try {
            HeytapHealthApi.getInstance().userInfoApi().readInfo(new HResponse<List<DataSet>>() {
                @Override
                public void onSuccess(List<DataSet> dataSets) {
                    dumpAllDataSets("USER_INFO", dataSets);
                }
                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "readUserInfo failed: code=" + errorCode);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "readUserInfo exception", e);
        }
    }
}
