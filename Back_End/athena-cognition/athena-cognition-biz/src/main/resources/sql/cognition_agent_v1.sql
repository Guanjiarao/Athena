-- Athena Cognition Agent V1 schema (MySQL 8+)
-- 认知图谱提案管线存储层：与 cognition_v1.sql 的 clue/digest/topic 流程完全并行，
-- 不改动、不依赖任何已有表（外键亦不指向旧表，保证两条管线可独立演进）。
-- 枚举取值以 athena-cognition-agent 的 graph/contract 合同类为准
-- （GraphNodeType / GraphNodeStatus / GraphEdgeType / GraphOperationType /
--   GraphProposalStatus / GraphUpdateRoute / GraphTriggerType 等），列注释中标注。

-- 1. 用户认知图谱：一个用户一张图，graph_version 单调递增，作为提案的基准版本
CREATE TABLE IF NOT EXISTS `cognition_graph` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '图谱归属用户，一个用户一张图',
    `graph_id` VARCHAR(64) NOT NULL COMMENT '对外图谱 ID（graph_ 前缀），Agent 合同 PersonalCognitionGraph.graphId',
    `graph_schema_version` VARCHAR(64) NOT NULL COMMENT '图结构版本，如 personal-cognition-graph-v1',
    `graph_version` BIGINT NOT NULL DEFAULT 0 COMMENT '图谱内容版本，每次应用提案 +1，空图为 0',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_graph_user` (`user_id`),
    UNIQUE KEY `uk_cognition_graph_graph_id` (`graph_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户认知图谱（一用户一图，版本化）';

-- 2. 图谱节点：字段对齐 GraphNode 合同；evidence_ids / feedback_options 存 JSON 数组
CREATE TABLE IF NOT EXISTS `cognition_graph_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `graph_id` VARCHAR(64) NOT NULL COMMENT '所属图谱（cognition_graph.graph_id）',
    `node_id` VARCHAR(128) NOT NULL COMMENT '图内节点 ID，GraphNode.id',
    `type` VARCHAR(32) NOT NULL COMMENT 'GraphNodeType：TOPIC/SOURCE_EVIDENCE/SELF_REPORTED_FACT/PATTERN_HYPOTHESIS/OPEN_QUESTION',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'GraphNodeStatus：ACTIVE/SUPERSEDED',
    `topic_id` VARCHAR(128) NULL COMMENT '关联的认知主题外部 ID，可空',
    `title` VARCHAR(255) NULL,
    `content` TEXT NULL COMMENT '节点正文（描述/假设内容）',
    `domain` VARCHAR(32) NULL COMMENT '领域分类，如 SLEEP/MOOD/OTHER',
    `evidence_ids` JSON NULL COMMENT '证据 ID 数组（JSON），GraphNode.evidenceIds',
    `action_type` VARCHAR(32) NULL COMMENT 'GraphActionType，行动类节点才有值',
    `action_status` VARCHAR(32) NULL COMMENT 'GraphActionStatus：PENDING/COMPLETED/SKIPPED',
    `due_at` DATETIME(3) NULL COMMENT '行动截止时间',
    `feedback_options` JSON NULL COMMENT '可选反馈项数组（JSON），GraphNode.feedbackOptions',
    `version` INT NOT NULL DEFAULT 1 COMMENT '节点自身版本，每次更新 +1',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_graph_node` (`graph_id`, `node_id`),
    KEY `idx_cognition_graph_node_graph_type` (`graph_id`, `type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认知图谱节点';

-- 3. 图谱边：字段对齐 GraphEdge 合同；active=0 表示已停用（DEACTIVATE_EDGE），不物理删除
CREATE TABLE IF NOT EXISTS `cognition_graph_edge` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `graph_id` VARCHAR(64) NOT NULL COMMENT '所属图谱（cognition_graph.graph_id）',
    `edge_id` VARCHAR(128) NOT NULL COMMENT '图内边 ID，GraphEdge.id',
    `type` VARCHAR(32) NOT NULL COMMENT 'GraphEdgeType：ABOUT/GROUNDS/SUPPORTS/CHALLENGES/NEXT_STEP_FOR',
    `from_node_id` VARCHAR(128) NOT NULL COMMENT '起点节点 ID',
    `to_node_id` VARCHAR(128) NOT NULL COMMENT '终点节点 ID',
    `evidence_ids` JSON NULL COMMENT '证据 ID 数组（JSON），GraphEdge.evidenceIds',
    `active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否生效，DEACTIVATE_EDGE 置 0',
    `version` INT NOT NULL DEFAULT 1 COMMENT '边自身版本，每次更新 +1',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_graph_edge` (`graph_id`, `edge_id`),
    KEY `idx_cognition_graph_edge_graph_to` (`graph_id`, `to_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认知图谱边';

-- 4. 图谱更新提案：Agent 产出、主后端持久化；operations_json 为审计副本，
--    graph_preview_json 仅供前端展示，二者都不是应用提案时的数据来源
--    （应用时以 cognition_proposal_operation 行为准）。
--    (user_id, workflow_version, idempotency_key) 唯一约束兜底重复提交/重试。
CREATE TABLE IF NOT EXISTS `cognition_proposal` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `proposal_id` VARCHAR(64) NOT NULL COMMENT '提案 ID，GraphUpdateProposal.proposalId',
    `user_id` BIGINT NOT NULL,
    `graph_id` VARCHAR(64) NOT NULL COMMENT '目标图谱',
    `base_graph_version` BIGINT NOT NULL COMMENT '提案基于的图谱版本，应用时做乐观校验',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'GraphProposalStatus：DRAFT/READY_FOR_CONFIRMATION/ACCEPTED/KEPT_AS_KNOWLEDGE/REJECTED/STALE',
    `route` VARCHAR(32) NULL COMMENT 'GraphUpdateRoute：UPDATE_EXISTING/CREATE_BRANCH/NO_CHANGE',
    `target_topic_id` VARCHAR(128) NULL COMMENT '目标主题节点 ID（UPDATE_EXISTING 路由）',
    `evidence_ids` JSON NULL COMMENT '提案引用的证据 ID 数组',
    `change_summary` TEXT NULL COMMENT '变更摘要（给用户看的自然语言）',
    `operations_json` JSON NULL COMMENT '操作列表审计副本（完整 GraphPatchOperation 数组）',
    `graph_preview_json` JSON NULL COMMENT '应用后图谱预览，仅供展示，不参与应用逻辑',
    `requires_user_confirmation` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否需要用户确认后才能应用',
    `workflow_version` VARCHAR(64) NOT NULL COMMENT '工作流版本，如 cognition-graph-workflow-v1',
    `run_id` VARCHAR(64) NULL COMMENT '产出该提案的 Agent 运行 ID',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键，来自 GraphUpdatePreparationRequest.idempotencyKey',
    `user_decision` VARCHAR(32) NULL COMMENT '用户决定：ACCEPT/KEEP_AS_KNOWLEDGE/REJECT，未决策为 NULL',
    `decided_at` DATETIME(3) NULL COMMENT '用户决策时间，未决策为 NULL',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_proposal_proposal_id` (`proposal_id`),
    UNIQUE KEY `uk_cognition_proposal_idem` (`user_id`, `workflow_version`, `idempotency_key`),
    KEY `idx_cognition_proposal_user_created` (`user_id`, `created_at`),
    KEY `idx_cognition_proposal_graph_status` (`graph_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图谱更新提案（Agent 产出，用户确认后应用）';

-- 5. 提案操作明细：GraphPatchOperation 逐条落行，应用提案时以此为准；
--    node_json / edge_json 按 operation_type 二选一有值
CREATE TABLE IF NOT EXISTS `cognition_proposal_operation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `proposal_id` VARCHAR(64) NOT NULL COMMENT '所属提案（cognition_proposal.proposal_id）',
    `operation_index` INT NOT NULL COMMENT '操作在提案中的顺序，从 0 开始',
    `operation_type` VARCHAR(32) NOT NULL COMMENT 'GraphOperationType：ADD_NODE/UPDATE_NODE/ADD_EDGE/SUPERSEDE_NODE/DEACTIVATE_EDGE',
    `target_id` VARCHAR(128) NULL COMMENT '操作目标节点/边 ID（UPDATE/SUPERSEDE/DEACTIVATE 类）',
    `node_json` JSON NULL COMMENT '完整节点快照（ADD_NODE/UPDATE_NODE），GraphNode 序列化',
    `edge_json` JSON NULL COMMENT '完整边快照（ADD_EDGE），GraphEdge 序列化',
    `superseded_by_node_id` VARCHAR(128) NULL COMMENT 'SUPERSEDE_NODE 时指向替代节点 ID',
    `evidence_ids` JSON NULL COMMENT '该操作引用的证据 ID 数组',
    `reason` VARCHAR(500) NULL COMMENT '操作理由（Agent 给出，供审计/展示）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_proposal_operation` (`proposal_id`, `operation_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提案操作明细（应用提案的数据来源）';

-- 6. 图谱历史：每次成功应用提案追加一条，(graph_id, graph_version) 唯一；
--    operator 固定为 USER（用户确认应用）或 SYSTEM（系统自动应用）
CREATE TABLE IF NOT EXISTS `cognition_graph_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `graph_id` VARCHAR(64) NOT NULL COMMENT '所属图谱',
    `graph_version` BIGINT NOT NULL COMMENT '本次应用后的图谱版本',
    `user_id` BIGINT NOT NULL,
    `proposal_id` VARCHAR(64) NULL COMMENT '来源提案，系统自动变更为 NULL',
    `operator` VARCHAR(16) NOT NULL COMMENT '操作者类型，固定 USER 或 SYSTEM',
    `operations_json` JSON NOT NULL COMMENT '本次应用的操作列表快照',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_graph_history` (`graph_id`, `graph_version`),
    KEY `idx_cognition_graph_history_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图谱版本历史（追加式，不更新）';

-- 7. 上下文快照：一次图谱更新准备的输入快照（图版本 + 证据 + 候选），
--    供重放、审计与排查
CREATE TABLE IF NOT EXISTS `cognition_context_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `context_snapshot_id` VARCHAR(64) NOT NULL COMMENT '快照 ID，GraphUpdatePreparationRequest.contextSnapshotId',
    `user_id` BIGINT NOT NULL,
    `graph_id` VARCHAR(64) NOT NULL COMMENT '快照时的图谱',
    `graph_version` BIGINT NOT NULL COMMENT '快照时的图谱版本',
    `evidence_ids` JSON NULL COMMENT '快照包含的既有证据 ID 数组',
    `candidate_ids` JSON NULL COMMENT '本次待处理的候选证据 ID 数组',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_context_snapshot` (`context_snapshot_id`),
    KEY `idx_cognition_context_snapshot_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图谱更新上下文快照';

-- 8. Agent 任务：一次图谱更新的调度单元；
--    (user_id, workflow_version, idempotency_key) 唯一约束保证同一幂等键只建一个任务，
--    并发重复插入靠该约束兜底（捕获 DuplicateKeyException 后返回已有任务）。
--    status：PENDING/RUNNING/SUCCEEDED/NO_CHANGE/NEEDS_CONFIRMATION/FAILED/DEAD
CREATE TABLE IF NOT EXISTS `cognition_agent_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` VARCHAR(64) NOT NULL COMMENT '任务 ID（task_ 前缀外部 ID）',
    `user_id` BIGINT NOT NULL,
    `workflow_version` VARCHAR(64) NOT NULL COMMENT '工作流版本',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键，与 workflow_version 组合防重',
    `trigger_type` VARCHAR(32) NOT NULL COMMENT 'GraphTriggerType：CLUE_CREATED/USER_REQUEST/ACTION_FEEDBACK',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCEEDED/NO_CHANGE/NEEDS_CONFIRMATION/FAILED/DEAD',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `max_retry` INT NOT NULL DEFAULT 3 COMMENT '最大重试次数，超过置 DEAD',
    `last_run_id` VARCHAR(64) NULL COMMENT '最近一次运行 ID',
    `proposal_id` VARCHAR(64) NULL COMMENT '产出的提案 ID，未产出为 NULL',
    `error_code` VARCHAR(64) NULL COMMENT '最近失败的错误码',
    `error_retryable` TINYINT(1) NULL COMMENT '最近失败是否可重试',
    `payload_json` JSON NULL COMMENT '任务执行上下文快照（MQ 消费者/崩溃恢复清扫器据此重建执行上下文）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_agent_task_task_id` (`task_id`),
    UNIQUE KEY `uk_cognition_agent_task_idem` (`user_id`, `workflow_version`, `idempotency_key`),
    KEY `idx_cognition_agent_task_status_updated` (`status`, `updated_at`),
    KEY `idx_cognition_agent_task_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 图谱更新任务（幂等调度单元）';

-- 9. Agent 运行记录：一个任务可多次运行（重试），record 每次运行的最终结果与模型信息
CREATE TABLE IF NOT EXISTS `cognition_agent_run` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `run_id` VARCHAR(64) NOT NULL COMMENT '运行 ID，GraphUpdatePreparationRequest.runId',
    `task_id` VARCHAR(64) NOT NULL COMMENT '所属任务（cognition_agent_task.task_id）',
    `workflow_version` VARCHAR(64) NOT NULL COMMENT '工作流版本',
    `final_status` VARCHAR(32) NOT NULL COMMENT '运行终态：SUCCEEDED/NO_CHANGE/NEEDS_CONFIRMATION/FAILED',
    `error_code` VARCHAR(64) NULL COMMENT '失败错误码，成功为 NULL',
    `latency_ms` BIGINT NULL COMMENT '整体耗时（毫秒）',
    `model_provider` VARCHAR(64) NULL COMMENT '模型提供方',
    `model_name` VARCHAR(128) NULL COMMENT '模型名称',
    `observation_json` JSON NULL COMMENT '运行级观测数据（token 用量、耗时分解等）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_agent_run_run_id` (`run_id`),
    KEY `idx_cognition_agent_run_task` (`task_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 工作流运行记录';

-- 10. Agent 节点运行记录：一次运行中每个工作流节点的观测记录，
--     node_id 为工作流节点 ID（如 GRAPH_PATCH_ASSEMBLY），(run_id, node_id) 唯一
CREATE TABLE IF NOT EXISTS `cognition_agent_node_run` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `run_id` VARCHAR(64) NOT NULL COMMENT '所属运行（cognition_agent_run.run_id）',
    `node_id` VARCHAR(128) NOT NULL COMMENT '工作流节点 ID，如 EVIDENCE_CANONICALIZATION_AND_DEDUPLICATION',
    `node_version` VARCHAR(64) NULL COMMENT '节点版本，如 graph-patch-assembly-v1',
    `observation_json` JSON NULL COMMENT '节点级观测数据（输入输出摘要、token、耗时）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_agent_node_run` (`run_id`, `node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 工作流节点运行记录';

-- 11. Outbox 事件：图谱/提案变更后的事件外发（事务内写入、异步投递），
--     status：NEW（待投递）/SENT（已投递）/FAILED（投递失败待重试）
CREATE TABLE IF NOT EXISTS `outbox_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `event_id` VARCHAR(64) NOT NULL COMMENT '事件 ID（event_ 前缀外部 ID）',
    `user_id` BIGINT NOT NULL COMMENT '事件归属用户',
    `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型，如 GRAPH_UPDATED/PROPOSAL_READY',
    `payload_json` JSON NOT NULL COMMENT '事件载荷',
    `status` VARCHAR(16) NOT NULL DEFAULT 'NEW' COMMENT 'NEW/SENT/FAILED',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '投递失败重试次数',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `sent_at` DATETIME(3) NULL COMMENT '投递成功时间，未投递为 NULL',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbox_event_event_id` (`event_id`),
    KEY `idx_outbox_event_status_created` (`status`, `created_at`),
    KEY `idx_outbox_event_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbox 事件（事务内写入，异步外发）';
