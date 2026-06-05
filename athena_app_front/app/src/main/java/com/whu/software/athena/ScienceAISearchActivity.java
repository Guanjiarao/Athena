package com.whu.software.athena;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.whu.software.athena.core.ArticleReference;
import com.whu.software.athena.core.ArticleReferenceResolver;
import com.whu.software.athena.core.Message;
import com.whu.software.athena.core.RagSseClient;
import com.whu.software.athena.features.chat.MessageAdapter;
import com.whu.software.athena.features.chat.history.ConversationHistorySheet;
import com.whu.software.athena.features.chat.history.ConversationSummary;
import com.whu.software.athena.features.chat.history.RemoteConversationApi;

import java.util.ArrayList;
import java.util.List;

public class ScienceAISearchActivity extends AppCompatActivity {

    private static final String TAG = "ScienceAISearchActivity";
    private static final String DEFAULT_TITLE = "AI \u79D1\u666E\u52A9\u624B";
    private static final String DEFAULT_HINT =
            "\u8BF7\u8F93\u5165\u4F60\u60F3\u4E86\u89E3\u7684\u79D1\u666E\u95EE\u9898...";
    private static final String DEFAULT_EMPTY_STATE =
            "\u8F93\u5165\u95EE\u9898\u540E\uff0cAI \u56DE\u7B54\u4F1A\u5C55\u793A\u5728\u8FD9\u91CC";
    private static final String LABEL_NEW_CONVERSATION = "\u65B0\u5BF9\u8BDD";
    private static final String LABEL_HISTORY = "\u5386\u53F2";
    private static final String LABEL_HISTORY_TITLE = "\u5386\u53F2\u4F1A\u8BDD";
    private static final String MSG_WAITING =
            "\u5F53\u524D\u56DE\u7B54\u5C1A\u672A\u5B8C\u6210\uff0c\u8BF7\u7A0D\u5019";
    private static final String MSG_REFERENCE_HINT =
            "\u4E3A\u4F60\u627E\u5230\u4EE5\u4E0B\u76F8\u5173\u79D1\u666E\u6587\u7AE0\uff1A";
    private static final String MSG_EMPTY_ANSWER =
            "\u62B1\u6B49\uff0C\u6682\u672A\u83B7\u53D6\u5230\u56DE\u7B54\u5185\u5BB9\u3002";
    private static final String MSG_REQUEST_ERROR =
            "\u62B1\u6B49\uff0C\u5F53\u524D\u670D\u52A1\u6682\u65F6\u4E0D\u53EF\u7528\uff0C\u8BF7\u7A0D\u540E\u518D\u8BD5\u3002";
    private static final String MSG_REQUEST_FAILED =
            "\u8BF7\u6C42\u5931\u8D25\uff0C\u8BF7\u7A0D\u540E\u91CD\u8BD5";
    private static final String MSG_HISTORY_LOAD_FAILED =
            "\u5386\u53F2\u4F1A\u8BDD\u52A0\u8F7D\u5931\u8D25";

    private ImageView btnBack;
    private TextView tvTitle;
    private TextView tvEmptyState;
    private TextView tvConversationStatus;
    private TextView btnNewConversation;
    private TextView btnHistory;
    private RecyclerView rvConversation;
    private EditText etQuestion;
    private ImageView btnSend;

    private final List<Message> messageList = new ArrayList<>();
    private final List<ConversationSummary> conversationSummaries = new ArrayList<>();

    private MessageAdapter adapter;
    private RagSseClient ragSseClient;
    private ArticleReferenceResolver articleReferenceResolver;
    private RemoteConversationApi conversationApi;
    private ConversationHistorySheet historySheet;

    private boolean isRequestInFlight = false;
    private int pendingAssistantIndex = RecyclerView.NO_POSITION;
    private String currentConversationId;
    private String currentConversationTitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_science_ai_search);

        initViews();
        initRecyclerView();
        initData();
        bindListeners();
        applyStaticCopy();
        loadConversationList(true, false, null);
        updateSendButtonState();
        updateEmptyState();
        updateConversationLabels();
    }

    private void initViews() {
        btnBack = findViewById(R.id.iv_science_ai_back);
        tvTitle = findViewById(R.id.tv_science_ai_title);
        tvEmptyState = findViewById(R.id.tv_science_ai_empty_state);
        tvConversationStatus = findViewById(R.id.tv_science_ai_conversation_status);
        btnNewConversation = findViewById(R.id.tv_science_ai_new_chat);
        btnHistory = findViewById(R.id.tv_science_ai_history);
        rvConversation = findViewById(R.id.rv_science_ai_conversation);
        etQuestion = findViewById(R.id.et_science_ai_input);
        btnSend = findViewById(R.id.iv_science_ai_send);
    }

    private void initRecyclerView() {
        rvConversation.setLayoutManager(new LinearLayoutManager(this));
        rvConversation.setHasFixedSize(false);
    }

    private void initData() {
        adapter = new MessageAdapter(this, messageList, this::scrollToBottom);
        rvConversation.setAdapter(adapter);

        ragSseClient = new RagSseClient();
        articleReferenceResolver = new ArticleReferenceResolver();
        conversationApi = new RemoteConversationApi();
        historySheet = new ConversationHistorySheet(this,
                new ConversationHistorySheet.Listener() {
                    @Override
                    public void onConversationSelected(ConversationSummary summary) {
                        openConversation(summary);
                    }

                    @Override
                    public void onConversationRenameRequested(ConversationSummary summary,
                                                              String newTitle) {
                        renameConversation(summary, newTitle);
                    }

                    @Override
                    public void onConversationDeleteRequested(ConversationSummary summary) {
                        deleteConversation(summary);
                    }
                });
    }

    private void bindListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> submitQuestion());
        btnNewConversation.setOnClickListener(v -> startNewConversation(true));
        btnHistory.setOnClickListener(v ->
                loadConversationList(false, true, this::showHistorySheet));

        etQuestion.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        etQuestion.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitQuestion();
                return true;
            }
            return false;
        });
    }

    private void applyStaticCopy() {
        tvTitle.setText(DEFAULT_TITLE);
        tvEmptyState.setText(DEFAULT_EMPTY_STATE);
        etQuestion.setHint(DEFAULT_HINT);
        btnNewConversation.setText(LABEL_NEW_CONVERSATION);
        btnHistory.setText(LABEL_HISTORY);
    }

    private void submitQuestion() {
        String question = etQuestion.getText().toString().trim();
        if (TextUtils.isEmpty(question)) {
            return;
        }

        if (isRequestInFlight) {
            Toast.makeText(this, MSG_WAITING, Toast.LENGTH_SHORT).show();
            return;
        }

        appendUserMessage(question);
        appendAssistantPlaceholder();
        etQuestion.setText("");

        isRequestInFlight = true;
        updateSendButtonState();
        updateEmptyState();

        ragSseClient.streamQuestion(
                this,
                question,
                currentConversationId,
                false,
                new RagSseClient.Listener() {
                    @Override
                    public void onMeta(String conversationId, String taskId, String title) {
                        runOnUiThread(() -> handleConversationMeta(conversationId, title));
                    }

                    @Override
                    public void onDelta(String delta) {
                        runOnUiThread(() -> appendAssistantDelta(delta));
                    }

                    @Override
                    public void onFinish(String messageId,
                                         String title,
                                         List<ArticleReference> references) {
                        Log.d(TAG, "[ScienceAI] onFinish"
                                + " messageId=" + messageId
                                + " title=" + title
                                + " references=" + references);
                        runOnUiThread(() -> completeAssistantMessage(title, references));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> showAssistantError(error));
                    }

                    @Override
                    public void onClosed() {
                        runOnUiThread(ScienceAISearchActivity.this::finishPendingRequestState);
                    }
                }
        );
    }

    private void appendUserMessage(String question) {
        Message userMessage = new Message("user", question);
        messageList.add(userMessage);
        adapter.notifyItemInserted(messageList.size() - 1);
        scrollToBottom();
    }

    private void appendAssistantPlaceholder() {
        Message placeholder = new Message("assistant", "...");
        placeholder.setTyping(true);
        messageList.add(placeholder);
        pendingAssistantIndex = messageList.size() - 1;
        adapter.notifyItemInserted(pendingAssistantIndex);
        scrollToBottom();
    }

    private void appendAssistantDelta(String delta) {
        Message assistantMessage = getPendingAssistantMessage();
        if (assistantMessage == null || TextUtils.isEmpty(delta)) {
            return;
        }

        if (assistantMessage.isTyping()) {
            assistantMessage.setTyping(false);
            assistantMessage.setContent("");
        }

        assistantMessage.appendContent(delta);
        adapter.notifyItemChanged(pendingAssistantIndex);
        scrollToBottom();
    }

    private void handleConversationMeta(String conversationId, String title) {
        if (!TextUtils.isEmpty(conversationId)) {
            currentConversationId = conversationId;
        }
        if (!TextUtils.isEmpty(title)) {
            currentConversationTitle = title;
        }
        updateConversationLabels();
    }

    private void completeAssistantMessage(String finishTitle,
                                          List<ArticleReference> references) {
        Message assistantMessage = getPendingAssistantMessage();
        if (assistantMessage == null) {
            Log.w(TAG, "[ScienceAI] completeAssistantMessage skipped because pending message is null");
            finishPendingRequestState();
            return;
        }

        Log.d(TAG, "[ScienceAI] completeAssistantMessage"
                + " pendingIndex=" + pendingAssistantIndex
                + " finishTitle=" + finishTitle
                + " rawReferences=" + references
                + " currentContentLength="
                + (assistantMessage.getContent() == null ? 0 : assistantMessage.getContent().length()));

        assistantMessage.setTyping(false);
        if (TextUtils.isEmpty(assistantMessage.getContent())
                || "...".contentEquals(assistantMessage.getContent())) {
            if (references != null && !references.isEmpty()) {
                assistantMessage.setContent(MSG_REFERENCE_HINT);
            } else {
                assistantMessage.setContent(MSG_EMPTY_ANSWER);
            }
        }
        assistantMessage.setReferences(references);
        adapter.notifyItemChanged(pendingAssistantIndex);
        scrollToBottom();
        resolveArticleReferences(assistantMessage, pendingAssistantIndex, references);

        if (!TextUtils.isEmpty(finishTitle)) {
            currentConversationTitle = finishTitle;
            updateConversationLabels();
        }

        loadConversationList(false, false, null);
        finishPendingRequestState();
    }

    private void resolveArticleReferences(Message assistantMessage,
                                          int messageIndex,
                                          List<ArticleReference> references) {
        if (assistantMessage == null
                || messageIndex == RecyclerView.NO_POSITION
                || references == null
                || references.isEmpty()
                || articleReferenceResolver == null) {
            return;
        }

        articleReferenceResolver.resolve(this,
                references,
                new ArticleReferenceResolver.ResolverCallback() {
                    @Override
                    public void onSuccess(List<ArticleReference> resolvedReferences) {
                        Log.d(TAG, "[ScienceAI] resolve success"
                                + " messageIndex=" + messageIndex
                                + " resolvedReferences=" + resolvedReferences);
                        runOnUiThread(() -> {
                            if (isFinishing()
                                    || messageIndex < 0
                                    || messageIndex >= messageList.size()
                                    || messageList.get(messageIndex) != assistantMessage) {
                                Log.w(TAG, "[ScienceAI] resolve success dropped because message changed"
                                        + " messageIndex=" + messageIndex
                                        + " isFinishing=" + isFinishing());
                                return;
                            }
                            assistantMessage.setReferences(resolvedReferences);
                            adapter.notifyItemChanged(messageIndex);
                            scrollToBottom();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "[ScienceAI] resolve article references failed: " + error
                                + " rawReferences=" + references);
                    }
                });
    }

    private void showAssistantError(String error) {
        Message assistantMessage = getPendingAssistantMessage();
        if (assistantMessage != null) {
            assistantMessage.setTyping(false);
            assistantMessage.setContent(MSG_REQUEST_ERROR);
            assistantMessage.setReferences(null);
            adapter.notifyItemChanged(pendingAssistantIndex);
        }

        String toastText = TextUtils.isEmpty(error) ? MSG_REQUEST_FAILED : error;
        Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show();
        scrollToBottom();
        finishPendingRequestState();
    }

    private Message getPendingAssistantMessage() {
        if (pendingAssistantIndex == RecyclerView.NO_POSITION
                || pendingAssistantIndex < 0
                || pendingAssistantIndex >= messageList.size()) {
            return null;
        }
        return messageList.get(pendingAssistantIndex);
    }

    private void finishPendingRequestState() {
        isRequestInFlight = false;
        pendingAssistantIndex = RecyclerView.NO_POSITION;
        updateSendButtonState();
        updateEmptyState();
    }

    private void cancelPendingRequest() {
        if (ragSseClient != null) {
            ragSseClient.cancel();
        }
        finishPendingRequestState();
    }

    private void loadConversationList(boolean autoSelectLatest,
                                      boolean showErrors,
                                      @Nullable Runnable afterLoad) {
        conversationApi.getConversationList(this,
                new RemoteConversationApi.DataCallback<List<ConversationSummary>>() {
                    @Override
                    public void onSuccess(List<ConversationSummary> data) {
                        runOnUiThread(() -> {
                            conversationSummaries.clear();
                            conversationSummaries.addAll(data);
                            updateCurrentTitleFromList();

                            if (autoSelectLatest
                                    && TextUtils.isEmpty(currentConversationId)
                                    && messageList.isEmpty()
                                    && !conversationSummaries.isEmpty()) {
                                openConversation(conversationSummaries.get(0));
                            }

                            if (afterLoad != null) {
                                afterLoad.run();
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            if (showErrors) {
                                String message = TextUtils.isEmpty(error)
                                        ? MSG_HISTORY_LOAD_FAILED
                                        : error;
                                Toast.makeText(
                                        ScienceAISearchActivity.this,
                                        message,
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                            if (afterLoad != null) {
                                afterLoad.run();
                            }
                        });
                    }
                });
    }

    private void openConversation(ConversationSummary summary) {
        cancelPendingRequest();
        currentConversationId = summary.getConversationId();
        currentConversationTitle = summary.getTitle();
        updateConversationLabels();

        conversationApi.getConversationMessages(this,
                summary.getConversationId(),
                new RemoteConversationApi.DataCallback<List<Message>>() {
                    @Override
                    public void onSuccess(List<Message> data) {
                        runOnUiThread(() -> renderConversation(data));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(
                                ScienceAISearchActivity.this,
                                TextUtils.isEmpty(error) ? MSG_HISTORY_LOAD_FAILED : error,
                                Toast.LENGTH_SHORT
                        ).show());
                    }
                });
    }

    private void renderConversation(List<Message> data) {
        messageList.clear();
        messageList.addAll(data);
        adapter.notifyDataSetChanged();
        updateEmptyState();
        scrollToBottom();
    }

    private void showHistorySheet() {
        historySheet.show(
                LABEL_HISTORY_TITLE,
                conversationSummaries,
                currentConversationId
        );
    }

    private void renameConversation(ConversationSummary summary, String newTitle) {
        conversationApi.renameConversation(this,
                summary.getConversationId(),
                newTitle,
                new RemoteConversationApi.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            if (TextUtils.equals(currentConversationId, summary.getConversationId())) {
                                currentConversationTitle = newTitle;
                                updateConversationLabels();
                            }
                            loadConversationList(false, false, null);
                            Toast.makeText(
                                    ScienceAISearchActivity.this,
                                    "\u5DF2\u66F4\u65B0\u4F1A\u8BDD\u6807\u9898",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(
                                ScienceAISearchActivity.this,
                                TextUtils.isEmpty(error) ? MSG_REQUEST_FAILED : error,
                                Toast.LENGTH_SHORT
                        ).show());
                    }
                });
    }

    private void deleteConversation(ConversationSummary summary) {
        conversationApi.deleteConversation(this,
                summary.getConversationId(),
                new RemoteConversationApi.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            if (TextUtils.equals(currentConversationId, summary.getConversationId())) {
                                startNewConversation(false);
                            }
                            loadConversationList(false, false, null);
                            Toast.makeText(
                                    ScienceAISearchActivity.this,
                                    "\u5DF2\u5220\u9664\u4F1A\u8BDD",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(
                                ScienceAISearchActivity.this,
                                TextUtils.isEmpty(error) ? MSG_REQUEST_FAILED : error,
                                Toast.LENGTH_SHORT
                        ).show());
                    }
                });
    }

    private void startNewConversation(boolean fromUserAction) {
        cancelPendingRequest();
        currentConversationId = null;
        currentConversationTitle = null;
        messageList.clear();
        adapter.notifyDataSetChanged();
        updateConversationLabels();
        updateEmptyState();
        if (fromUserAction) {
            Toast.makeText(this, "\u5DF2\u521B\u5EFA\u65B0\u5BF9\u8BDD", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCurrentTitleFromList() {
        if (TextUtils.isEmpty(currentConversationId)) {
            updateConversationLabels();
            return;
        }
        for (ConversationSummary summary : conversationSummaries) {
            if (TextUtils.equals(currentConversationId, summary.getConversationId())) {
                currentConversationTitle = summary.getTitle();
                break;
            }
        }
        updateConversationLabels();
    }

    private void updateConversationLabels() {
        String activeTitle = TextUtils.isEmpty(currentConversationTitle)
                ? DEFAULT_TITLE
                : currentConversationTitle;
        String statusTitle = TextUtils.isEmpty(currentConversationTitle)
                ? LABEL_NEW_CONVERSATION
                : currentConversationTitle;
        tvTitle.setText(activeTitle);
        tvConversationStatus.setText(statusTitle);
    }

    private void updateSendButtonState() {
        boolean enabled = !isRequestInFlight
                && !TextUtils.isEmpty(etQuestion.getText().toString().trim());
        btnSend.setEnabled(enabled);
        btnSend.setAlpha(enabled ? 1.0f : 0.7f);
        btnSend.setBackgroundResource(enabled
                ? R.drawable.bg_btn_send_active
                : R.drawable.bg_btn_send_disabled);
    }

    private void updateEmptyState() {
        tvEmptyState.setVisibility(messageList.isEmpty()
                ? android.view.View.VISIBLE
                : android.view.View.GONE);
    }

    private void scrollToBottom() {
        if (messageList.isEmpty()) {
            return;
        }
        rvConversation.scrollToPosition(messageList.size() - 1);
    }

    @Override
    protected void onDestroy() {
        if (historySheet != null) {
            historySheet.dismiss();
        }
        if (ragSseClient != null) {
            ragSseClient.cancel();
        }
        if (articleReferenceResolver != null) {
            articleReferenceResolver.cancel();
        }
        super.onDestroy();
    }
}
