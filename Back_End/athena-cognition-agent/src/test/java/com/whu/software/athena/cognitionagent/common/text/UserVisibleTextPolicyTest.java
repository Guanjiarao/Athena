package com.whu.software.athena.cognitionagent.common.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserVisibleTextPolicyTest {

    @Test
    void pureChinesePasses() {
        assertTrue(UserVisibleTextPolicy.isUserVisibleChinese(
                "用户反馈这次观察的情况出现了。"));
        assertTrue(UserVisibleTextPolicy.isUserVisibleChinese("记录一次相关的身体变化"));
    }

    @Test
    void pureEnglishFails() {
        assertFalse(UserVisibleTextPolicy.isUserVisibleChinese(
                "The user reported that the planned observation occurred."));
        assertFalse(UserVisibleTextPolicy.isUserVisibleChinese(
                "Record one related body change every day this week."));
    }

    @Test
    void chineseWithFewEnglishTermsPasses() {
        assertTrue(UserVisibleTextPolicy.isUserVisibleChinese(
                "建议每天记录 sleep 时长和 HRV 变化，并留意 REM 占比。"));
    }

    @Test
    void nullAndBlankPass() {
        assertTrue(UserVisibleTextPolicy.isUserVisibleChinese(null));
        assertTrue(UserVisibleTextPolicy.isUserVisibleChinese(""));
        assertTrue(UserVisibleTextPolicy.isUserVisibleChinese("   "));
    }

    @Test
    void latinCountBoundaryAtTwenty() {
        // exactly 20 latin letters, no CJK: tolerated
        assertTrue(UserVisibleTextPolicy.isUserVisibleChinese("abcdefghijklmnopqrst"));
        // 21 latin letters, no CJK: rejected
        assertFalse(UserVisibleTextPolicy.isUserVisibleChinese("abcdefghijklmnopqrstu"));
    }

    @Test
    void latinToCjkRatioBoundary() {
        String fortyTwoHan = "观察记录".repeat(10) + "身体"; // 42 CJK characters
        // 21 latin vs 42 CJK: ratio exactly one half, still acceptable
        assertTrue(UserVisibleTextPolicy.isUserVisibleChinese(
                "abcdefghijklmnopqrstu" + fortyTwoHan));
        // 22 latin vs 42 CJK: more than half, rejected
        assertFalse(UserVisibleTextPolicy.isUserVisibleChinese(
                "abcdefghijklmnopqrstuv" + fortyTwoHan));
    }
}
