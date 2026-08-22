package com.whu.software.athena.features.triage;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whu.software.athena.R;
import com.whu.software.athena.core.LLMClient;
import com.whu.software.athena.core.Message;
import com.whu.software.athena.features.chat.MessageAdapter;

import java.util.ArrayList;
import java.util.List;

public class TriageFragment extends Fragment {

    private RecyclerView recyclerTriage;
    private MessageAdapter adapter;
    private List<Message> messageList;
    private EditText etTriageInput;
    private Button btnTriageSend;
    private TextView tvStatus;

    private LLMClient llmClient;
    private JsonObject medicalRecord;
    private Gson gson;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        messageList = new ArrayList<>();
        llmClient = new LLMClient();
        medicalRecord = new JsonObject();
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_triage, container, false);

        recyclerTriage = view.findViewById(R.id.recyclerTriage);
        etTriageInput = view.findViewById(R.id.etTriageInput);
        btnTriageSend = view.findViewById(R.id.btnTriageSend);
        tvStatus = view.findViewById(R.id.tvStatus);

        Context context = getContext();
        if (context != null) {
            adapter = new MessageAdapter(context, messageList);
            recyclerTriage.setLayoutManager(new LinearLayoutManager(context));
            recyclerTriage.setAdapter(adapter);
        }

        updateStatus();

        if (messageList.isEmpty()) {
            messageList.add(new Message("assistant", "你好，我是分诊助手。请告诉我你不舒服的症状。"));
            adapter.notifyDataSetChanged();
        }

        btnTriageSend.setOnClickListener(v -> processInput());

        return view;
    }

    private void updateStatus() {
        if (tvStatus != null) {
            StringBuilder display = new StringBuilder();
            display.append("就医信息：\n");

            if (medicalRecord.size() == 0) {
                display.append("暂无信息");
            } else {
                for (String key : medicalRecord.keySet()) {
                    String value = "";
                    if (medicalRecord.get(key) != null && !medicalRecord.get(key).isJsonNull()) {
                        value = medicalRecord.get(key).getAsString();
                    }
                    String displayKey = translateKey(key);
                    display.append(displayKey).append("：").append(value).append("\n");
                }
            }

            tvStatus.setText(display.toString());
        }
    }

    private String translateKey(String key) {
        switch (key) {
            case "symptoms":
                return "症状";
            case "lmp":
                return "末次月经";
            case "date":
                return "日期";
            default:
                return key;
        }
    }

    private void processInput() {
        String input = etTriageInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            return;
        }

        messageList.add(new Message("user", input));
        adapter.notifyItemInserted(messageList.size() - 1);
        recyclerTriage.scrollToPosition(messageList.size() - 1);
        etTriageInput.setText("");
        btnTriageSend.setEnabled(false);

        Message loadingMsg = new Message("assistant", "...");
        loadingMsg.setTyping(true);
        messageList.add(loadingMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        recyclerTriage.scrollToPosition(messageList.size() - 1);

        extractInfo(input);
    }

    private void extractInfo(String userInput) {
        List<Message> extractionMessages = new ArrayList<>();
        extractionMessages.add(new Message("system",
                "你是一个医疗数据提取员。从用户的口语描述中提取 JSON 数据。"
                        + "仅更新用户明确提到的字段。不要猜测未提及的字段。"
                        + "输出必须是合法的 JSON 对象。需要提取的字段包括："
                        + "symptoms（症状）、lmp（末次月经日期）、date（日期）等。"));

        extractionMessages.addAll(messageList);
        extractionMessages.add(new Message("user", userInput));

        llmClient.getCompletion(requireContext(), extractionMessages, true, new LLMClient.LLMCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JsonObject updates = JsonParser.parseString(response).getAsJsonObject();
                    for (String key : updates.keySet()) {
                        medicalRecord.add(key, updates.get(key));
                    }

                    if (getActivity() == null) {
                        return;
                    }
                    getActivity().runOnUiThread(() -> {
                        updateStatus();
                        generateResponse(userInput);
                    });
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> generateResponse(userInput));
                    }
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> generateResponse(userInput));
                }
            }
        });
    }

    private void generateResponse(String userInput) {
        List<Message> context = new ArrayList<>();
        context.add(new Message("system",
                "你是分诊护士。根据病历数据和用户输入回复。当前病历：" + medicalRecord));
        context.addAll(messageList);
        context.add(new Message("user", userInput));

        llmClient.getCompletion(requireContext(), context, false, new LLMClient.LLMCallback() {
            @Override
            public void onSuccess(String response) {
                if (getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    removeLoadingMessage();
                    messageList.add(new Message("assistant", response));
                    adapter.notifyItemInserted(messageList.size() - 1);
                    recyclerTriage.scrollToPosition(messageList.size() - 1);
                    btnTriageSend.setEnabled(true);
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    removeLoadingMessage();
                    Toast.makeText(getContext(), "错误: " + error, Toast.LENGTH_SHORT).show();
                    btnTriageSend.setEnabled(true);
                });
            }
        });
    }

    private void removeLoadingMessage() {
        if (!messageList.isEmpty() && messageList.get(messageList.size() - 1).isTyping()) {
            int position = messageList.size() - 1;
            messageList.remove(position);
            adapter.notifyItemRemoved(position);
        }
    }
}
