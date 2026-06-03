package com.whu.software.athena;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * 记录页根 Fragment。
 *
 * <p>作为"经期 / 备孕 / 怀孕"三个子页面的容器。
 * 默认显示 {@link PeriodFragment}（经期页），
 * 经期页的 Tab 点击"备孕"时由此 Fragment 将子 Fragment 替换为
 * {@link PregnancyPrepFragment}，备孕页点击"经期"时回调 {@link #switchToPeriod()}。
 */
public class RecordFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_record, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState == null) {
            switchToPeriod();
        }
    }

    /** 切换到经期页（PeriodFragment），由备孕页 Tab 点击触发。 */
    public void switchToPeriod() {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.record_fragment_container, new PeriodFragment())
                .commit();
    }

    /** 切换到备孕页（PregnancyPrepFragment），由经期页 Tab 点击触发。 */
    public void switchToPregnancyPrep() {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.record_fragment_container, new PregnancyPrepFragment())
                .commit();
    }

    /** 切换到怀孕页（PregnancyFragment），由经期/备孕页 Tab 点击触发。 */
    public void switchToPregnancy() {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.record_fragment_container, new PregnancyFragment())
                .commit();
    }
}
