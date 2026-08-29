package com.whu.software.athena;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import org.json.JSONObject;
import com.whu.software.athena.utils.UserDao;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.net.FollowRequestManager;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private LinearLayout layoutNotLoggedIn;
    private View layoutLoggedIn;

    private ShapeableImageView ivAvatar;
    private TextView tvUsername;
    private TextView tvUserId;
    private TextView tvFollowingCount;
    private TextView tvFollowersCount;
    private TextView tvLikesCount;
    private LinearLayout layoutFollowing;
    private LinearLayout layoutFollowers;
    private LinearLayout layoutLikes;
    private TabLayout tabLayoutProfile;
    private ViewPager2 vpProfileContent;
    private View ivSettings;
    private View btnDataAsset;
    private View btnLogin;
    private View itemProfileEdit;
    private View itemProfileNotifications;
    private View itemProfilePrivacy;
    private View itemProfileAbout;
    private View btnProfileLogout;
    private ChipGroup profileChipPreferences;

    private UserDao userDao;
    private FollowRequestManager followRequestManager;
    private ActivityResultLauncher<Intent> loginLauncher;

    private static final String[] TAB_TITLES = {"收藏", "点赞", "历史"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        userDao = new UserDao(requireContext());
        followRequestManager = FollowRequestManager.getInstance(requireContext());
        loginLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == androidx.appcompat.app.AppCompatActivity.RESULT_OK && hasToken()) {
                        loadLoginStatus();
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).onLoginSuccess();
                        }
                    }
                }
        );
        initViews(view);
        setupClickListeners();
        restorePreferenceChips();
        loadLoginStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadLoginStatus();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (userDao != null) {
            userDao.close();
        }
    }

    private void initViews(View view) {
        layoutNotLoggedIn = view.findViewById(R.id.layout_not_logged_in);
        layoutLoggedIn    = view.findViewById(R.id.layout_logged_in);
        btnLogin          = view.findViewById(R.id.btn_login);

        ivAvatar          = view.findViewById(R.id.iv_avatar);
        tvUsername        = view.findViewById(R.id.tv_username);
        tvUserId          = view.findViewById(R.id.tv_user_id);
        tvFollowingCount  = view.findViewById(R.id.tv_following_count);
        tvFollowersCount  = view.findViewById(R.id.tv_followers_count);
        tvLikesCount      = view.findViewById(R.id.tv_likes_count);
        layoutFollowing   = view.findViewById(R.id.layout_following);
        layoutFollowers   = view.findViewById(R.id.layout_followers);
        layoutLikes       = view.findViewById(R.id.layout_likes);
        ivSettings        = view.findViewById(R.id.iv_settings);
        btnDataAsset      = view.findViewById(R.id.btn_data_asset);
        itemProfileEdit = view.findViewById(R.id.item_profile_edit);
        itemProfileNotifications = view.findViewById(R.id.item_profile_notifications);
        itemProfilePrivacy = view.findViewById(R.id.item_profile_privacy);
        itemProfileAbout = view.findViewById(R.id.item_profile_about);
        btnProfileLogout = view.findViewById(R.id.btn_profile_logout);
        profileChipPreferences = null;
    }

    private void setupTabAndPager() {
        vpProfileContent.setAdapter(new FragmentStateAdapter(requireActivity()) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                // ProfileGridFragment 的 0 仍对应旧的“作品”接口；个人页从收藏开始展示。
                return ProfileGridFragment.newInstance(position + 1);
            }

            @Override
            public int getItemCount() {
                return TAB_TITLES.length;
            }
        });

        new TabLayoutMediator(tabLayoutProfile, vpProfileContent,
                (tab, position) -> tab.setText(TAB_TITLES[position])
        ).attach();

        // Enable nested scrolling for ViewPager2 to work properly with NestedScrollView
        vpProfileContent.setNestedScrollingEnabled(true);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> startLoginActivity());

        itemProfileEdit.setOnClickListener(v -> startActivity(new Intent(requireActivity(), EditProfileActivity.class)));
        itemProfileNotifications.setOnClickListener(v -> Toast.makeText(requireContext(), "消息通知（开发中）", Toast.LENGTH_SHORT).show());
        itemProfilePrivacy.setOnClickListener(v -> startActivity(new Intent(requireActivity(), DataPrivacyActivity.class)));
        itemProfileAbout.setOnClickListener(v -> Toast.makeText(requireContext(), "关于我们（开发中）", Toast.LENGTH_SHORT).show());
        btnProfileLogout.setOnClickListener(v -> confirmLogout());
        if (profileChipPreferences != null) {
            profileChipPreferences.setOnCheckedStateChangeListener((group, checkedIds) -> savePreferenceChips());
        }

        if (btnDataAsset != null) {
            btnDataAsset.setOnClickListener(v ->
                    startActivity(new Intent(requireActivity(), MyCognitionActivity.class)));
        }
    }

    private void savePreferenceChips() {
        if (profileChipPreferences == null) return;
        StringBuilder value = new StringBuilder();
        for (int id : profileChipPreferences.getCheckedChipIds()) {
            Chip chip = profileChipPreferences.findViewById(id);
            if (chip != null) {
                if (value.length() > 0) value.append(",");
                value.append(chip.getText());
            }
        }
        requireContext().getSharedPreferences("athena_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putString("user_preferences", value.toString()).apply();
    }

    private void restorePreferenceChips() {
        if (profileChipPreferences == null) return;
        String saved = requireContext().getSharedPreferences("athena_prefs", android.content.Context.MODE_PRIVATE)
                .getString("user_preferences", "");
        if (saved == null || saved.isEmpty()) return;
        for (String label : saved.split(",")) {
            for (int i = 0; i < profileChipPreferences.getChildCount(); i++) {
                View child = profileChipPreferences.getChildAt(i);
                if (child instanceof Chip && ((Chip) child).getText().toString().equals(label.trim())) {
                    ((Chip) child).setChecked(true);
                }
            }
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出当前账号吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出", (dialog, which) -> performLogout())
                .show();
    }

    private void performLogout() {
        try {
            userDao.open();
            String[] currentUser = userDao.getCurrentLoginUser();
            if (currentUser != null && currentUser.length > 1) {
                userDao.updateLoginStatus(currentUser[1], 0);
            }
        } finally {
            userDao.close();
        }
        requireContext().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                .edit().remove("token").apply();
        startActivity(new Intent(requireActivity(), LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        requireActivity().finish();
    }

    private void startLoginActivity() {
        try {
            Intent intent = new Intent(requireActivity(), LoginActivity.class);
            intent.putExtra(LoginActivity.EXTRA_RETURN_TO_CALLER, true);
            loginLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "启动 LoginActivity 失败", e);
            if (isAdded() && getContext() != null) {
                Toast.makeText(getContext(), "启动登录页面失败，请重试", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openSettings() {
        try {
            userDao.open();
            String[] currentUser = userDao.getCurrentLoginUser();
            boolean isLoggedIn = (currentUser != null && "1".equals(currentUser[0]));
            if (!isLoggedIn) {
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(requireActivity(), SettingsActivity.class));
        } catch (Exception e) {
            Log.e(TAG, "打开设置页面失败", e);
        } finally {
            userDao.close();
        }
    }

    private void loadLoginStatus() {
        if (!hasToken()) {
            showLoggedOutUI();
            return;
        }
        try {
            userDao.open();
            String[] currentUser = userDao.getCurrentLoginUser();
            boolean isLoggedIn = currentUser != null
                    && currentUser.length >= 2
                    && "1".equals(currentUser[0]);
            if (isLoggedIn) {
                showLoggedInUI(currentUser);
            } else {
                // token 存在但本地用户缓存缺失时，先展示已登录骨架，避免误判成未登录
                showLoggedInUI(new String[]{"1", "用户"});
            }
        } catch (Exception e) {
            Log.e(TAG, "加载登录状态失败", e);
            showLoggedInUI(new String[]{"1", "用户"});
        } finally {
            if (userDao != null) {
                userDao.close();
            }
        }
    }

    private void showLoggedInUI(String[] userInfo) {
        applyProfileLoginUi(true);

        String phone = userInfo.length >= 2 && userInfo[1] != null ? userInfo[1] : "用户";
        tvUsername.setText("用户" + (phone.length() >= 4 ? phone.substring(phone.length() - 4) : phone));
        tvUserId.setText("ID: " + phone);
        tvFollowingCount.setText("0");
        tvFollowersCount.setText("0");
        tvLikesCount.setText("0");

        // 请求用户详情信息，包括头像
        followRequestManager.requestUserInfo(null, new FollowRequestManager.UserInfoCallback() {
            @Override
            public void onSuccess(JSONObject userInfo) {
                try {
                    // 更新昵称
                    if (userInfo.has("nickName")) {
                        String nickName = userInfo.getString("nickName");
                        if (!nickName.isEmpty()) {
                            tvUsername.setText(nickName);
                        }
                    }
                    
                    // 更新用户ID
                    if (userInfo.has("userId")) {
                        long userId = userInfo.getLong("userId");
                        tvUserId.setText("ID: " + userId);
                    }
                    
                    // 更新头像
                    if (userInfo.has("icon")) {
                        String iconUrl = userInfo.getString("icon");
                        if (!iconUrl.isEmpty()) {
                            Glide.with(requireContext())
                                    .load(iconUrl)
                                    .circleCrop()
                                    .placeholder(R.drawable.circle_background)
                                    .into(ivAvatar);
                        }
                    }
                    
                    // 获赞数暂未联调完成：空值/字符串"null"一律回退到0
                    String likeTotal = userInfo.optString("likeTotal", "0");
                    if (likeTotal == null || likeTotal.trim().isEmpty() || "null".equalsIgnoreCase(likeTotal.trim())) {
                        likeTotal = "0";
                    }
                    tvLikesCount.setText(likeTotal);
                    
                } catch (Exception e) {
                    Log.e(TAG, "解析用户信息失败: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e(TAG, "获取用户信息失败: " + errorMsg);
            }
        });

        followRequestManager.requestFollowCount(null, new FollowRequestManager.FollowCountCallback() {
            @Override
            public void onSuccess(int count) {
                tvFollowingCount.setText(String.valueOf(count));
            }

            @Override
            public void onFailure(String errorMsg) {
                // 静默失败
            }
        });

        followRequestManager.requestFanCount(null, new FollowRequestManager.FanCountCallback() {
            @Override
            public void onSuccess(int count) {
                tvFollowersCount.setText(String.valueOf(count));
            }

            @Override
            public void onFailure(String errorMsg) {
                // 静默失败
            }
        });
    }

    private void showLoggedOutUI() {
        applyProfileLoginUi(false);
    }

    /**
     * 根据登录状态切换「我的」内层布局，并跨层级控制宿主 Activity 底部导航（R.id.nav_view）。
     * 同时委托 MainActivity 调整 nav_host 底部约束与 FAB，保证未登录时内容区真正全屏。
     */
    private void applyProfileLoginUi(boolean isLoggedIn) {
        if (isLoggedIn) {
            layoutNotLoggedIn.setVisibility(View.GONE);
            layoutLoggedIn.setVisibility(View.VISIBLE);
        } else {
            layoutNotLoggedIn.setVisibility(View.VISIBLE);
            layoutLoggedIn.setVisibility(View.GONE);
        }
        if (getActivity() instanceof MainActivity) {
            boolean demoMode = !requireContext().getSharedPreferences("athena_cognition_config", android.content.Context.MODE_PRIVATE)
                    .getBoolean("use_http", false);
            ((MainActivity) getActivity()).setBottomNavigationVisible(isLoggedIn || demoMode);
        }
    }

    private boolean hasToken() {
        String token = TokenManager.getToken(requireContext());
        return token != null && !token.trim().isEmpty();
    }

    public void onLoginSuccess() {
        loadLoginStatus();
    }

    public void refreshProfile() {
        loadLoginStatus();
    }
}
