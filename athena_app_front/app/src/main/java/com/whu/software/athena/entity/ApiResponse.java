package com.whu.software.athena.entity;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * 通用接口返回包装。
 */
public class ApiResponse<T> implements Serializable {

    @SerializedName("code")
    private int code;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private T data;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
