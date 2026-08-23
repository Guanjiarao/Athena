package com.whu.software.athena.cognition;

import com.google.gson.Gson;
import com.whu.software.athena.cognition.CognitionModels.ActionFeedbackResult;
import com.whu.software.athena.cognition.CognitionModels.DigestDecision;
import com.whu.software.athena.cognition.CognitionModels.Home;
import com.whu.software.athena.cognition.CognitionModels.HomeSummaryState;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Pure JVM checks for the stable parts of Cognition Contract V1. */
public class CognitionContractTest {

    @Test public void allBusinessErrorCodesHaveStableUserMessages() {
        String[] codes = {
                "COGNITION_INVALID_ARGUMENT", "COGNITION_NOT_FOUND",
                "COGNITION_STATE_CONFLICT", "COGNITION_VERSION_CONFLICT",
                "COGNITION_CLUE_IN_USE", "COGNITION_NO_VALID_EVIDENCE",
                "COGNITION_TASK_RUNNING", "COGNITION_GENERATION_FAILED"
        };
        for (String code : codes) {
            String message = CognitionErrorMessages.toUserMessage(200, 409, code, null);
            assertFalse(code, message.isEmpty());
            assertFalse(code, "请求失败，请稍后重试".equals(message));
        }
    }

    @Test public void gatewayTokenFailureIsTreatedAsAuthenticationFailure() {
        assertTrue(CognitionErrorMessages.isAuthenticationFailure(
                200, 500, "未能读取到有效 token"));
        assertEquals("登录已失效，请重新登录",
                CognitionErrorMessages.toUserMessage(200, 500, null, "未能读取到有效 token"));
    }

    @Test public void unrelatedServerFailureDoesNotLookLikeAuthenticationFailure() {
        assertFalse(CognitionErrorMessages.isAuthenticationFailure(200, 500, "生成失败"));
    }

    @Test public void contractKeepsAllDecisionAndFeedbackBranches() {
        assertEquals(EnumSet.of(DigestDecision.ACCEPT_AS_TOPIC,
                        DigestDecision.KEEP_AS_KNOWLEDGE, DigestDecision.REJECT),
                EnumSet.allOf(DigestDecision.class));
        assertEquals(EnumSet.of(ActionFeedbackResult.OCCURRED,
                        ActionFeedbackResult.NOT_OCCURRED, ActionFeedbackResult.UNCERTAIN,
                        ActionFeedbackResult.SKIPPED),
                EnumSet.allOf(ActionFeedbackResult.class));
    }

    @Test public void contractKeepsAllNineHomeStates() {
        assertEquals(9, HomeSummaryState.values().length);
    }

    @Test public void gsonAcceptsMissingNullableHomeFields() {
        Home home = new Gson().fromJson(
                "{\"summaryState\":\"EMPTY\",\"headline\":\"从一条身体线索开始\"}", Home.class);
        assertEquals(HomeSummaryState.EMPTY, home.summaryState);
        assertEquals("从一条身体线索开始", home.headline);
        assertNull(home.activeTopic);
        assertNull(home.nextAction);
        assertNull(home.latestInsight);
    }
}
