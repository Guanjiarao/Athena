package com.whu.software.athena.features.chat;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.whu.software.athena.R;
import com.whu.software.athena.core.LLMClient;
import com.whu.software.athena.core.Message;
import com.whu.software.athena.core.Prompts;
import com.whu.software.athena.features.chat.history.ConversationHistoryAdapter;
import com.whu.software.athena.features.chat.history.ConversationSummary;
import com.whu.software.athena.features.chat.history.LocalConversationStore;
import com.whu.software.athena.features.privacy.RLHFMemory;

import java.util.ArrayList;
import java.util.List;

public class ChatFragment extends Fragment {

    private static final String ARG_MODE = "mode";
    private static final String LABEL_NEW_ACTION = "\u65B0\u5BF9\u8BDD";
    private static final String LABEL_NEW_CONVERSATION = "\u65B0\u7684\u8BDD\u9898";
    private static final String LABEL_HISTORY = "\u5386\u53F2\u5BF9\u8BDD";
    private static final String LABEL_HISTORY_TITLE = "\u4F1A\u8BDD\u6863\u6848";
    private static final String LABEL_HISTORY_SUBTITLE =
            "\u5207\u6362\u3001\u91CD\u547D\u540D\u6216\u5220\u9664\u4E4B\u524D\u804A\u8FC7\u7684\u8BDD\u9898";
    private static final String LABEL_HISTORY_EMPTY =
            "\u6682\u65E0\u53EF\u7528\u7684\u5386\u53F2\u8BB0\u5F55";
    private static final String MSG_WAITING =
            "\u5F53\u524D\u56DE\u7B54\u5C1A\u672A\u5B8C\u6210\uff0c\u8BF7\u7A0D\u5019";
    private static final int MAX_TITLE_LENGTH = 14;
    private static final long DRAWER_ANIM_DURATION_MS = 320L;
    private static final float MASK_ALPHA = 0.18f;

    private String mode;

    private View chatMainContainer;
    private View historyDrawerContainer;
    private View historyDrawerMask;
    private RecyclerView recyclerView;
    private RecyclerView historyRecyclerView;
    private EditText etInput;
    private ImageView btnSend;
    private TextView tvConversationStatus;
    private TextView btnHistoryConversation;
    private TextView btnNewConversation;
    private TextView tvHistoryDrawerTitle;
    private TextView tvHistoryDrawerSubtitle;
    private TextView tvHistoryEmpty;

    private MessageAdapter adapter;
    private ConversationHistoryAdapter historyAdapter;
    private final List<Message> messageList = new ArrayList<>();

    private LLMClient llmClient;
    private LocalConversationStore localConversationStore;

    private boolean isRequestInFlight = false;
    private boolean isHistoryDrawerOpen = false;
    private int requestGeneration = 0;
    private int historyDrawerWidth;
    private float historyDrawerContentShift;
    private String currentConversationId;
    private String currentConversationTitle;

    public static ChatFragment newInstance(String mode) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, mode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mode = getArguments().getString(ARG_MODE);
        }
        llmClient = new LLMClient();
        localConversationStore = new LocalConversationStore(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        chatMainContainer = view.findViewById(R.id.chatMainContainer);
        historyDrawerContainer = view.findViewById(R.id.historyDrawerContainer);
        historyDrawerMask = view.findViewById(R.id.historyDrawerMask);
        recyclerView = view.findViewById(R.id.recyclerView);
        historyRecyclerView = view.findViewById(R.id.rvHistoryList);
        etInput = view.findViewById(R.id.etInput);
        btnSend = view.findViewById(R.id.btnSend);
        tvConversationStatus = view.findViewById(R.id.tvConversationStatus);
        btnHistoryConversation = view.findViewById(R.id.btnHistoryConversation);
        btnNewConversation = view.findViewById(R.id.btnNewConversation);
        tvHistoryDrawerTitle = view.findViewById(R.id.tvHistoryDrawerTitle);
        tvHistoryDrawerSubtitle = view.findViewById(R.id.tvHistoryDrawerSubtitle);
        tvHistoryEmpty = view.findViewById(R.id.tvHistoryEmpty);

        setupViews();
        restoreConversation();
        return view;
    }

    private void setupViews() {
        adapter = new MessageAdapter(requireContext(), messageList, this::scrollToBottom);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        historyAdapter = new ConversationHistoryAdapter(new ConversationHistoryAdapter.Listener() {
            @Override
            public void onConversationSelected(ConversationSummary summary) {
                loadConversation(summary.getConversationId());
                closeHistoryDrawer(true);
            }

            @Override
            public void onConversationRenameRequested(ConversationSummary summary) {
                renameConversation(summary);
            }

            @Override
            public void onConversationDeleteRequested(ConversationSummary summary) {
                deleteConversation(summary);
            }
        });
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        historyRecyclerView.setAdapter(historyAdapter);

        btnHistoryConversation.setText(LABEL_HISTORY);
        btnNewConversation.setText(LABEL_NEW_ACTION);
        tvHistoryDrawerTitle.setText(LABEL_HISTORY_TITLE);
        tvHistoryDrawerSubtitle.setText(LABEL_HISTORY_SUBTITLE);
        tvHistoryEmpty.setText(LABEL_HISTORY_EMPTY);
        etInput.setHint("\u628A\u60F3\u8BF4\u7684\u8BDD\u544A\u8BC9\u6211...");

        etInput.addTextChangedListener(new TextWatcher() {
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

        btnSend.setOnClickListener(v -> sendMessage());
        btnHistoryConversation.setOnClickListener(v -> toggleHistoryDrawer());
        btnNewConversation.setOnClickListener(v -> startFreshConversation());
        historyDrawerMask.setOnClickListener(v -> closeHistoryDrawer(true));

        chatMainContainer.post(this::configureDrawerWidth);
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (isHistoryDrawerOpen) {
                            closeHistoryDrawer(true);
                            return;
                        }
                        setEnabled(false);
                        requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    }
                }
        );

        updateSendButtonState();
    }

    private void configureDrawerWidth() {
        int screenWidth = chatMainContainer.getWidth();
        if (screenWidth <= 0) {
            screenWidth = requireContext().getResources().getDisplayMetrics().widthPixels;
        }
        historyDrawerWidth = Math.round(screenWidth * 0.58f);
        historyDrawerContentShift = historyDrawerWidth * 0.72f;
        ViewGroup.LayoutParams params = historyDrawerContainer.getLayoutParams();
        params.width = historyDrawerWidth;
        historyDrawerContainer.setLayoutParams(params);
        historyDrawerContainer.setTranslationX(isHistoryDrawerOpen ? 0f : -historyDrawerWidth);
        chatMainContainer.setTranslationX(isHistoryDrawerOpen ? historyDrawerContentShift : 0f);
    }

    private void restoreConversation() {
        String latestConversationId = localConversationStore.getLatestConversationId(mode);
        if (TextUtils.isEmpty(latestConversationId)) {
            startNewConversation();
            return;
        }
        loadConversation(latestConversationId);
    }

    private void toggleHistoryDrawer() {
        if (isHistoryDrawerOpen) {
            closeHistoryDrawer(true);
        } else {
            openHistoryDrawer();
        }
    }

    private void openHistoryDrawer() {
        refreshHistoryList();
        if (historyDrawerWidth == 0) {
            configureDrawerWidth();
        }
        isHistoryDrawerOpen = true;
        historyDrawerMask.setVisibility(View.VISIBLE);
        historyDrawerMask.animate()
                .alpha(MASK_ALPHA)
                .setDuration(DRAWER_ANIM_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        historyDrawerContainer.animate()
                .translationX(0f)
                .setDuration(DRAWER_ANIM_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        chatMainContainer.animate()
                .translationX(historyDrawerContentShift)
                .setDuration(DRAWER_ANIM_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void closeHistoryDrawer(boolean animated) {
        isHistoryDrawerOpen = false;
        if (!animated) {
            historyDrawerMask.setAlpha(0f);
            historyDrawerMask.setVisibility(View.GONE);
            historyDrawerContainer.setTranslationX(-historyDrawerWidth);
            chatMainContainer.setTranslationX(0f);
            return;
        }
        historyDrawerMask.animate()
                .alpha(0f)
                .setDuration(DRAWER_ANIM_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> historyDrawerMask.setVisibility(View.GONE))
                .start();
        historyDrawerContainer.animate()
                .translationX(-historyDrawerWidth)
                .setDuration(DRAWER_ANIM_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        chatMainContainer.animate()
                .translationX(0f)
                .setDuration(DRAWER_ANIM_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void refreshHistoryList() {
        List<ConversationSummary> summaries = localConversationStore.getConversationSummaries(mode);
        historyAdapter.submitList(summaries, currentConversationId);
        tvHistoryEmpty.setVisibility(summaries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void loadConversation(String conversationId) {
        cancelPendingRequest();

        ConversationSummary summary = localConversationStore.getConversationSummary(conversationId);
        if (summary == null) {
            startNewConversation();
            return;
        }

        currentConversationId = summary.getConversationId();
        currentConversationTitle = summary.getTitle();
        renderConversation(localConversationStore.getMessages(conversationId));
        refreshHistoryList();
    }

    private void startNewConversation() {
        cancelPendingRequest();
        currentConversationId = null;
        currentConversationTitle = null;
        renderConversation(new ArrayList<>());
        refreshHistoryList();
    }

    private void startFreshConversation() {
        if (isHistoryDrawerOpen) {
            closeHistoryDrawer(true);
        }
        startNewConversation();
        etInput.setText("");
        etInput.requestFocus();
        scrollToBottom();
    }

    private void renameConversation(ConversationSummary summary) {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(summary.getTitle());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(requireContext())
                .setTitle("\u91CD\u547D\u540D\u4F1A\u8BDD")
                .setView(input)
                .setNegativeButton("\u53D6\u6D88", null)
                .setPositiveButton("\u786E\u5B9A", (dialog, which) -> {
                    String newTitle = input.getText().toString().trim();
                    if (TextUtils.isEmpty(newTitle)) {
                        return;
                    }
                    localConversationStore.renameConversation(summary.getConversationId(), newTitle);
                    if (TextUtils.equals(currentConversationId, summary.getConversationId())) {
                        currentConversationTitle = newTitle;
                        updateConversationStatus();
                        persistCurrentConversation();
                    }
                    refreshHistoryList();
                    Toast.makeText(
                            requireContext(),
                            "\u5DF2\u66F4\u65B0\u4F1A\u8BDD\u6807\u9898",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .show();
    }

    private void deleteConversation(ConversationSummary summary) {
        localConversationStore.deleteConversation(summary.getConversationId());
        if (TextUtils.equals(currentConversationId, summary.getConversationId())) {
            startNewConversation();
        } else {
            refreshHistoryList();
        }
        Toast.makeText(requireContext(), "\u5DF2\u5220\u9664\u4F1A\u8BDD", Toast.LENGTH_SHORT).show();
    }

    private void renderConversation(@NonNull List<Message> storedMessages) {
        messageList.clear();
        messageList.add(buildWelcomeMessage());
        messageList.addAll(storedMessages);
        adapter.notifyDataSetChanged();
        updateConversationStatus();
        scrollToBottom();
        updateSendButtonState();
    }

    private Message buildWelcomeMessage() {
        String[] display = getModeDisplay(mode);
        return new Message(MessageAdapter.ROLE_WELCOME, display[0] + "|" + display[1]);
    }

    private void sendMessage() {
        String content = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            return;
        }
        if (isRequestInFlight) {
            Toast.makeText(requireContext(), MSG_WAITING, Toast.LENGTH_SHORT).show();
            return;
        }

        ensureConversationInitialized(content);
        adapter.stopAllTyping();

        Message userMsg = new Message("user", content);
        messageList.add(userMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        scrollToBottom();
        persistCurrentConversation();

        etInput.setText("");
        isRequestInFlight = true;
        updateSendButtonState();

        List<Message> context = buildRequestContext();
        appendLoadingMessage();

        final int currentRequestId = ++requestGeneration;
        llmClient.getCompletion(requireContext(), context, false, new LLMClient.LLMCallback() {
            @Override
            public void onSuccess(String response) {
                if (getActivity() == null || currentRequestId != requestGeneration) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (currentRequestId != requestGeneration) {
                        return;
                    }
                    removeLoadingMessage();
                    isRequestInFlight = false;

                    Message aiMsg = new Message("assistant", response);
                    aiMsg.setTyping(true);
                    messageList.add(aiMsg);
                    adapter.notifyItemInserted(messageList.size() - 1);
                    scrollToBottom();

                    persistCurrentConversation();
                    refreshHistoryList();
                    updateSendButtonState();
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null || currentRequestId != requestGeneration) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (currentRequestId != requestGeneration) {
                        return;
                    }
                    removeLoadingMessage();
                    isRequestInFlight = false;
                    persistCurrentConversation();
                    refreshHistoryList();
                    updateSendButtonState();
                    Toast.makeText(
                            getContext(),
                            "\u7F51\u7EDC\u9519\u8BEF: " + error,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void appendLoadingMessage() {
        Message loadingMsg = new Message("assistant", "...");
        loadingMsg.setTyping(true);
        messageList.add(loadingMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        scrollToBottom();
    }

    private void removeLoadingMessage() {
        if (messageList.isEmpty()) {
            return;
        }
        int lastIndex = messageList.size() - 1;
        Message lastMessage = messageList.get(lastIndex);
        if (lastMessage.isTyping() && "...".equals(lastMessage.getContent())) {
            messageList.remove(lastIndex);
            adapter.notifyItemRemoved(lastIndex);
        }
    }

    private void ensureConversationInitialized(String firstQuestion) {
        if (!TextUtils.isEmpty(currentConversationId)) {
            return;
        }
        currentConversationId = localConversationStore.createConversationId();
        currentConversationTitle = buildConversationTitle(firstQuestion);
        updateConversationStatus();
    }

    private void persistCurrentConversation() {
        if (TextUtils.isEmpty(currentConversationId)) {
            return;
        }
        localConversationStore.saveConversation(
                mode,
                currentConversationId,
                currentConversationTitle,
                messageList
        );
    }

    private List<Message> buildRequestContext() {
        List<Message> context = new ArrayList<>();
        String basePrompt = Prompts.PERSONA_PROMPTS.get(mode);
        if (basePrompt != null) {
            context.add(new Message("system", basePrompt + Prompts.GENERATIVE_UI_SUFFIX));
        }

        String alignmentPrompt = RLHFMemory.buildAlignmentPrompt(requireContext());
        if (alignmentPrompt != null) {
            context.add(new Message("system", alignmentPrompt));
            long userMsgCount = messageList.stream()
                    .filter(message -> "user".equals(message.getRole()))
                    .count();
            if (userMsgCount == 1) {
                Message notice = new Message(
                        MessageAdapter.ROLE_SYSTEM_NOTICE,
                        "\u60A8\u7684\u504F\u597D\u6821\u6B63\u5DF2\u751F\u6548\uff0cAI \u4F1A\u5728\u672C\u6B21\u5BF9\u8BDD\u4E2D\u66F4\u7A33\u5B9A\u5730\u4FDD\u6301\u4E00\u81F4\u3002"
                );
                messageList.add(notice);
                adapter.notifyItemInserted(messageList.size() - 1);
            }
        }

        int start = Math.max(0, messageList.size() - 10);
        for (int i = start; i < messageList.size(); i++) {
            Message message = messageList.get(i);
            if (MessageAdapter.ROLE_WELCOME.equals(message.getRole())
                    || MessageAdapter.ROLE_SYSTEM_NOTICE.equals(message.getRole())
                    || message.isTyping()) {
                continue;
            }
            context.add(new Message(message.getRole(), message.getContent()));
        }
        return context;
    }

    private void cancelPendingRequest() {
        requestGeneration++;
        isRequestInFlight = false;
        llmClient.cancel();
        updateSendButtonState();
    }

    private void updateSendButtonState() {
        boolean enabled = !isRequestInFlight
                && !TextUtils.isEmpty(etInput.getText().toString().trim());
        btnSend.setEnabled(enabled);
        btnSend.setAlpha(enabled ? 1.0f : 0.58f);
        btnSend.setBackgroundResource(enabled
                ? R.drawable.bg_chat_send_active
                : R.drawable.bg_chat_send_disabled);
    }

    private void updateConversationStatus() {
        tvConversationStatus.setText(TextUtils.isEmpty(currentConversationTitle)
                ? LABEL_NEW_CONVERSATION
                : currentConversationTitle);
    }

    private void scrollToBottom() {
        if (messageList.isEmpty()) {
            return;
        }
        recyclerView.scrollToPosition(messageList.size() - 1);
    }

    private String buildConversationTitle(String rawQuestion) {
        String normalized = rawQuestion == null ? "" : rawQuestion.replace('\n', ' ').trim();
        if (TextUtils.isEmpty(normalized)) {
            return LABEL_NEW_CONVERSATION;
        }
        if (normalized.length() <= MAX_TITLE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_TITLE_LENGTH) + "...";
    }

    private static String[] getModeDisplay(String key) {
        switch (key) {
            case "sister":
                return new String[]{"tree", "\u6E29\u58F0\u6811\u6D1E"};
            case "pro":
                return new String[]{"profession", "\u4E13\u4E1A\u79D1\u666E"};
            case "triage":
                return new String[]{"tree", "\u5C31\u533B\u52A9\u624B"};
            case "privacy":
                return new String[]{"tree", "\u9690\u79C1\u5B88\u62A4"};
            case "monitor":
                return new String[]{"tree", "\u751F\u7269\u76D1\u6D4B"};
            default:
                return new String[]{"tree", "Athena AI"};
        }
    }

    @Override
    public void onDestroyView() {
        closeHistoryDrawer(false);
        cancelPendingRequest();
        super.onDestroyView();
    }
}
