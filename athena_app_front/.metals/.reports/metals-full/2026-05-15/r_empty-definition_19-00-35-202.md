error id: file:///D:/aa/athena-zyj%20(2)/athena_front/app/src/main/java/com/whu/software/athena/config/ApiConfig.java:java/lang/String#
file:///D:/aa/athena-zyj%20(2)/athena_front/app/src/main/java/com/whu/software/athena/config/ApiConfig.java
empty definition using pc, found symbol in pc: java/lang/String#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1747
uri: file:///D:/aa/athena-zyj%20(2)/athena_front/app/src/main/java/com/whu/software/athena/config/ApiConfig.java
text:
```scala
package com.whu.software.athena.config;

public class ApiConfig {

    private static final String HOST = "172.20.10.14";
    public static final String BASE_URL = "http://" + HOST + ":13715/athena/";

    public static final String API_BLOG_LIST = BASE_URL + "blog/list";
    public static final String API_BLOG_LIST_BY_TYPE = BASE_URL + "blog/listByTypeId";
    public static final String API_BLOG_LIST_BY_CHANNEL = BASE_URL + "blog/listBychannelId";
    public static final String API_BLOG_DETAIL = BASE_URL + "blog/Detail";
    public static final String API_NOTE_BASIC_LIST_BY_NOTE_IDS =
            BASE_URL + "blog/noteBasic/listByNoteIds";
    public static final String API_BLOG_SUBMIT = BASE_URL + "blog/submit";
    public static final String API_BLOG_LIKE_STATUS = BASE_URL + "blog/isLike";
    public static final String API_BLOG_LIKE_TOGGLE = BASE_URL + "blog/like";
    public static final String API_BLOG_COLLECT_STATUS = BASE_URL + "blog/isCollect";
    public static final String API_BLOG_COLLECT_TOGGLE = BASE_URL + "blog/collect";
    public static final String API_BLOG_LIKE_LIST = BASE_URL + "blog/likeList";
    public static final String API_BLOG_COLLECT_LIST = BASE_URL + "blog/collectList";
    public static final String API_BLOG_MY_LIST = BASE_URL + "blog/myList";
    public static final String API_BLOG_VIEW_HISTORY = BASE_URL + "blog/viewHistory";

    public static final String API_RAG_STREAM = BASE_URL + "rag/rag/v3/chat";
    public static final String API_CONVERSATIONS = BASE_URL + "rag/conversations";
    public static final String API_TRIAGE_ANALYZE = BASE_URL + "rag/triage/analyze";
    public static final String API_TRIAGE_LLM_COMPLETE = BASE_URL + "rag/triage/llm/complete";
    public static final St@@ring API_TRIAGE_VISION_ANALYZE = BASE_URL + "rag/triage/vision/analyze";

    public static final String API_USER_GET_INFO = BASE_URL + "user/getUserInfo";
    public static final String API_USER_UPDATE_ICON = BASE_URL + "user/updateIcon";
    public static final String API_USER_UPDATE_NICKNAME = BASE_URL + "user/updateNickName";
    public static final String API_USER_UPDATE_BIRTHDAY = BASE_URL + "user/updateBirthday";
    public static final String API_USER_FOLLOW_STATUS = BASE_URL + "relation/isFollow";
    public static final String API_USER_FOLLOW = BASE_URL + "relation/follow";
    public static final String API_USER_UNFOLLOW = BASE_URL + "relation/unfollow";
    public static final String API_USER_FOLLOW_COUNT = BASE_URL + "relation/followCount";
    public static final String API_USER_FAN_COUNT = BASE_URL + "relation/fanCount";
    public static final String API_FOLLOWING_LIST = BASE_URL + "relation/followList";
    public static final String API_FOLLOWER_LIST = BASE_URL + "relation/fanList";

    public static final String API_COMMENT_LIST_PAGE = BASE_URL + "comment/listPage";
    public static final String API_COMMENT_PUBLISH = BASE_URL + "comment/publish";
    public static final String API_COMMENT_EXTEND = BASE_URL + "comment/extend";

    public static final String API_FILE_UPLOAD = BASE_URL + "file/upload";

    public static final String API_LOGIN = BASE_URL + "login";
    public static final String API_LOGIN_CODE = BASE_URL + "login/code";
    public static final String API_REGISTER = BASE_URL + "register";

    public static final String API_MENSTRUATION_LATEST = BASE_URL + "menstruation/latest";
    public static final String API_MENSTRUATION_START = BASE_URL + "menstruation/start";
    public static final String API_MENSTRUATION_END = BASE_URL + "menstruation/end";

    public static final String API_RECORD_DETAIL = BASE_URL + "record/detail";
    public static final String API_RECORD_SAVE = BASE_URL + "record";
    public static final String API_RECORD_UPDATE = BASE_URL + "record/update";
    public static final String API_RECORD_DELETE = BASE_URL + "record/delete";
    public static final String API_RECORD_MARKS = BASE_URL + "record/marks";

    public static final String MOCK_COVER_URL =
            "https://xiaoxiaolanfeng-java-ai.oss-cn-beijing.aliyuncs.com/9977fb324f344996997b48081aecfae2.jpg";

    public static final int CONNECT_TIMEOUT = 15;
    public static final int READ_TIMEOUT = 15;
    public static final int WRITE_TIMEOUT = 15;
    public static final int DEBOUNCE_DELAY = 500;

    public static final String API_QWEN_VL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    public static final String QWEN_API_KEY = "sk-dc65179484174c2494d247185601f167";
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: java/lang/String#