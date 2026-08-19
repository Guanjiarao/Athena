package athena.cognition.biz.repository;

import athena.cognition.biz.domain.CognitionModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class CognitionJdbcRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public CognitionJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public long insertClue(long userId, ClueCreateRequest request) {
        String sql = """
                INSERT INTO tb_cognition_clue
                (user_id, clue_type, mark_intent, relation_detail, desired_help, article_id, article_title,
                 source_name, excerpt, question_type, question_text, body_record_id, occurred_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """;
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, request.clueType().name());
            ps.setString(3, enumName(request.markIntent()));
            ps.setString(4, enumName(request.relationDetail()));
            ps.setString(5, request.desiredHelp());
            ps.setString(6, request.articleId());
            ps.setString(7, request.articleTitle());
            ps.setString(8, request.sourceName());
            ps.setString(9, request.excerpt());
            ps.setString(10, request.questionType());
            ps.setString(11, request.questionText());
            if (request.bodyRecordId() == null) ps.setObject(12, null); else ps.setLong(12, request.bodyRecordId());
            ps.setTimestamp(13, Timestamp.from(request.occurredAt()));
            return ps;
        }, key);
        return requiredKey(key);
    }

    public Optional<ClueView> findClue(long userId, long clueId) {
        return first(jdbc.query("SELECT * FROM tb_cognition_clue WHERE user_id=? AND id=?", CLUE_ROW, userId, clueId));
    }

    public List<ClueView> findClues(long userId, List<Long> clueIds) {
        if (clueIds.isEmpty()) return List.of();
        return namedJdbc.query("SELECT * FROM tb_cognition_clue WHERE user_id=:userId AND id IN (:ids) ORDER BY id",
                new MapSqlParameterSource("userId", userId).addValue("ids", clueIds), CLUE_ROW);
    }

    public List<ClueView> listClues(long userId, ClueSection section, Long cursor, int limit) {
        String condition = switch (section) {
            case PENDING -> "status IN ('PENDING','IN_DIGEST') AND (mark_intent IS NULL OR mark_intent <> 'QUESTION')";
            case ORGANIZED -> "status IN ('ORGANIZED','KNOWLEDGE_ONLY')";
            case QUESTIONS -> "mark_intent='QUESTION' AND status <> 'WITHDRAWN'";
        };
        long before = cursor == null ? Long.MAX_VALUE : cursor;
        return jdbc.query("SELECT * FROM tb_cognition_clue WHERE user_id=? AND id<? AND " + condition + " ORDER BY id DESC LIMIT ?",
                CLUE_ROW, userId, before, limit);
    }

    public long insertTask(long userId) {
        String sql = "INSERT INTO tb_cognition_digest_task (user_id,status,generator_type) VALUES (?,'PENDING','FIXED_V1')";
        return insertAndReturnKey(sql, userId);
    }

    public void linkTaskClues(long userId, long taskId, List<Long> clueIds) {
        jdbc.batchUpdate("INSERT INTO tb_cognition_digest_task_clue (user_id,digest_task_id,clue_id) VALUES (?,?,?)",
                clueIds, clueIds.size(), (ps, clueId) -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, taskId);
                    ps.setLong(3, clueId);
                });
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId).addValue("ids", clueIds);
        namedJdbc.update("UPDATE tb_cognition_clue SET status='IN_DIGEST', version=version+1 WHERE user_id=:userId AND id IN (:ids) AND status='PENDING'", params);
    }

    public void markTaskRunning(long userId, long taskId) {
        jdbc.update("UPDATE tb_cognition_digest_task SET status='RUNNING',attempt_count=attempt_count+1,started_at=?,failure_code=NULL,failure_message=NULL,version=version+1 WHERE user_id=? AND id=?",
                Timestamp.from(Instant.now()), userId, taskId);
    }

    public void markTaskSucceeded(long userId, long taskId) {
        jdbc.update("UPDATE tb_cognition_digest_task SET status='SUCCEEDED',finished_at=?,version=version+1 WHERE user_id=? AND id=?",
                Timestamp.from(Instant.now()), userId, taskId);
    }

    public void markTaskFailed(long userId, long taskId, String failureCode, String safeMessage) {
        jdbc.update("UPDATE tb_cognition_digest_task SET status='FAILED',failure_code=?,failure_message=?,finished_at=?,version=version+1 WHERE user_id=? AND id=?",
                failureCode, safeMessage, Timestamp.from(Instant.now()), userId, taskId);
    }

    public Optional<TaskRow> findTask(long userId, long taskId) {
        return first(jdbc.query("""
                        SELECT t.*, d.id AS digest_id FROM tb_cognition_digest_task t
                        LEFT JOIN tb_cognition_digest d ON d.digest_task_id=t.id
                        WHERE t.user_id=? AND t.id=?
                        """, TASK_ROW, userId, taskId));
    }

    public List<ClueView> findTaskClues(long userId, long taskId) {
        return jdbc.query("""
                SELECT c.* FROM tb_cognition_clue c
                JOIN tb_cognition_digest_task_clue tc ON tc.clue_id=c.id
                WHERE tc.user_id=? AND tc.digest_task_id=? ORDER BY c.id
                """, CLUE_ROW, userId, taskId);
    }

    public long insertDigest(long userId, long taskId, athena.cognition.biz.generator.DigestGenerator.GeneratedDigest generated) {
        String sql = """
                INSERT INTO tb_cognition_digest
                (user_id,digest_task_id,status,title,common_point,possible_link,uncertainty,suggested_action,generator_type,generator_version)
                VALUES (?,?,'PENDING_CONFIRMATION',?,?,?,?,?,'FIXED_V1',?)
                """;
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setLong(2, taskId);
            ps.setString(3, generated.title());
            ps.setString(4, generated.commonPoint());
            ps.setString(5, generated.possibleLink());
            ps.setString(6, generated.uncertainty());
            ps.setString(7, generated.suggestedAction());
            ps.setString(8, generated.generatorVersion());
            return ps;
        }, key);
        return requiredKey(key);
    }

    public void insertEvidence(long userId, long digestId, List<ClueView> clues) {
        jdbc.batchUpdate("INSERT INTO tb_cognition_evidence (user_id,digest_id,clue_id,evidence_level,evidence_role) VALUES (?,?,?, ?,?)",
                clues, clues.size(), (ps, clue) -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, digestId);
                    ps.setLong(3, clue.clueId());
                    ps.setString(4, clue.clueType() == ClueType.BODY_RECORD ? "HIGH" : "LOW");
                    ps.setString(5, clue.markIntent() == MarkIntent.QUESTION ? "QUESTION_CONTEXT" : "OBSERVATION_CONTEXT");
                });
    }

    public Optional<DigestRow> findDigest(long userId, long digestId, boolean forUpdate) {
        String suffix = forUpdate ? " FOR UPDATE" : "";
        return first(jdbc.query("SELECT * FROM tb_cognition_digest WHERE user_id=? AND id=?" + suffix, DIGEST_ROW, userId, digestId));
    }

    public Optional<DigestRow> findDigestByTask(long userId, long taskId) {
        return first(jdbc.query("SELECT * FROM tb_cognition_digest WHERE user_id=? AND digest_task_id=?", DIGEST_ROW, userId, taskId));
    }

    public List<DigestRow> listDigests(long userId, DigestStatus status, Long cursor, int limit) {
        long before = cursor == null ? Long.MAX_VALUE : cursor;
        return jdbc.query("SELECT * FROM tb_cognition_digest WHERE user_id=? AND status=? AND id<? ORDER BY id DESC LIMIT ?",
                DIGEST_ROW, userId, status.name(), before, limit);
    }

    public List<EvidenceView> findDigestEvidence(long userId, long digestId) {
        return jdbc.query("SELECT id,clue_id,evidence_level,evidence_role FROM tb_cognition_evidence WHERE user_id=? AND digest_id=? ORDER BY id",
                (rs, row) -> new EvidenceView(rs.getLong("id"), rs.getLong("clue_id"), rs.getString("evidence_level"), rs.getString("evidence_role")),
                userId, digestId);
    }

    public long acceptDigest(long userId, DigestRow digest) {
        long topicId = insertAndReturnKey("""
                INSERT INTO tb_cognition_topic
                (user_id,source_digest_id,title,summary,uncertainty,maturity,progress,risk_status)
                VALUES (?,?,?,?,?,'CLUE','FOLLOWING','NONE')
                """, userId, digest.id(), digest.title(), digest.possibleLink(), digest.uncertainty());
        long actionId = insertAndReturnKey("""
                INSERT INTO tb_cognition_action (user_id,topic_id,title,instruction,status,due_at)
                VALUES (?,?,?,?,'PENDING',?)
                """, userId, topicId, "完成一次观察记录", digest.suggestedAction(), Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS)));
        jdbc.update("UPDATE tb_cognition_digest SET status='ACCEPTED',decided_at=?,version=version+1 WHERE user_id=? AND id=?",
                Timestamp.from(Instant.now()), userId, digest.id());
        jdbc.update("UPDATE tb_cognition_evidence SET topic_id=? WHERE user_id=? AND digest_id=?", topicId, userId, digest.id());
        jdbc.update("""
                UPDATE tb_cognition_clue c JOIN tb_cognition_evidence e ON e.clue_id=c.id
                SET c.status='ORGANIZED',c.version=c.version+1 WHERE e.user_id=? AND e.digest_id=? AND c.user_id=?
                """, userId, digest.id(), userId);
        insertTopicVersion(userId, topicId, 1, digest.possibleLink(), digest.uncertainty(),
                CognitionMaturity.CLUE, TopicProgress.FOLLOWING, RiskStatus.NONE, "DIGEST_ACCEPTED");
        insertDecision(userId, digest.id(), DigestDecision.ACCEPT_TOPIC, null, topicId);
        return actionId;
    }

    public void saveDigestAsKnowledge(long userId, DigestRow digest, String reasonCode) {
        jdbc.update("UPDATE tb_cognition_digest SET status='SAVED_KNOWLEDGE',decided_at=?,version=version+1 WHERE user_id=? AND id=?",
                Timestamp.from(Instant.now()), userId, digest.id());
        updateDigestCluesStatus(userId, digest.id(), ClueStatus.KNOWLEDGE_ONLY);
        insertDecision(userId, digest.id(), DigestDecision.SAVE_KNOWLEDGE, reasonCode, null);
    }

    public void rejectDigest(long userId, DigestRow digest, String reasonCode) {
        jdbc.update("UPDATE tb_cognition_digest SET status='REJECTED',decided_at=?,version=version+1 WHERE user_id=? AND id=?",
                Timestamp.from(Instant.now()), userId, digest.id());
        updateDigestCluesStatus(userId, digest.id(), ClueStatus.REJECTED);
        insertDecision(userId, digest.id(), DigestDecision.REJECT, reasonCode, null);
    }

    private void updateDigestCluesStatus(long userId, long digestId, ClueStatus status) {
        jdbc.update("""
                UPDATE tb_cognition_clue c JOIN tb_cognition_evidence e ON e.clue_id=c.id
                SET c.status=?,c.version=c.version+1 WHERE e.user_id=? AND e.digest_id=? AND c.user_id=?
                """, status.name(), userId, digestId, userId);
    }

    private void insertDecision(long userId, long digestId, DigestDecision decision, String reasonCode, Long topicId) {
        jdbc.update("INSERT INTO tb_cognition_digest_decision (user_id,digest_id,decision,reason_code,topic_id) VALUES (?,?,?,?,?)",
                userId, digestId, decision.name(), reasonCode, topicId);
    }

    public Optional<TopicRow> findTopic(long userId, long topicId, boolean forUpdate) {
        return first(jdbc.query("SELECT * FROM tb_cognition_topic WHERE user_id=? AND id=?" + (forUpdate ? " FOR UPDATE" : ""),
                TOPIC_ROW, userId, topicId));
    }

    public Optional<TopicRow> findTopicByDigest(long userId, long digestId) {
        return first(jdbc.query("SELECT * FROM tb_cognition_topic WHERE user_id=? AND source_digest_id=?", TOPIC_ROW, userId, digestId));
    }

    public List<TopicRow> listTopics(long userId, TopicProgress progress, Long cursor, int limit) {
        long before = cursor == null ? Long.MAX_VALUE : cursor;
        if (progress == null) {
            return jdbc.query("SELECT * FROM tb_cognition_topic WHERE user_id=? AND id<? ORDER BY id DESC LIMIT ?", TOPIC_ROW, userId, before, limit);
        }
        return jdbc.query("SELECT * FROM tb_cognition_topic WHERE user_id=? AND progress=? AND id<? ORDER BY id DESC LIMIT ?",
                TOPIC_ROW, userId, progress.name(), before, limit);
    }

    public void updateTopicProgress(long userId, TopicRow topic, TopicProgress progress) {
        int nextVersion = topic.currentVersion() + 1;
        jdbc.update("UPDATE tb_cognition_topic SET progress=?,current_version=?,version=version+1 WHERE user_id=? AND id=?",
                progress.name(), nextVersion, userId, topic.id());
        insertTopicVersion(userId, topic.id(), nextVersion, topic.summary(), topic.uncertainty(), topic.maturity(), progress,
                topic.riskStatus(), "USER_PROGRESS_CHANGED");
    }

    public List<ActionView> findTopicActions(long userId, long topicId) {
        return jdbc.query("SELECT * FROM tb_cognition_action WHERE user_id=? AND topic_id=? ORDER BY id DESC", ACTION_ROW, userId, topicId);
    }

    public List<FeedbackView> findTopicFeedback(long userId, long topicId) {
        return jdbc.query("SELECT * FROM tb_cognition_feedback WHERE user_id=? AND topic_id=? ORDER BY id DESC", FEEDBACK_ROW, userId, topicId);
    }

    public List<TopicVersionView> findTopicVersions(long userId, long topicId) {
        return jdbc.query("SELECT * FROM tb_cognition_topic_version WHERE user_id=? AND topic_id=? ORDER BY version_no DESC",
                (rs, row) -> new TopicVersionView(rs.getInt("version_no"), rs.getString("summary"), rs.getString("uncertainty"),
                        CognitionMaturity.valueOf(rs.getString("maturity")), TopicProgress.valueOf(rs.getString("progress")),
                        RiskStatus.valueOf(rs.getString("risk_status")), rs.getString("change_reason"), instant(rs.getTimestamp("created_at"))),
                userId, topicId);
    }

    public Optional<ActionView> findAction(long userId, long actionId, boolean forUpdate) {
        return first(jdbc.query("SELECT * FROM tb_cognition_action WHERE user_id=? AND id=?" + (forUpdate ? " FOR UPDATE" : ""),
                ACTION_ROW, userId, actionId));
    }

    public Optional<ActionView> findFirstTopicAction(long userId, long topicId) {
        return first(jdbc.query("SELECT * FROM tb_cognition_action WHERE user_id=? AND topic_id=? ORDER BY id LIMIT 1", ACTION_ROW, userId, topicId));
    }

    public Optional<FeedbackView> findFeedbackByAction(long userId, long actionId) {
        return first(jdbc.query("SELECT * FROM tb_cognition_feedback WHERE user_id=? AND action_id=?", FEEDBACK_ROW, userId, actionId));
    }

    public long insertFeedback(long userId, ActionView action, FeedbackRequest request) {
        long id = insertAndReturnKey("""
                INSERT INTO tb_cognition_feedback (user_id,action_id,topic_id,accuracy,completed,note)
                VALUES (?,?,?,?,?,?)
                """, userId, action.actionId(), action.topicId(), request.accuracy().name(), request.completed(), request.note());
        ActionStatus status = request.completed() ? ActionStatus.COMPLETED : ActionStatus.SKIPPED;
        jdbc.update("UPDATE tb_cognition_action SET status=?,completed_at=?,version=version+1 WHERE user_id=? AND id=?",
                status.name(), Timestamp.from(Instant.now()), userId, action.actionId());

        TopicRow topic = findTopic(userId, action.topicId(), true).orElseThrow();
        int nextVersion = topic.currentVersion() + 1;
        jdbc.update("UPDATE tb_cognition_topic SET current_version=?,version=version+1 WHERE user_id=? AND id=?",
                nextVersion, userId, topic.id());
        insertTopicVersion(userId, topic.id(), nextVersion, topic.summary(), topic.uncertainty(), topic.maturity(),
                topic.progress(), topic.riskStatus(), "ACTION_FEEDBACK");
        return id;
    }

    private void insertTopicVersion(long userId, long topicId, int version, String summary, String uncertainty,
                                    CognitionMaturity maturity, TopicProgress progress, RiskStatus risk, String reason) {
        jdbc.update("""
                INSERT INTO tb_cognition_topic_version
                (user_id,topic_id,version_no,summary,uncertainty,maturity,progress,risk_status,change_reason)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, userId, topicId, version, summary, uncertainty, maturity.name(), progress.name(), risk.name(), reason);
    }

    public int countPendingDigests(long userId) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM tb_cognition_digest WHERE user_id=? AND status='PENDING_CONFIRMATION'", Integer.class, userId));
    }

    public int countFailedTasks(long userId) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM tb_cognition_digest_task WHERE user_id=? AND status='FAILED'", Integer.class, userId));
    }

    public Optional<TopicRow> findPrimaryTopic(long userId) {
        return first(jdbc.query("""
                SELECT * FROM tb_cognition_topic WHERE user_id=? AND progress IN ('FOLLOWING','OBSERVING')
                ORDER BY CASE risk_status WHEN 'PROFESSIONAL_HELP' THEN 1 WHEN 'WATCH' THEN 2 ELSE 3 END, updated_at DESC LIMIT 1
                """, TOPIC_ROW, userId));
    }

    public Optional<ActionView> findNextAction(long userId) {
        return first(jdbc.query("SELECT * FROM tb_cognition_action WHERE user_id=? AND status='PENDING' ORDER BY due_at,id LIMIT 1", ACTION_ROW, userId));
    }

    public Optional<IdempotencyRow> findIdempotency(long userId, String operation, String key) {
        return first(jdbc.query("SELECT * FROM tb_cognition_idempotency WHERE user_id=? AND operation=? AND idempotency_key=? AND expires_at>?",
                (rs, row) -> new IdempotencyRow(rs.getString("request_hash"), rs.getString("resource_type"),
                        nullableLong(rs, "resource_id")), userId, operation, key, Timestamp.from(Instant.now())));
    }

    public void insertIdempotency(long userId, String operation, String key, String requestHash, String resourceType, long resourceId) {
        jdbc.update("""
                INSERT INTO tb_cognition_idempotency
                (user_id,operation,idempotency_key,request_hash,resource_type,resource_id,expires_at)
                VALUES (?,?,?,?,?,?,?)
                """, userId, operation, key, requestHash, resourceType, resourceId,
                Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS)));
    }

    private long insertAndReturnKey(String sql, Object... args) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, key);
        return requiredKey(key);
    }

    private static long requiredKey(KeyHolder key) {
        Number value = key.getKey();
        if (value == null) throw new IllegalStateException("database did not return generated key");
        return value.longValue();
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private static final RowMapper<ClueView> CLUE_ROW = (rs, row) -> new ClueView(
            rs.getLong("id"), ClueType.valueOf(rs.getString("clue_type")),
            rs.getString("mark_intent") == null ? null : MarkIntent.valueOf(rs.getString("mark_intent")),
            rs.getString("relation_detail") == null ? null : RelationDetail.valueOf(rs.getString("relation_detail")),
            rs.getString("desired_help"), rs.getString("article_id"), rs.getString("article_title"),
            rs.getString("source_name"), rs.getString("excerpt"), rs.getString("question_type"), rs.getString("question_text"),
            nullableLong(rs, "body_record_id"), instant(rs.getTimestamp("occurred_at")), ClueStatus.valueOf(rs.getString("status")),
            instant(rs.getTimestamp("created_at")));

    private static final RowMapper<TaskRow> TASK_ROW = (rs, row) -> new TaskRow(rs.getLong("id"),
            DigestTaskStatus.valueOf(rs.getString("status")), GeneratorType.valueOf(rs.getString("generator_type")),
            rs.getInt("attempt_count"), rs.getString("failure_code"), nullableLong(rs, "digest_id"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("finished_at")));

    private static final RowMapper<DigestRow> DIGEST_ROW = (rs, row) -> new DigestRow(rs.getLong("id"), rs.getLong("digest_task_id"),
            DigestStatus.valueOf(rs.getString("status")), rs.getString("title"), rs.getString("common_point"),
            rs.getString("possible_link"), rs.getString("uncertainty"), rs.getString("suggested_action"),
            GeneratorType.valueOf(rs.getString("generator_type")), rs.getString("generator_version"), rs.getInt("version"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("decided_at")));

    private static final RowMapper<TopicRow> TOPIC_ROW = (rs, row) -> new TopicRow(rs.getLong("id"), rs.getLong("source_digest_id"),
            rs.getString("title"), rs.getString("summary"), rs.getString("uncertainty"),
            CognitionMaturity.valueOf(rs.getString("maturity")), TopicProgress.valueOf(rs.getString("progress")),
            RiskStatus.valueOf(rs.getString("risk_status")), rs.getInt("current_version"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));

    private static final RowMapper<ActionView> ACTION_ROW = (rs, row) -> new ActionView(rs.getLong("id"), rs.getLong("topic_id"),
            rs.getString("title"), rs.getString("instruction"), ActionStatus.valueOf(rs.getString("status")),
            instant(rs.getTimestamp("due_at")), instant(rs.getTimestamp("completed_at")));

    private static final RowMapper<FeedbackView> FEEDBACK_ROW = (rs, row) -> new FeedbackView(rs.getLong("id"), rs.getLong("action_id"),
            rs.getLong("topic_id"), FeedbackAccuracy.valueOf(rs.getString("accuracy")), rs.getBoolean("completed"),
            instant(rs.getTimestamp("created_at")));

    public record TaskRow(long id, DigestTaskStatus status, GeneratorType generatorType, int attemptCount,
                          String failureCode, Long digestId, Instant createdAt, Instant finishedAt) {
    }

    public record DigestRow(long id, long taskId, DigestStatus status, String title, String commonPoint,
                            String possibleLink, String uncertainty, String suggestedAction, GeneratorType generatorType,
                            String generatorVersion, int version, Instant createdAt, Instant decidedAt) {
    }

    public record TopicRow(long id, long sourceDigestId, String title, String summary, String uncertainty,
                           CognitionMaturity maturity, TopicProgress progress, RiskStatus riskStatus, int currentVersion,
                           Instant createdAt, Instant updatedAt) {
    }

    public record IdempotencyRow(String requestHash, String resourceType, Long resourceId) {
    }
}
