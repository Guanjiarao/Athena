package com.whu.software.athena.utils;

/**
 * Keeps the app's UI mode ids isolated from the backend record API contract.
 */
public final class HealthRecordModeMapper {

    private HealthRecordModeMapper() {
    }

    public static int toApiModeType(int uiModeType) {
        if (uiModeType >= 1 && uiModeType <= 3) {
            return uiModeType - 1;
        }
        return uiModeType;
    }

    public static int toUiModeType(int apiModeType) {
        if (apiModeType >= 0 && apiModeType <= 2) {
            return apiModeType + 1;
        }
        return apiModeType;
    }
}
