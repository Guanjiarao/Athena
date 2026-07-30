

package com.nageoffer.ai.ragent.triage.session.dao;

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
 * triage 会话终态记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_triage_session_record")
public class TriageSessionRecordDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String sessionId;

    private String userId;

    private String currentState;

    private String nextAction;

    private Integer riskLevel;

    private Double riskScore;

    private String finalReply;

    private String userInputSnapshot;

    private String conversationHistoryJson;

    private String extractedSymptomsJson;

    private String missingFieldsJson;

    private String riskAssessmentJson;

    private String stateLogJson;

    private String auditTrailJson;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
