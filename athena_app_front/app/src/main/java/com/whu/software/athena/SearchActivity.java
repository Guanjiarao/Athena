package com.whu.software.athena;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.whu.software.athena.db.SearchHistoryDBHelper;

import java.util.List;

public class SearchActivity extends AppCompatActivity {
    // 搜索类型常量
    public static final int SEARCH_TYPE_KNOWLEDGE = 0; // 知识页面搜索
    public static final int SEARCH_TYPE_SQUARE = 1;   // 广场页面搜索
    
    // 搜索类型参数
    private int searchType;
    
    // 视图
    private ImageButton ivBack;
    private EditText etSearch;
    private ImageView ivClear;
    private TextView tvSearch;
    private LinearLayout llHistoryContainer;
    private TextView tvClearHistory;
    
    // 数据库助手
    private SearchHistoryDBHelper dbHelper;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        
        // 设置ActionBar返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("搜索");
        }
        
        // 初始化视图
        initViews();
        
        // 获取传递的搜索类型参数
        Intent intent = getIntent();
        searchType = intent.getIntExtra("search_type", SEARCH_TYPE_KNOWLEDGE); // 默认值为知识搜索
        
        Log.d("SearchActivity", "Search type: " + searchType);
        
        // 初始化数据库助手
        String dbName = SearchHistoryDBHelper.getDBNameByType(searchType);
        dbHelper = new SearchHistoryDBHelper(this, dbName);
        
        // 加载搜索历史
        loadSearchHistory();
        
        // 设置搜索框焦点
        etSearch.requestFocus();
    }
    
    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        etSearch = findViewById(R.id.et_search);
        ivClear = findViewById(R.id.iv_clear);
        tvSearch = findViewById(R.id.tv_search);
        llHistoryContainer = findViewById(R.id.ll_history_container);
        tvClearHistory = findViewById(R.id.tv_clear_history);

        // 退出/返回按钮
        ivBack.setOnClickListener(v -> finish());
        
        // 设置搜索框文本变化监听器
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 显示/隐藏清除按钮
                ivClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
        
        // 设置搜索框软键盘搜索按钮监听
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
        
        // 清除按钮点击事件
        ivClear.setOnClickListener(v -> etSearch.setText(""));
        
        // 搜索按钮点击事件
        tvSearch.setOnClickListener(v -> performSearch());
        
        // 清空历史记录按钮点击事件
        tvClearHistory.setOnClickListener(v -> {
            dbHelper.clearAllSearchHistory();
            loadSearchHistory();
            Toast.makeText(this, "已清空搜索历史", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void performSearch() {
        String searchText = etSearch.getText().toString().trim();
        if (searchText.isEmpty()) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 保存搜索历史
        dbHelper.insertSearchHistory(searchText);
        loadSearchHistory();
        
        // 跳转到搜索结果页面
        Intent intent = new Intent(this, SearchResultActivity.class);
        intent.putExtra("searchType", searchType == SEARCH_TYPE_KNOWLEDGE ? "knowledge" : "square");
        intent.putExtra("searchText", searchText);
        startActivity(intent);
    }
    
    /**
     * 加载搜索历史
     */
    private void loadSearchHistory() {
        // 清空历史记录容器
        llHistoryContainer.removeAllViews();
        
        // 获取搜索历史列表
        List<String> historyList = dbHelper.getSearchHistoryList();
        
        if (historyList.isEmpty()) {
            // 没有搜索历史
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("暂无搜索历史");
            tvEmpty.setTextSize(14);
            tvEmpty.setTextColor(getResources().getColor(R.color.text_secondary));
            tvEmpty.setPadding(16, 16, 16, 16);
            llHistoryContainer.addView(tvEmpty);
            tvClearHistory.setVisibility(View.GONE);
        } else {
            // 显示搜索历史，使用水平流式布局
            int containerWidth = llHistoryContainer.getWidth();
            if (containerWidth == 0) {
                // 如果容器宽度为0，使用默认宽度
                containerWidth = getResources().getDisplayMetrics().widthPixels - 64; // 减去左右边距
            }
            
            LinearLayout currentRow = createNewRow();
            llHistoryContainer.addView(currentRow);
            
            int currentRowWidth = 0;
            int buttonMargin = 8; // 按钮之间的间距
            
            for (String keyword : historyList) {
                // 处理关键词长度，超过10个汉字时显示前5个汉字加省略号
                String displayText = keyword;
                if (keyword.length() > 10) {
                    displayText = keyword.substring(0, 5) + "...";
                }
                
                // 创建历史记录按钮
                android.widget.Button btnKeyword = new android.widget.Button(this);
                btnKeyword.setText(displayText);
                btnKeyword.setTextSize(14);
                btnKeyword.setTextColor(getResources().getColor(R.color.text_primary));
                btnKeyword.setBackgroundResource(R.drawable.search_history_btn_bg);
                
                // 设置按钮内边距
                int padding = 6;
                btnKeyword.setPadding(padding, padding, padding, padding);
                
                // 测量按钮宽度
                btnKeyword.measure(0, 0);
                int buttonWidth = btnKeyword.getMeasuredWidth() + buttonMargin;
                
                // 检查是否需要换行
                if (currentRowWidth + buttonWidth > containerWidth) {
                    // 创建新行
                    currentRow = createNewRow();
                    llHistoryContainer.addView(currentRow);
                    currentRowWidth = 0;
                }
                
                // 设置按钮点击事件
                btnKeyword.setOnClickListener(v -> {
                    etSearch.setText(keyword);
                    etSearch.requestFocus();
                });
                
                // 设置按钮的布局参数，添加水平间距
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMarginEnd(buttonMargin);
                btnKeyword.setLayoutParams(params);
                
                // 添加按钮到当前行
                currentRow.addView(btnKeyword);
                currentRowWidth += buttonWidth;
            }
            
            tvClearHistory.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 创建新的行布局
     * @return 新的水平LinearLayout
     */
    private LinearLayout createNewRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4, 4, 4, 4);
        return row;
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // 当窗口获得焦点时，重新加载搜索历史，以确保容器宽度正确
            loadSearchHistory();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 关闭数据库
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
    
    public int getSearchType() {
        return searchType;
    }
}
