

package com.nageoffer.ai.ragent.triage.rule.dao;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * triage 常用症状信号到追问槽位的规则配置。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_triage_slot_rule")
public class TriageSlotRuleDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 症状信号，如：腿疼、腹痛、咳嗽。
     */
    private String signal;

    /**
     * 槽位代码，对应 SlotCode 枚举名。
     */
    private String slotCode;

    /**
     * gap 类型，对应 QuestionGapType 枚举名。
     */
    private String gapType;

    /**
     * gap 来源，对应 QuestionGapSource 枚举名。
     */
    private String source;

    /**
     * 规则优先级。
     */
    private Integer priority;

    /**
     * 规则命中原因。
     */
    private String reason;

    /**
     * LLM 学习/配置得到的置信度，>0.6 的规则才会进入缓存和追问链路。
     */
    private Double confidence;

    /**
     * 可选项 JSON 数组，结构同 TriageClarificationData.QuestionOption。
     */
    private String optionsJson;

    /**
     * 是否启用。
     */
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
