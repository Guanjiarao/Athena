package com.whu.software.athena;

import android.content.Context;
import android.text.TextUtils;

import com.whu.software.athena.core.LLMClient;
import com.whu.software.athena.core.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy text-analysis entry point now backed by the server proxy.
 */
public class DeepSeekApiService {

    public interface OnDeepSeekResponseListener {
        void onSuccess(String report);
        void onFailure(String error);
    }

    public static void analyzeOvulationStrip(
            Context ctx,
            String imageUrl,
            OnDeepSeekResponseListener listener
    ) {
        if (listener == null) {
            return;
        }
        if (TextUtils.isEmpty(imageUrl)) {
            listener.onFailure("imageUrl must not be empty");
            return;
        }

        String prompt = "I uploaded an ovulation test strip image at: " + imageUrl + "\n\n"
                + "Please provide a careful text-only analysis including: "
                + "1) how to compare the control line and test line, "
                + "2) the likely LH level classification, "
                + "3) practical next-step suggestions for retesting and planning.";

        List<Message> messages = new ArrayList<>();
        messages.add(new Message("user", prompt));

        new LLMClient().getCompletion(ctx, messages, false, new LLMClient.LLMCallback() {
            @Override
            public void onSuccess(String response) {
                listener.onSuccess(response);
            }

            @Override
            public void onError(String error) {
                listener.onFailure(error);
            }
        });
    }
}
