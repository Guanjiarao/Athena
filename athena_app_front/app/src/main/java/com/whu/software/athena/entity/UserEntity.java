package com.whu.software.athena.entity;

import com.google.gson.annotations.SerializedName;

public class UserEntity {

    @SerializedName("id")
    private long id;

    @SerializedName("nickName")
    private String nickName;

    @SerializedName("icon")
    private String icon;

    @SerializedName("priority")
    private boolean priority;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isPriority() {
        return priority;
    }

    public void setPriority(boolean priority) {
        this.priority = priority;
    }
}
