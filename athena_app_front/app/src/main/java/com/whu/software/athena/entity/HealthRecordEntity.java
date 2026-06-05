package com.whu.software.athena.entity;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * 健康模块单条记录实体。
 *
 * <p>字段与后端 /athena/record/* 接口对齐：
 * <ul>
 *   <li>{@code id}           — 记录主键</li>
 *   <li>{@code userId}       — 用户 ID</li>
 *   <li>{@code recordDate}   — 记录日期（yyyy-MM-dd）</li>
 *   <li>{@code modeType}     — 健康模式（1=经期 / 2=备孕 / 3=怀孕）</li>
 *   <li>{@code recordItemId} — 记录项目编号（对应列表中的每个 Action Item）</li>
 *   <li>{@code recordValue}  — 记录内容（JSON 字符串或简单文本）</li>
 * </ul>
 */
public class HealthRecordEntity implements Serializable {

    @SerializedName("id")
    private int id;

    @SerializedName("userId")
    private int userId;

    @SerializedName("recordDate")
    private String recordDate;

    @SerializedName("modeType")
    private int modeType;

    @SerializedName("recordItemId")
    private int recordItemId;

    @SerializedName("recordValue")
    private String recordValue;

    public HealthRecordEntity() {}

    public HealthRecordEntity(int userId,
                              String recordDate,
                              int modeType,
                              int recordItemId,
                              String recordValue) {
        this.userId       = userId;
        this.recordDate   = recordDate;
        this.modeType     = modeType;
        this.recordItemId = recordItemId;
        this.recordValue  = recordValue;
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public int    getId()           { return id; }
    public int    getUserId()       { return userId; }
    public String getRecordDate()   { return recordDate; }
    public int    getModeType()     { return modeType; }
    public int    getRecordItemId() { return recordItemId; }
    public String getRecordValue()  { return recordValue; }

    // ── Setters ─────────────────────────────────────────────────────────────

    public void setId(int id)                   { this.id = id; }
    public void setUserId(int userId)           { this.userId = userId; }
    public void setRecordDate(String d)         { this.recordDate = d; }
    public void setModeType(int modeType)       { this.modeType = modeType; }
    public void setRecordItemId(int itemId)     { this.recordItemId = itemId; }
    public void setRecordValue(String value)    { this.recordValue = value; }
}
