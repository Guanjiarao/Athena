package com.whu.software.athena.entity;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CommentResponse {

    @SerializedName("code")
    private int code;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private List<CommentBean> data;

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public List<CommentBean> getData() { return data; }

    public boolean isSuccess() {
        return code == 200;
    }
}