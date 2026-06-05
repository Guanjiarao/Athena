package com.whu.software.athena.features.chat.history;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.whu.software.athena.core.Message;
import com.whu.software.athena.features.chat.MessageAdapter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class LocalConversationStore {

    private static final String PREFS_NAME = "athena_chat_history";
    private static final String KEY_CONVERSATIONS = "conversations";
    private static final Type CONVERSATION_LIST_TYPE =
            new TypeToken<List<StoredConversation>>() { }.getType();

    private final SharedPreferences sharedPreferences;
    private final Gson gson = new Gson();

    public LocalConversationStore(@NonNull Context context) {
        sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public String createConversationId() {
        return UUID.randomUUID().toString();
    }

    @NonNull
    public List<ConversationSummary> getConversationSummaries(@NonNull String mode) {
        List<ConversationSummary> summaries = new ArrayList<>();
        for (StoredConversation conversation : loadConversations()) {
            if (!TextUtils.equals(mode, conversation.mode)) {
                continue;
            }
            summaries.add(new ConversationSummary(
                    conversation.conversationId,
                    safeTitle(conversation.title),
                    conversation.lastTimeMillis,
                    ConversationTimeFormatter.formatMillis(conversation.lastTimeMillis)
            ));
        }
        summaries.sort((left, right) ->
                Long.compare(right.getSortTimeMillis(), left.getSortTimeMillis()));
        return summaries;
    }

    @Nullable
    public ConversationSummary getConversationSummary(String conversationId) {
        for (StoredConversation conversation : loadConversations()) {
            if (TextUtils.equals(conversationId, conversation.conversationId)) {
                return new ConversationSummary(
                        conversation.conversationId,
                        safeTitle(conversation.title),
                        conversation.lastTimeMillis,
                        ConversationTimeFormatter.formatMillis(conversation.lastTimeMillis)
                );
            }
        }
        return null;
    }

    @Nullable
    public String getLatestConversationId(@NonNull String mode) {
        List<ConversationSummary> summaries = getConversationSummaries(mode);
        return summaries.isEmpty() ? null : summaries.get(0).getConversationId();
    }

    @NonNull
    public List<Message> getMessages(String conversationId) {
        for (StoredConversation conversation : loadConversations()) {
            if (!TextUtils.equals(conversationId, conversation.conversationId)) {
                continue;
            }
            List<Message> messages = new ArrayList<>();
            for (StoredMessage storedMessage : conversation.messages) {
                if (TextUtils.isEmpty(storedMessage.role)
                        || TextUtils.isEmpty(storedMessage.content)) {
                    continue;
                }
                messages.add(new Message(storedMessage.role, storedMessage.content));
            }
            return messages;
        }
        return new ArrayList<>();
    }

    public void saveConversation(@NonNull String mode,
                                 @NonNull String conversationId,
                                 @NonNull String title,
                                 @NonNull List<Message> messages) {
        List<StoredConversation> conversations = loadConversations();
        StoredConversation target = null;
        for (StoredConversation conversation : conversations) {
            if (TextUtils.equals(conversationId, conversation.conversationId)) {
                target = conversation;
                break;
            }
        }
        if (target == null) {
            target = new StoredConversation();
            target.conversationId = conversationId;
            target.mode = mode;
            conversations.add(target);
        }

        target.mode = mode;
        target.title = safeTitle(title);
        target.lastTimeMillis = System.currentTimeMillis();
        target.messages = sanitizeMessages(messages);
        persist(conversations);
    }

    public void renameConversation(@NonNull String conversationId, @NonNull String newTitle) {
        List<StoredConversation> conversations = loadConversations();
        for (StoredConversation conversation : conversations) {
            if (!TextUtils.equals(conversationId, conversation.conversationId)) {
                continue;
            }
            conversation.title = safeTitle(newTitle);
            conversation.lastTimeMillis = System.currentTimeMillis();
            break;
        }
        persist(conversations);
    }

    public void deleteConversation(@NonNull String conversationId) {
        List<StoredConversation> conversations = loadConversations();
        conversations.removeIf(conversation ->
                TextUtils.equals(conversationId, conversation.conversationId));
        persist(conversations);
    }

    @NonNull
    private List<StoredMessage> sanitizeMessages(@NonNull List<Message> messages) {
        List<StoredMessage> result = new ArrayList<>();
        for (Message message : messages) {
            if (message == null
                    || TextUtils.isEmpty(message.getRole())
                    || TextUtils.isEmpty(message.getContent())) {
                continue;
            }
            if (MessageAdapter.ROLE_WELCOME.equals(message.getRole())
                    || MessageAdapter.ROLE_SYSTEM_NOTICE.equals(message.getRole())) {
                continue;
            }
            StoredMessage storedMessage = new StoredMessage();
            storedMessage.role = message.getRole();
            storedMessage.content = message.getContent();
            result.add(storedMessage);
        }
        return result;
    }

    @NonNull
    private List<StoredConversation> loadConversations() {
        String rawJson = sharedPreferences.getString(KEY_CONVERSATIONS, "[]");
        List<StoredConversation> conversations = gson.fromJson(rawJson, CONVERSATION_LIST_TYPE);
        if (conversations == null) {
            return new ArrayList<>();
        }
        for (StoredConversation conversation : conversations) {
            if (conversation.messages == null) {
                conversation.messages = new ArrayList<>();
            }
        }
        return conversations;
    }

    private void persist(@NonNull List<StoredConversation> conversations) {
        sharedPreferences.edit()
                .putString(KEY_CONVERSATIONS, gson.toJson(conversations))
                .apply();
    }

    @NonNull
    private String safeTitle(String rawTitle) {
        if (TextUtils.isEmpty(rawTitle)) {
            return "\u65B0\u5BF9\u8BDD";
        }
        String normalized = rawTitle.replace('\n', ' ').trim();
        return TextUtils.isEmpty(normalized) ? "\u65B0\u5BF9\u8BDD" : normalized;
    }

    private static final class StoredConversation {
        String conversationId;
        String title;
        long lastTimeMillis;
        String mode;
        List<StoredMessage> messages = new ArrayList<>();
    }

    private static final class StoredMessage {
        String role;
        String content;
    }
}
