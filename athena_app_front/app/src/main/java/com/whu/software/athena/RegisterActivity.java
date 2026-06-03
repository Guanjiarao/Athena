package com.whu.software.athena;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private TextInputEditText etAccount;
    private TextInputEditText etCode;
    private Button btnGetCode;
    private Button btnRegisterSubmit;

    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        client = UnsafeOkHttpClient.getUnsafeOkHttpClient();
        initViews();
    }

    private void initViews() {
        etAccount         = findViewById(R.id.et_reg_account);
        etCode            = findViewById(R.id.et_reg_code);
        btnGetCode        = findViewById(R.id.btn_reg_get_code);
        btnRegisterSubmit = findViewById(R.id.btn_register_submit);
        TextView tvBack   = findViewById(R.id.tv_back);

        tvBack.setOnClickListener(v -> finish());

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                btnRegisterSubmit.setEnabled(
                        isValidPhone(etAccount.getText().toString().trim()) &&
                        !TextUtils.isEmpty(etCode.getText())
                );
            }
        };
        etAccount.addTextChangedListener(watcher);
        etCode.addTextChangedListener(watcher);

        btnGetCode.setOnClickListener(v -> {
            String phone = etAccount.getText().toString().trim();
            if (!isValidPhone(phone)) {
                Toast.makeText(this, "Please enter a valid 11-digit phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            sendCodeRequest(phone);
        });

        btnRegisterSubmit.setOnClickListener(v -> performRegister());
    }

    private void performRegister() {
        String phone = etAccount.getText().toString().trim();
        String code  = etCode.getText().toString().trim();

        if (!isValidPhone(phone)) {
            Toast.makeText(this, "Please enter a valid 11-digit phone number", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(code)) {
            Toast.makeText(this, "Please enter verification code", Toast.LENGTH_SHORT).show();
            return;
        }

        sendRegisterRequest(phone, code);
    }

    private void sendRegisterRequest(String phone, String code) {
        JSONObject json = new JSONObject();
        try {
            json.put("phone", phone);
            json.put("code", code);
        } catch (JSONException e) {
            Toast.makeText(this, "Data format error", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestBody requestBody = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );
        Request request = new Request.Builder()
                .url(ApiConfig.API_REGISTER)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build();

        btnRegisterSubmit.setEnabled(false);
        btnRegisterSubmit.setText("Registering...");

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Registration request failed", e);
                    Toast.makeText(RegisterActivity.this, "Network request failed, please check your network", Toast.LENGTH_SHORT).show();
                    resetButton();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                Log.d(TAG, "Registration response: " + body);
                runOnUiThread(() -> {
                    try {
                        JSONObject resp = new JSONObject(body);
                        int respCode = resp.optInt("code", -1);
                        String msg   = resp.optString("message", "Unknown error");

                        if (respCode == 200) {
                            Intent result = new Intent();
                            result.putExtra(LoginActivity.EXTRA_PREFILL_ACCOUNT, phone);
                            setResult(RESULT_OK, result);
                            finish();
                        } else {
                            Toast.makeText(RegisterActivity.this, "Registration failed: " + msg, Toast.LENGTH_SHORT).show();
                            resetButton();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing failed", e);
                        Toast.makeText(RegisterActivity.this, "Response parsing error", Toast.LENGTH_SHORT).show();
                        resetButton();
                    }
                });
            }
        });
    }

    private void sendCodeRequest(String phone) {
        RequestBody body = new FormBody.Builder().add("phone", phone).build();
        Request request  = new Request.Builder()
                .url(ApiConfig.API_LOGIN_CODE)
                .post(body)
                .build();

        btnGetCode.setEnabled(false);
        btnGetCode.setText("Sending...");

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Code request failed", e);
                    Toast.makeText(RegisterActivity.this, "Failed to send verification code, please check your network", Toast.LENGTH_SHORT).show();
                    btnGetCode.setEnabled(true);
                    btnGetCode.setText("Get Code");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String respBody = response.body().string();
                Log.d(TAG, "Code response: " + respBody);
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this, "Verification code sent", Toast.LENGTH_SHORT).show();
                        startCountDown();
                    } else {
                        Toast.makeText(RegisterActivity.this, "Failed to send verification code: " + response.code(), Toast.LENGTH_SHORT).show();
                        btnGetCode.setEnabled(true);
                        btnGetCode.setText("Get Code");
                    }
                });
            }
        });
    }

    private void startCountDown() {
        new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!isFinishing() && !isDestroyed()) {
                    btnGetCode.setText(millisUntilFinished / 1000 + "s");
                } else {
                    cancel();
                }
            }

            @Override
            public void onFinish() {
                if (!isFinishing() && !isDestroyed()) {
                    btnGetCode.setText("Get Code");
                    btnGetCode.setEnabled(true);
                }
            }
        }.start();
    }

    private void resetButton() {
        btnRegisterSubmit.setText("Complete Registration");
        btnRegisterSubmit.setEnabled(
                isValidPhone(etAccount.getText().toString().trim()) &&
                !TextUtils.isEmpty(etCode.getText())
        );
    }

    private boolean isValidPhone(String phone) {
        return !TextUtils.isEmpty(phone) && phone.matches("\\d{11}");
    }
}
