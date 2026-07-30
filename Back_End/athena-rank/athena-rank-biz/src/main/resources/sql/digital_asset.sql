CREATE TABLE IF NOT EXISTS t_digital_asset_feedback (
    id                      BIGINT       NOT NULL PRIMARY KEY COMMENT '主键ID',
    user_id                 BIGINT       NOT NULL COMMENT '提交用户ID',
    conversation_id          VARCHAR(64)  NOT NULL COMMENT 'RAG会话ID',
    message_id               VARCHAR(64)  NOT NULL COMMENT 'RAG消息ID',
    vote                    TINYINT      NOT NULL COMMENT '反馈值：1=点赞，-1=点踩',
    reason                  VARCHAR(255)          DEFAULT NULL COMMENT '反馈原因',
    comment                 VARCHAR(1024)         DEFAULT NULL COMMENT '补充说明',
    rag_message_role         VARCHAR(16)           DEFAULT NULL COMMENT 'RAG消息角色',
    rag_message_content      MEDIUMTEXT            DEFAULT NULL COMMENT 'RAG消息内容快照',
    rag_thinking_content     MEDIUMTEXT            DEFAULT NULL COMMENT 'RAG深度思考内容快照',
    rag_thinking_duration    INT                   DEFAULT NULL COMMENT 'RAG深度思考耗时秒数',
    rag_message_create_time  DATETIME              DEFAULT NULL COMMENT 'RAG消息创建时间',
    audit_status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING/APPROVED/REJECTED',
    audit_user_id            BIGINT                DEFAULT NULL COMMENT '审核人ID',
    audit_time              DATETIME              DEFAULT NULL COMMENT '审核时间',
    audit_remark            VARCHAR(1024)         DEFAULT NULL COMMENT '审核备注',
    asset_score             INT                   DEFAULT NULL COMMENT '审核通过发放数字资产数量，1~100',
    asset_record_id          BIGINT                DEFAULT NULL COMMENT '关联数字资产流水ID',
    create_time             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除 0正常 1删除',
    UNIQUE KEY uk_user_message (user_id, message_id),
    KEY idx_audit_status_create_time (audit_status, create_time),
    KEY idx_user_create_time (user_id, create_time),
    KEY idx_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字资产RAG反馈审核表';

CREATE TABLE IF NOT EXISTS t_digital_asset_account (
    id             BIGINT   NOT NULL PRIMARY KEY COMMENT '主键ID',
    user_id         BIGINT   NOT NULL COMMENT '用户ID',
    total_asset     INT      NOT NULL DEFAULT 0 COMMENT '当前数字资产总量',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT  NOT NULL DEFAULT 0 COMMENT '是否删除 0正常 1删除',
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字资产账户表';

CREATE TABLE IF NOT EXISTS t_digital_asset_record (
    id             BIGINT       NOT NULL PRIMARY KEY COMMENT '主键ID',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    source_type     VARCHAR(32)  NOT NULL COMMENT '来源类型：RAG_FEEDBACK',
    source_id       BIGINT       NOT NULL COMMENT '来源业务ID',
    change_amount   INT          NOT NULL COMMENT '变更数量',
    balance_after   INT          NOT NULL COMMENT '变更后余额',
    remark          VARCHAR(512)          DEFAULT NULL COMMENT '备注',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除 0正常 1删除',
    UNIQUE KEY uk_source (source_type, source_id),
    KEY idx_user_create_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字资产流水表';
