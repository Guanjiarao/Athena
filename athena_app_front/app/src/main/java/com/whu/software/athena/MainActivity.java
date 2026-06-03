package com.whu.software.athena;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private static final String PREF_AUTH = "auth_prefs";
    private static final String KEY_TOKEN = "token";
    private static final String PREF_CALENDAR_MIGRATION = "calendar_migration";
    private static final String KEY_RECORD_MARKS_DECOUPLED = "v2_record_marks_decoupled";

    private BottomNavigationView navView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        runCalendarDataDecoupleMigrationOnce();
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        navView = findViewById(R.id.nav_view);

        navView.setItemIconTintList(null);
        navView.setBackgroundColor(ContextCompat.getColor(this, R.color.white));
        navView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_knowledge) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new KnowledgeFragment())
                        .commit();
                return true;
            } else if (itemId == R.id.navigation_square) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new SquareFragment())
                        .commit();
                return true;
            } else if (itemId == R.id.navigation_ai) {
                startActivity(new Intent(MainActivity.this, AIActivity.class));
                return false;
            } else if (itemId == R.id.navigation_record) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new RecordFragment())
                        .commit();
                return true;
            } else if (itemId == R.id.navigation_profile) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new ProfileFragment())
                        .commit();
                return true;
            }

            return false;
        });

        boolean hasToken = hasLocalToken();
        if (savedInstanceState == null) {
            if (hasToken) {
                setBottomNavigationVisible(true);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new KnowledgeFragment())
                        .commit();
                navView.setSelectedItemId(R.id.navigation_knowledge);
            } else {
                setBottomNavigationVisible(false);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new ProfileFragment())
                        .commit();
                navView.setSelectedItemId(R.id.navigation_profile);
            }
        } else {
            setBottomNavigationVisible(hasToken);
        }
    }

    public void setBottomNavigationVisible(boolean visible) {
        View host = findViewById(R.id.nav_host_fragment);
        if (navView == null || host == null) {
            return;
        }

        View parent = (View) host.getParent();
        if (!(parent instanceof ConstraintLayout)) {
            navView.setVisibility(visible ? View.VISIBLE : View.GONE);
            return;
        }

        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) host.getLayoutParams();
        if (visible) {
            lp.bottomToTop = R.id.nav_view;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
            navView.setVisibility(View.VISIBLE);
        } else {
            lp.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            navView.setVisibility(View.GONE);
        }
        host.setLayoutParams(lp);
    }

    public void onLoginSuccess() {
        setBottomNavigationVisible(true);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, new KnowledgeFragment())
                .commit();
        if (navView != null) {
            navView.setSelectedItemId(R.id.navigation_knowledge);
        }
    }

    private boolean hasLocalToken() {
        SharedPreferences sp = getSharedPreferences(PREF_AUTH, MODE_PRIVATE);
        String token = sp.getString(KEY_TOKEN, "");
        return token != null && !token.trim().isEmpty();
    }

    private void runCalendarDataDecoupleMigrationOnce() {
        SharedPreferences migrationSp = getSharedPreferences(PREF_CALENDAR_MIGRATION, MODE_PRIVATE);
        if (migrationSp.getBoolean(KEY_RECORD_MARKS_DECOUPLED, false)) {
            return;
        }

        SharedPreferences cycleSp = getSharedPreferences("cycle_settings", MODE_PRIVATE);
        cycleSp.edit()
                .remove("record_marks")
                .remove("record_marks_cache")
                .remove("menstruation_days_from_record_marks")
                .remove("server_marked_dates")
                .apply();

        migrationSp.edit().putBoolean(KEY_RECORD_MARKS_DECOUPLED, true).apply();
    }
}
