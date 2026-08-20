package athena.cognition.biz.domain;

/**
 * Business error with a stable contract error code (section 12).
 * The semantic code (400/401/404/409/500) goes to the outer Result.code;
 * the stable errorCode goes to data.errorCode.
 */
public class CognitionException extends RuntimeException {

    // Section 12 stable business error codes
    public static final String INVALID_ARGUMENT = "COGNITION_INVALID_ARGUMENT";
    public static final String NOT_FOUND = "COGNITION_NOT_FOUND";
    public static final String STATE_CONFLICT = "COGNITION_STATE_CONFLICT";
    public static final String VERSION_CONFLICT = "COGNITION_VERSION_CONFLICT";
    public static final String CLUE_IN_USE = "COGNITION_CLUE_IN_USE";
    public static final String NO_VALID_EVIDENCE = "COGNITION_NO_VALID_EVIDENCE";
    public static final String TASK_RUNNING = "COGNITION_TASK_RUNNING";
    public static final String GENERATION_FAILED = "COGNITION_GENERATION_FAILED";

    private final String errorCode;
    private final int semanticCode;
    private final String objectId;
    private final String currentStatus;

    public CognitionException(String errorCode, int semanticCode, String message,
                              String objectId, String currentStatus) {
        super(message);
        this.errorCode = errorCode;
        this.semanticCode = semanticCode;
        this.objectId = objectId;
        this.currentStatus = currentStatus;
    }

    public static CognitionException invalidArgument(String message) {
        return new CognitionException(INVALID_ARGUMENT, 400, message, null, null);
    }

    public static CognitionException unauthenticated() {
        return new CognitionException(null, 401, "请先登录", null, null);
    }

    public static CognitionException notFound() {
        return new CognitionException(NOT_FOUND, 404, "对象不存在", null, null);
    }

    public static CognitionException stateConflict(String message, String objectId, String currentStatus) {
        return new CognitionException(STATE_CONFLICT, 409, message, objectId, currentStatus);
    }

    public static CognitionException versionConflict(String objectId, String currentVersion) {
        return new CognitionException(VERSION_CONFLICT, 409, "数据已被更新，请刷新后重试", objectId, currentVersion);
    }

    public static CognitionException clueInUse(String clueId) {
        return new CognitionException(CLUE_IN_USE, 409, "该线索已进入整理草稿，不能撤销", clueId, null);
    }

    public static CognitionException noValidEvidence() {
        return new CognitionException(NO_VALID_EVIDENCE, 400, "没有可用于整理的相关线索", null, null);
    }

    public static CognitionException taskRunning() {
        return new CognitionException(TASK_RUNNING, 409, "相同主题已有正在进行的整理任务", null, null);
    }

    public static CognitionException generationFailed(String objectId) {
        return new CognitionException(GENERATION_FAILED, 500, "整理暂时失败，可以稍后重试", objectId, null);
    }

    public String errorCode() {
        return errorCode;
    }

    public int semanticCode() {
        return semanticCode;
    }

    public String objectId() {
        return objectId;
    }

    public String currentStatus() {
        return currentStatus;
    }
}
