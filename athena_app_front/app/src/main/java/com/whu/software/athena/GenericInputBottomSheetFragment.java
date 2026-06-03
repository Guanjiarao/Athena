package com.whu.software.athena;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.whu.software.athena.utils.HealthRecordSaver;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic bottom sheet used by health record input rows.
 */
public class GenericInputBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_HINT = "arg_hint";
    private static final String ARG_KEY = "arg_key";
    private static final String ARG_RECORD_DATE = "arg_record_date";
    private static final String ARG_RECORD_ITEM_ID = "arg_record_item_id";
    private static final String ARG_MODE_TYPE = "arg_mode_type";

    public static final String REQUEST_KEY_RECORD_SAVED = "request_key_record_saved";
    public static final String BUNDLE_KEY_DATE = "bundle_key_date";

    private static final String NET_TAG = "AthenaNet";

    private static final Map<String, Integer> KEY_TO_ITEM_ID = new HashMap<>();

    static {
        KEY_TO_ITEM_ID.put("key_symptoms", 3);
        KEY_TO_ITEM_ID.put("key_symptoms_prep", 3);
        KEY_TO_ITEM_ID.put("key_symptoms_pregnancy", 3);

        KEY_TO_ITEM_ID.put("key_mood", 4);
        KEY_TO_ITEM_ID.put("key_mood_prep", 4);
        KEY_TO_ITEM_ID.put("key_mood_pregnancy", 4);

        KEY_TO_ITEM_ID.put("key_discharge", 5);
        KEY_TO_ITEM_ID.put("key_discharge_prep", 5);

        KEY_TO_ITEM_ID.put("key_temp", 6);
        KEY_TO_ITEM_ID.put("key_bbt_prep", 6);
        KEY_TO_ITEM_ID.put("key_temp_pregnancy", 6);

        KEY_TO_ITEM_ID.put("key_weight", 7);
        KEY_TO_ITEM_ID.put("key_weight_prep", 7);

        KEY_TO_ITEM_ID.put("key_diary", 8);
        KEY_TO_ITEM_ID.put("key_habit", 9);

        KEY_TO_ITEM_ID.put("key_poop", 10);
        KEY_TO_ITEM_ID.put("key_poop_prep", 10);
        KEY_TO_ITEM_ID.put("key_poop_pregnancy", 10);

        KEY_TO_ITEM_ID.put("key_nutrition_pregnancy", 11);
        KEY_TO_ITEM_ID.put("key_blood_sugar_pregnancy", 12);
        KEY_TO_ITEM_ID.put("key_plan_pregnancy", 13);
    }

    public static GenericInputBottomSheetFragment newInstance(String title, String hint, String key) {
        return newInstance(title, hint, key, null, -1, -1);
    }

    public static GenericInputBottomSheetFragment newInstance(String title,
                                                              String hint,
                                                              String key,
                                                              String recordDate,
                                                              int recordItemId,
                                                              int modeType) {
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title != null ? title : "");
        args.putString(ARG_HINT, hint != null ? hint : "");
        args.putString(ARG_KEY, key != null ? key : "");
        args.putString(ARG_RECORD_DATE, recordDate);
        args.putInt(ARG_RECORD_ITEM_ID, recordItemId);
        args.putInt(ARG_MODE_TYPE, modeType);

        GenericInputBottomSheetFragment fragment = new GenericInputBottomSheetFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_generic_input_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String title = args != null ? args.getString(ARG_TITLE, "") : "";
        String hint = args != null ? args.getString(ARG_HINT, "") : "";
        String key = args != null ? args.getString(ARG_KEY, "") : "";
        String argDate = args != null ? args.getString(ARG_RECORD_DATE, null) : null;
        int argItemId = args != null ? args.getInt(ARG_RECORD_ITEM_ID, -1) : -1;
        int argModeType = args != null ? args.getInt(ARG_MODE_TYPE, -1) : -1;

        TextView tvTitle = view.findViewById(R.id.tv_bs_title);
        EditText etInput = view.findViewById(R.id.et_bs_input);
        tvTitle.setText(title);
        if (!TextUtils.isEmpty(hint)) {
            etInput.setHint(hint);
        }

        view.findViewById(R.id.btn_bs_cancel).setOnClickListener(v -> dismiss());

        view.findViewById(R.id.btn_bs_save).setOnClickListener(v -> {
            String content = etInput.getText() != null ? etInput.getText().toString().trim() : "";
            if (TextUtils.isEmpty(content)) {
                Toast.makeText(requireContext(), "请输入内容后再保存", Toast.LENGTH_SHORT).show();
                return;
            }

            int itemId = argItemId > 0 ? argItemId : resolveRecordItemId(key);
            if (itemId <= 0) {
                Toast.makeText(requireContext(), "当前记录项暂未接入，请稍后再试", Toast.LENGTH_SHORT).show();
                return;
            }

            String recordDate = !TextUtils.isEmpty(argDate) ? argDate : resolveOperateDate();
            if (TextUtils.isEmpty(recordDate)) {
                Toast.makeText(requireContext(), "未获取到记录日期，请重试", Toast.LENGTH_SHORT).show();
                return;
            }

            int modeType = argModeType > 0 ? argModeType : resolveModeTypeFromParentFragment();
            if (modeType <= 0) {
                Toast.makeText(requireContext(), "未识别当前健康模式，请稍后再试", Toast.LENGTH_SHORT).show();
                return;
            }

            CharSequence titleCs = tvTitle.getText();
            String titleText = titleCs != null ? titleCs.toString() : "";
            HealthRecordSaver.postRecordSave(
                    requireContext(),
                    recordDate,
                    itemId,
                    content,
                    modeType,
                    () -> {
                        Toast.makeText(
                                requireContext(),
                                titleText + "记录成功：" + content,
                                Toast.LENGTH_SHORT
                        ).show();
                        Bundle result = new Bundle();
                        result.putString(BUNDLE_KEY_DATE, recordDate);
                        Log.d(NET_TAG, "发送记录刷新事件: date=" + recordDate);
                        getParentFragmentManager().setFragmentResult(REQUEST_KEY_RECORD_SAVED, result);
                        dismiss();
                    });
        });
    }

    private int resolveRecordItemId(String key) {
        Integer itemId = KEY_TO_ITEM_ID.get(key);
        return itemId != null ? itemId : -1;
    }

    private String resolveOperateDate() {
        if (getParentFragment() instanceof PeriodFragment) {
            return ((PeriodFragment) getParentFragment()).getOperateDateForRecord();
        }
        if (getParentFragment() instanceof PregnancyPrepFragment) {
            return ((PregnancyPrepFragment) getParentFragment()).getOperateDateForRecord();
        }
        if (getParentFragment() instanceof PregnancyFragment) {
            return ((PregnancyFragment) getParentFragment()).getOperateDateForRecord();
        }
        return null;
    }

    private int resolveModeTypeFromParentFragment() {
        if (getParentFragment() instanceof PeriodFragment) {
            return 1;
        }
        if (getParentFragment() instanceof PregnancyPrepFragment) {
            return 2;
        }
        if (getParentFragment() instanceof PregnancyFragment) {
            return 3;
        }
        return -1;
    }
}
