package com.whu.software.athena.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.whu.software.athena.config.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Backend cycle-related APIs used by health pages and analysis report.
 */
public final class CycleApiService {

    private static final String TAG = "CycleApiService";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build();

    private CycleApiService() {
    }

    public static final class LatestCycle {
        @Nullable public Long id;
        @Nullable public LocalDate startDate;
        @Nullable public LocalDate endDate;
        @Nullable public LocalDate displayEndDate;
        @Nullable public Integer durationDays;
        @Nullable public Integer displayDurationDays;
        @Nullable public Integer cycleLength;
        @Nullable public LocalDate monthStartDate;
        @Nullable public LocalDate monthEndDate;
        public boolean predicted;
    }

    public static final class CycleSlice {
        @Nullable public Long id;
        @Nullable public LocalDate startDate;
        @Nullable public LocalDate endDate;
        @Nullable public LocalDate displayEndDate;
        @Nullable public Integer durationDays;
        @Nullable public Integer displayDurationDays;
        @Nullable public Integer cycleLength;
        @Nullable public LocalDate monthStartDate;
        @Nullable public LocalDate monthEndDate;
        public boolean predicted;
    }

    public static final class CycleStats {
        @Nullable public Integer averageCycleLength;
        @Nullable public Integer averageDurationDays;
        @Nullable public Integer cycleSampleCount;
        @Nullable public Integer durationSampleCount;
        @Nullable public LocalDate predictedNextStartDate;
        @Nullable public LocalDate predictedNextEndDate;
    }

    public static final class MonthView {
        public int year;
        public int month;
        @NonNull public List<LocalDate> actualDates = new ArrayList<>();
        @NonNull public List<LocalDate> predictedDates = new ArrayList<>();
        @NonNull public List<CycleSlice> actualCycleList = new ArrayList<>();
        public boolean todayInActualCycle;
        public boolean todayInPredictedCycle;
        @Nullable public LocalDate nextPredictedStartDate;
        @Nullable public LocalDate nextPredictedEndDate;
    }

    public static final class CyclePrediction {
        public boolean predictable;
        @Nullable public String reason;
        @Nullable public Integer referenceCycleCount;
        @Nullable public Integer durationSampleCount;
        @Nullable public Integer averageCycleLength;
        @Nullable public Integer averageDurationDays;
        @Nullable public LocalDate predictedStartDate;
        @Nullable public LocalDate predictedEndDate;
    }

    @Nullable
    public static LatestCycle getLatestCycleSync(@NonNull Context context) throws Exception {
        JSONObject data = getDataObject(context, ApiConfig.API_MENSTRUATION_LATEST);
        if (data == null) {
            return null;
        }
        LatestCycle cycle = new LatestCycle();
        populateCycleFields(cycle, data);
        return cycle;
    }

    @Nullable
    public static CycleStats getCycleStatsSync(@NonNull Context context) throws Exception {
        JSONObject data = getDataObject(context, ApiConfig.API_MENSTRUATION_STATS);
        if (data == null) {
            return null;
        }
        CycleStats stats = new CycleStats();
        stats.averageCycleLength = optInteger(data, "averageCycleLength");
        stats.averageDurationDays = optInteger(data, "averageDurationDays");
        stats.cycleSampleCount = optInteger(data, "cycleSampleCount");
        stats.durationSampleCount = optInteger(data, "durationSampleCount");
        stats.predictedNextStartDate = parseDate(data.optString("predictedNextStartDate", null));
        stats.predictedNextEndDate = parseDate(data.optString("predictedNextEndDate", null));
        return stats;
    }

    @Nullable
    public static MonthView getMonthViewSync(@NonNull Context context, int year, int month) throws Exception {
        HttpUrl url = HttpUrl.parse(ApiConfig.API_MENSTRUATION_MONTH)
                .newBuilder()
                .addQueryParameter("year", String.valueOf(year))
                .addQueryParameter("month", String.valueOf(month))
                .build();
        JSONObject data = getDataObject(context, url.toString());
        if (data == null) {
            Log.w(TAG, "getMonthViewSync: data is null for year=" + year + ", month=" + month);
            return null;
        }
        MonthView monthView = new MonthView();
        monthView.year = data.optInt("year", year);
        monthView.month = data.optInt("month", month);
        monthView.actualDates = parseMonthDateList(data, "actualDates", "actualCycleList");
        monthView.predictedDates = parseMonthDateList(data, "predictedDates", "predictedCycleList");
        monthView.actualCycleList = parseCycleSliceList(data.optJSONArray("actualCycleList"));
        monthView.todayInActualCycle = data.optBoolean("todayInActualCycle", false);
        monthView.todayInPredictedCycle = data.optBoolean("todayInPredictedCycle", false);
        monthView.nextPredictedStartDate = parseDate(data.optString("nextPredictedStartDate", null));
        monthView.nextPredictedEndDate = parseDate(data.optString("nextPredictedEndDate", null));
        if (monthView.actualDates.isEmpty() && !monthView.actualCycleList.isEmpty()) {
            for (CycleSlice cycle : monthView.actualCycleList) {
                LocalDate startDate = resolveCycleStartDate(cycle);
                LocalDate endDate = resolveCycleEndDate(cycle);
                if (startDate != null && endDate != null) {
                    addDateRange(monthView.actualDates, startDate, endDate);
                } else if (startDate != null) {
                    addDateIfValid(monthView.actualDates, startDate);
                }
            }
        }
        Log.d(TAG, "getMonthViewSync raw data year=" + year
                + ", month=" + month
                + ", actualDatesRaw=" + data.optJSONArray("actualDates")
                + ", predictedDatesRaw=" + data.optJSONArray("predictedDates")
                + ", actualCycleListRaw=" + data.optJSONArray("actualCycleList")
                + ", predictedCycleListRaw=" + data.optJSONArray("predictedCycleList"));
        Log.d(TAG, "getMonthViewSync parsed year=" + monthView.year
                + ", month=" + monthView.month
                + ", actualDatesCount=" + monthView.actualDates.size()
                + ", actualDates=" + monthView.actualDates
                + ", predictedDatesCount=" + monthView.predictedDates.size()
                + ", predictedDates=" + monthView.predictedDates
                + ", todayInActualCycle=" + monthView.todayInActualCycle
                + ", todayInPredictedCycle=" + monthView.todayInPredictedCycle
                + ", actualCycleListCount=" + monthView.actualCycleList.size()
                + ", nextPredictedStartDate=" + monthView.nextPredictedStartDate
                + ", nextPredictedEndDate=" + monthView.nextPredictedEndDate);
        return monthView;
    }

    @Nullable
    public static CyclePrediction getPredictionSync(@NonNull Context context) throws Exception {
        JSONObject data = getDataObject(context, ApiConfig.API_MENSTRUATION_PREDICTION);
        if (data == null) {
            return null;
        }
        CyclePrediction prediction = new CyclePrediction();
        prediction.predictable = data.optBoolean("predictable", false);
        prediction.reason = emptyToNull(data.optString("reason", null));
        prediction.referenceCycleCount = optInteger(data, "referenceCycleCount");
        prediction.durationSampleCount = optInteger(data, "durationSampleCount");
        prediction.averageCycleLength = optInteger(data, "averageCycleLength");
        prediction.averageDurationDays = optInteger(data, "averageDurationDays");
        prediction.predictedStartDate = parseDate(data.optString("predictedStartDate", null));
        prediction.predictedEndDate = parseDate(data.optString("predictedEndDate", null));
        return prediction;
    }

    public static void deleteCycleSync(@NonNull Context context, long id) throws Exception {
        String token = TokenManager.getToken(context);
        String url = ApiConfig.API_MENSTRUATION_DELETE + "/" + id;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + (token == null ? "" : token))
                .delete()
                .build();
        Log.d(TAG, "deleteCycleSync request url=" + url + ", cycleId=" + id);
        try (Response response = CLIENT.newCall(request).execute()) {
            String raw = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "deleteCycleSync response http=" + response.code() + ", raw=" + raw);
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            JSONObject root = new JSONObject(raw);
            if (root.optInt("code", -1) != 200) {
                throw new IOException(root.optString("message", "delete failed"));
            }
        }
    }

    @Nullable
    private static JSONObject getDataObject(@NonNull Context context, @NonNull String url) throws Exception {
        String token = TokenManager.getToken(context);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + (token == null ? "" : token))
                .get()
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            JSONObject root = new JSONObject(response.body().string());
            if (root.optInt("code", -1) != 200) {
                throw new IOException(root.optString("message", "request failed"));
            }
            Object data = root.opt("data");
            if (data instanceof JSONObject) {
                return (JSONObject) data;
            }
            return null;
        }
    }

    @NonNull
    private static List<LocalDate> parseDateList(@Nullable JSONArray array) {
        List<LocalDate> dates = new ArrayList<>();
        if (array == null) {
            return dates;
        }
        for (int i = 0; i < array.length(); i++) {
            LocalDate date = parseDate(array.optString(i, null));
            if (date != null) {
                dates.add(date);
            }
        }
        return dates;
    }

    @NonNull
    private static List<CycleSlice> parseCycleSliceList(@Nullable JSONArray array) {
        List<CycleSlice> cycles = new ArrayList<>();
        if (array == null) {
            return cycles;
        }
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item == null) {
                continue;
            }
            CycleSlice cycle = null;
            if (item instanceof JSONObject) {
                cycle = parseCycleSlice((JSONObject) item);
            } else if (item instanceof String) {
                LocalDate date = parseDate((String) item);
                if (date != null) {
                    cycle = new CycleSlice();
                    cycle.startDate = date;
                    cycle.displayEndDate = date;
                    cycle.monthStartDate = date;
                    cycle.monthEndDate = date;
                    cycle.durationDays = 1;
                    cycle.displayDurationDays = 1;
                }
            }
            if (cycle != null) {
                cycles.add(cycle);
            }
        }
        return cycles;
    }

    @NonNull
    private static CycleSlice parseCycleSlice(@NonNull JSONObject data) {
        CycleSlice cycle = new CycleSlice();
        populateCycleFields(cycle, data);
        cycle.monthStartDate = firstAvailableDate(data, "monthStartDate", "month_start_date");
        cycle.monthEndDate = firstAvailableDate(data, "monthEndDate", "month_end_date");
        if (cycle.monthStartDate == null) {
            cycle.monthStartDate = cycle.startDate;
        }
        if (cycle.monthEndDate == null) {
            cycle.monthEndDate = cycle.displayEndDate != null ? cycle.displayEndDate : cycle.endDate;
        }
        return cycle;
    }

    private static void populateCycleFields(@NonNull LatestCycle target, @NonNull JSONObject data) {
        target.id = optLong(data, "id");
        target.startDate = firstAvailableDate(data, "startDate", "start_date");
        target.endDate = firstAvailableDate(data, "endDate", "end_date");
        target.displayEndDate = firstAvailableDate(data, "displayEndDate", "display_end_date");
        target.durationDays = optInteger(data, "durationDays");
        target.displayDurationDays = optInteger(data, "displayDurationDays");
        target.cycleLength = optInteger(data, "cycleLength");
        target.monthStartDate = firstAvailableDate(data, "monthStartDate", "month_start_date");
        target.monthEndDate = firstAvailableDate(data, "monthEndDate", "month_end_date");
        target.predicted = data.optBoolean("predicted", false);
        if (target.displayEndDate == null) {
            target.displayEndDate = target.endDate;
        }
        if (target.monthStartDate == null) {
            target.monthStartDate = target.startDate;
        }
        if (target.monthEndDate == null) {
            target.monthEndDate = target.displayEndDate != null ? target.displayEndDate : target.endDate;
        }
        if (target.displayDurationDays == null || target.displayDurationDays <= 0) {
            target.displayDurationDays = deriveDurationDays(target.startDate, target.displayEndDate, target.durationDays);
        }
    }

    private static void populateCycleFields(@NonNull CycleSlice target, @NonNull JSONObject data) {
        target.id = optLong(data, "id");
        target.startDate = firstAvailableDate(data, "startDate", "start_date");
        target.endDate = firstAvailableDate(data, "endDate", "end_date");
        target.displayEndDate = firstAvailableDate(data, "displayEndDate", "display_end_date");
        target.durationDays = optInteger(data, "durationDays");
        target.displayDurationDays = optInteger(data, "displayDurationDays");
        target.cycleLength = optInteger(data, "cycleLength");
        target.monthStartDate = firstAvailableDate(data, "monthStartDate", "month_start_date");
        target.monthEndDate = firstAvailableDate(data, "monthEndDate", "month_end_date");
        target.predicted = data.optBoolean("predicted", false);
        if (target.displayEndDate == null) {
            target.displayEndDate = target.endDate;
        }
        if (target.monthStartDate == null) {
            target.monthStartDate = target.startDate;
        }
        if (target.monthEndDate == null) {
            target.monthEndDate = target.displayEndDate != null ? target.displayEndDate : target.endDate;
        }
        if (target.displayDurationDays == null || target.displayDurationDays <= 0) {
            target.displayDurationDays = deriveDurationDays(target.startDate, target.displayEndDate, target.durationDays);
        }
    }

    @Nullable
    private static LocalDate resolveCycleStartDate(@NonNull CycleSlice cycle) {
        return cycle.startDate != null ? cycle.startDate : cycle.monthStartDate;
    }

    @Nullable
    private static LocalDate resolveCycleEndDate(@NonNull CycleSlice cycle) {
        if (cycle.monthEndDate != null) {
            return cycle.monthEndDate;
        }
        if (cycle.displayEndDate != null) {
            return cycle.displayEndDate;
        }
        if (cycle.endDate != null) {
            return cycle.endDate;
        }
        LocalDate startDate = resolveCycleStartDate(cycle);
        Integer duration = cycle.displayDurationDays != null && cycle.displayDurationDays > 0
                ? cycle.displayDurationDays
                : cycle.durationDays;
        if (startDate != null && duration != null && duration > 0) {
            return startDate.plusDays(duration - 1L);
        }
        return null;
    }

    @Nullable
    private static Integer deriveDurationDays(@Nullable LocalDate startDate,
                                              @Nullable LocalDate endDate,
                                              @Nullable Integer fallbackDuration) {
        if (startDate != null && endDate != null && !endDate.isBefore(startDate)) {
            return (int) (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1);
        }
        return fallbackDuration;
    }

    @NonNull
    private static List<LocalDate> parseMonthDateList(@NonNull JSONObject data,
                                                      @NonNull String dateArrayKey,
                                                      @NonNull String cycleListKey) {
        List<LocalDate> dates = parseDateList(data.optJSONArray(dateArrayKey));
        if (!dates.isEmpty()) {
            return dates;
        }

        JSONArray cycleList = data.optJSONArray(cycleListKey);
        if (cycleList == null) {
            return dates;
        }

        for (int i = 0; i < cycleList.length(); i++) {
            Object item = cycleList.opt(i);
            if (item instanceof String) {
                addDateIfValid(dates, parseDate((String) item));
                continue;
            }
            if (!(item instanceof JSONObject)) {
                continue;
            }

            JSONObject obj = (JSONObject) item;
            LocalDate startDate = firstAvailableDate(obj,
                    "startDate", "start_date", "recordDate", "record_date", "date");
            LocalDate endDate = firstAvailableDate(obj,
                    "endDate", "end_date");
            if (startDate != null && endDate != null) {
                addDateRange(dates, startDate, endDate);
                continue;
            }
            if (startDate != null) {
                addDateIfValid(dates, startDate);
                continue;
            }

            addDateList(dates, parseDateList(obj.optJSONArray(dateArrayKey)));
        }
        return dates;
    }

    private static void addDateList(@NonNull List<LocalDate> target, @NonNull List<LocalDate> source) {
        for (LocalDate date : source) {
            addDateIfValid(target, date);
        }
    }

    private static void addDateIfValid(@NonNull List<LocalDate> target, @Nullable LocalDate date) {
        if (date != null && !target.contains(date)) {
            target.add(date);
        }
    }

    private static void addDateRange(@NonNull List<LocalDate> target,
                                     @NonNull LocalDate start,
                                     @NonNull LocalDate end) {
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            addDateIfValid(target, cursor);
            cursor = cursor.plusDays(1);
        }
    }

    @Nullable
    private static LocalDate firstAvailableDate(@NonNull JSONObject object, @NonNull String... keys) {
        for (String key : keys) {
            LocalDate parsed = parseDate(object.optString(key, null));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    @Nullable
    private static LocalDate parseDate(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Integer optInteger(@NonNull JSONObject object, @NonNull String key) {
        if (!object.has(key) || object.isNull(key)) {
            return null;
        }
        try {
            return object.getInt(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Long optLong(@NonNull JSONObject object, @NonNull String key) {
        if (!object.has(key) || object.isNull(key)) {
            return null;
        }
        try {
            return object.getLong(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
