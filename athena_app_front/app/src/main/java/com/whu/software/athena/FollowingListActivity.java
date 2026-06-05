package com.whu.software.athena;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.UserEntity;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class FollowingListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private UserAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_following_list);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.rv_following);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new UserAdapter();
        recyclerView.setAdapter(adapter);

        loadFollowingList();
    }

    private void loadFollowingList() {
        String token = TokenManager.getToken(this);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }

        Request request = new Request.Builder()
                .url(ApiConfig.API_FOLLOWING_LIST)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        UnsafeOkHttpClient.getUnsafeOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> Toast.makeText(FollowingListActivity.this, "Request failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseStr = response.body().string();
                try {
                    Gson gson = new Gson();
                    Type type = new TypeToken<ApiResponse<List<UserEntity>>>(){}.getType();
                    ApiResponse<List<UserEntity>> apiResponse = gson.fromJson(responseStr, type);

                    mainHandler.post(() -> {
                        if (apiResponse != null && apiResponse.getCode() == 200) {
                            List<UserEntity> users = apiResponse.getData();
                            if (users != null) {
                                adapter.setData(users);
                            } else {
                                adapter.setData(new ArrayList<>());
                            }
                        } else {
                            String msg = apiResponse != null ? apiResponse.getMessage() : "Failed to get following list";
                            Toast.makeText(FollowingListActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> Toast.makeText(FollowingListActivity.this, "Failed to parse data", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public T getData() {
            return data;
        }
    }
}
