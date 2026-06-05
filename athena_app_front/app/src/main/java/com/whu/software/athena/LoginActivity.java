package com.whu.software.athena;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.LoginData;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UserDao;
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

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    public static final String EXTRA_PREFILL_ACCOUNT = "prefill_account";
    public static final String EXTRA_RETURN_TO_CALLER = "return_to_caller";
    private static final String PREF_AUTH = "auth_prefs";
    private static final String KEY_TOKEN = "token";

    private TextInputEditText etAccount;
    private TextInputEditText etVerificationCode;
    private Button btnGetCode;
    private Button btnLoginSubmit;
    private TextView tvGoRegister;

    private OkHttpClient client;
    private UserDao userDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        userDao = new UserDao(this);
        client = UnsafeOkHttpClient.getUnsafeOkHttpClient();

        initViews();
        handlePrefill();
    }

    private void initViews() {
        etAccount          = findViewById(R.id.et_account);
        etVerificationCode = findViewById(R.id.et_verification_code);
        btnGetCode         = findViewById(R.id.btn_get_code);
        btnLoginSubmit     = findViewById(R.id.btn_login_submit);
        tvGoRegister       = findViewById(R.id.tv_go_register);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String phone = etAccount.getText().toString().trim();
                String code  = etVerificationCode.getText().toString().trim();
                btnLoginSubmit.setEnabled(isValidPhone(phone) && !TextUtils.isEmpty(code));
            }
        };
        etAccount.addTextChangedListener(watcher);
        etVerificationCode.addTextChangedListener(watcher);

        btnGetCode.setOnClickListener(v -> {
            String phone = etAccount.getText().toString().trim();
            if (!isValidPhone(phone)) {
                Toast.makeText(this, "Please enter a valid 11-digit phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            sendCodeRequest(phone);
        });

        btnLoginSubmit.setOnClickListener(v -> performLogin());

        tvGoRegister.setOnClickListener(v ->
                startActivityForResult(new Intent(this, RegisterActivity.class), 100)
        );
    }

    private void handlePrefill() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_PREFILL_ACCOUNT)) {
            String prefillPhone = intent.getStringExtra(EXTRA_PREFILL_ACCOUNT);
            if (prefillPhone != null) {
                etAccount.setText(prefillPhone);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            String phone = data.getStringExtra(EXTRA_PREFILL_ACCOUNT);
            if (phone != null) {
                etAccount.setText(phone);
            }
        }
    }

    private void performLogin() {
        String phone = etAccount.getText().toString().trim();
        String code  = etVerificationCode.getText().toString().trim();

        if (!isValidPhone(phone)) {
            Toast.makeText(this, "Please enter a valid 11-digit phone number", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(code)) {
            Toast.makeText(this, "Please enter verification code", Toast.LENGTH_SHORT).show();
            return;
        }

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
                .url(ApiConfig.API_LOGIN)
                .post(requestBody)
                .build();

        btnLoginSubmit.setEnabled(false);
        btnLoginSubmit.setText("Logging in...");

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Login request failed", e);
                    Toast.makeText(LoginActivity.this, "Network request failed, please check your network", Toast.LENGTH_SHORT).show();
                    resetLoginButton();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Login response: " + body);
                runOnUiThread(() -> {
                    try {
                        JSONObject resp    = new JSONObject(body);
                        int        resCode = resp.optInt("code", -1);
                        String     msg     = resp.optString("message", "Unknown error");

                        if (resCode == 200) {
                            // token 嵌套在 data 对象中，注意后端 "firsrLogin" 拼写错误
                            JSONObject data       = resp.optJSONObject("data");
                            String     token      = data != null ? data.optString("token", "") : "";
                            boolean    firstLogin = data != null && data.optBoolean("firsrLogin", false);

                            if (!TextUtils.isEmpty(token)) {
                                // 打印 data 全部字段，用于确认后端 userId 的真实 key
                                Log.d(TAG, "Login data 完整内容: " + (data != null ? data.toString() : "null"));

                                // 解析 userId，穷举后端可能使用的所有字段名
                                String userId = "";
                                if (data != null) {
                                    userId = data.optString("userId", "");
                                    if (TextUtils.isEmpty(userId)) userId = data.optString("id", "");
                                    if (TextUtils.isEmpty(userId)) userId = data.optString("user_id", "");
                                    if (TextUtils.isEmpty(userId)) userId = data.optString("uid", "");
                                    if (TextUtils.isEmpty(userId)) userId = data.optString("userID", "");
                                }
                                Log.d(TAG, "Login success, 解析到 userId=[" + userId + "], firstLogin=" + firstLogin);

                                // 持久化 token 和 userId 到本地数据库
                                userDao.open();
                                userDao.insertOrUpdateUser(1, phone, token, userId);
                                userDao.close();
                                saveTokenToSp(token);

                                Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                                if (getIntent().getBooleanExtra(EXTRA_RETURN_TO_CALLER, false)) {
                                    setResult(RESULT_OK);
                                    finish();
                                } else {
                                    // 跳转到主页面
                                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                }
                            } else {
                                Log.e(TAG, "token is empty, data=" + data);
                                Toast.makeText(LoginActivity.this, "登录失败：未获取到 token", Toast.LENGTH_SHORT).show();
                                resetLoginButton();
                            }
                        } else {
                            Toast.makeText(LoginActivity.this, "登录失败：" + msg, Toast.LENGTH_SHORT).show();
                            resetLoginButton();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON 解析失败，原始响应：" + body, e);
                        Toast.makeText(LoginActivity.this, "响应解析失败，请重试", Toast.LENGTH_SHORT).show();
                        resetLoginButton();
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
                    Toast.makeText(LoginActivity.this, "Failed to send verification code, please check your network", Toast.LENGTH_SHORT).show();
                    btnGetCode.setEnabled(true);
                    btnGetCode.setText("Get Code");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Code response: " + respBody);
                runOnUiThread(() -> {
                    if (!response.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Failed to send verification code: " + response.code(), Toast.LENGTH_SHORT).show();
                        btnGetCode.setEnabled(true);
                        btnGetCode.setText("Get Code");
                        return;
                    }
                    try {
                        JSONObject root = new JSONObject(respBody);
                        int bizCode = root.optInt("code", -1);
                        if (bizCode != 200) {
                            String msg = root.optString("message", "发送验证码失败");
                            Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show();
                            btnGetCode.setEnabled(true);
                            btnGetCode.setText("Get Code");
                            return;
                        }

                        String realCode = extractRealVerificationCode(root);
                        if (!TextUtils.isEmpty(realCode)) {
                            etVerificationCode.setText(realCode);
                            new AlertDialog.Builder(LoginActivity.this)
                                    .setTitle("【演示环境】验证码自动提取成功")
                                    .setMessage("由于大创比赛短信资质限制，演示模式下已从服务端安全通道为您自动提取并填入真实验证码：" + realCode + "。请直接点击登录。")
                                    .setPositiveButton("知道了", (dialog, which) -> dialog.dismiss())
                                    .show();
                        } else {
                            Log.w(TAG, "业务成功但未解析到验证码字段，原始响应: " + respBody);
                        }
                        Toast.makeText(LoginActivity.this, "Verification code sent", Toast.LENGTH_SHORT).show();
                        startCountDown();
                    } catch (JSONException e) {
                        Log.e(TAG, "验证码响应 JSON 解析失败: " + respBody, e);
                        Toast.makeText(LoginActivity.this, "响应解析失败", Toast.LENGTH_SHORT).show();
                        btnGetCode.setEnabled(true);
                        btnGetCode.setText("Get Code");
                    }
                });
            }
        });
    }

    /**
     * 在业务 {@code code == 200} 的前提下，从发验证码接口 JSON 中解析短信验证码。
     * 兼容 data 为字符串、对象（含 code / verificationCode 等）或数字等形式。
     */
    private String extractRealVerificationCode(JSONObject root) {
        if (root == null) {
            return null;
        }
        if (!root.isNull("data")) {
            try {
                Object dataObj = root.get("data");
                if (dataObj instanceof String) {
                    String s = ((String) dataObj).trim();
                    if (!TextUtils.isEmpty(s) && !"null".equalsIgnoreCase(s)) {
                        return s;
                    }
                } else if (dataObj instanceof JSONObject) {
                    JSONObject data = (JSONObject) dataObj;
                    String[] keys = {
                            "code",
                            "verificationCode",
                            "verifyCode",
                            "smsCode",
                            "captcha",
                            "sms_code",
                            "verification_code",
                            "vcode"
                    };
                    for (String key : keys) {
                        if (!data.has(key) || data.isNull(key)) {
                            continue;
                        }
                        String str = data.optString(key, "").trim();
                        if (!TextUtils.isEmpty(str)) {
                            return str;
                        }
                        try {
                            if (data.optLong(key, Long.MIN_VALUE) != Long.MIN_VALUE) {
                                return String.valueOf(data.optLong(key));
                            }
                        } catch (Exception ignored) {
                            // ignore
                        }
                    }
                } else if (dataObj instanceof Number) {
                    return String.valueOf(((Number) dataObj).longValue());
                }
            } catch (JSONException e) {
                Log.w(TAG, "读取 data 字段失败", e);
            }
        }
        String[] rootKeys = {"verificationCode", "verifyCode", "smsCode", "sms_code", "verification_code"};
        for (String key : rootKeys) {
            String s = root.optString(key, "").trim();
            if (!TextUtils.isEmpty(s)) {
                return s;
            }
        }
        return null;
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

    private void resetLoginButton() {
        btnLoginSubmit.setText("Login");
        btnLoginSubmit.setEnabled(
                isValidPhone(etAccount.getText().toString().trim()) &&
                !TextUtils.isEmpty(etVerificationCode.getText().toString().trim())
        );
    }

    private boolean isValidPhone(String phone) {
        return !TextUtils.isEmpty(phone) && phone.matches("\\d{11}");
    }

    private void saveTokenToSp(String token) {
        SharedPreferences sp = getSharedPreferences(PREF_AUTH, MODE_PRIVATE);
        sp.edit().putString(KEY_TOKEN, token).apply();
    }
}
