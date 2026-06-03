package com.whu.software.athena.features.monitor;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.whu.software.athena.R;

import java.util.Random;

public class MonitorFragment extends Fragment {

    private TextView tvHeartRate, tvAnalysisResult;
    private Button btnStress, btnCapture;
    private ImageView ivFace;
    
    private Handler handler;
    private Runnable hrRunnable;
    private boolean isStressMode = false;
    private int currentHr = 80;
    private Random random = new Random();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_monitor, container, false);
        
        tvHeartRate = view.findViewById(R.id.tvHeartRate);
        tvAnalysisResult = view.findViewById(R.id.tvAnalysisResult);
        btnStress = view.findViewById(R.id.btnStress);
        btnCapture = view.findViewById(R.id.btnCapture);
        ivFace = view.findViewById(R.id.ivFace);

        btnStress.setOnClickListener(v -> {
            isStressMode = true;
            Toast.makeText(getContext(), "Stress Mode Activated!", Toast.LENGTH_SHORT).show();
            // Reset after 10 seconds
            new Handler().postDelayed(() -> isStressMode = false, 10000);
        });
        
        btnCapture.setOnClickListener(v -> analyzeFace());

        startHeartRateMonitor();

        return view;
    }

    private void startHeartRateMonitor() {
        handler = new Handler(Looper.getMainLooper());
        hrRunnable = new Runnable() {
            @Override
            public void run() {
                updateHeartRate();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(hrRunnable);
    }

    private void updateHeartRate() {
        if (getActivity() == null) return;

        int target = isStressMode ? 130 : 75;
        // Move towards target
        if (currentHr < target) currentHr += random.nextInt(5);
        else if (currentHr > target) currentHr -= random.nextInt(5);
        
        // Add noise
        currentHr += (random.nextInt(3) - 1);
        
        tvHeartRate.setText(currentHr + " BPM");
        if (currentHr > 120) {
            tvHeartRate.setTextColor(0xFFFF0000); // Red
        } else {
            tvHeartRate.setTextColor(0xFF000000); // Black
        }
    }

    private void analyzeFace() {
        // Mock analysis since we don't have real camera implementation in this snippet
        // In real app, we would use CameraX to capture image
        
        // Create a dummy bitmap (greyish)
        Bitmap dummy = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        dummy.eraseColor(0xFFDDDDDD); 
        ivFace.setImageBitmap(dummy);
        
        // Analyze logic (Mock)
        boolean isPale = random.nextBoolean();
        String result = isPale ? "Status: Pale (Low Vitality)" : "Status: Healthy (Good Color)";
        
        tvAnalysisResult.setText(result);
        
        if (isPale && currentHr > 100) {
             Toast.makeText(getContext(), "ALERT: High HR + Pale Face detected!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null && hrRunnable != null) {
            handler.removeCallbacks(hrRunnable);
        }
    }
}
