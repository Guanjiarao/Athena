package athena.cognition.biz.repository;

import athena.cognition.biz.domain.CognitionIds;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC persistence for the 11 contract tables (cognition-contract-v1.md section 11).
 * Internal ids are numeric; external string ids (clue_1001 form) are assembled in
 * the service layer via {@link CognitionIds}.
 */
@Repository
public class CognitionJdbcRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public CognitionJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    // ---------- clue ----------

    public long insertClue(long userId, ClueCreateRequest request, ClueIntent intent, ClueStatus status,
                           HelpRequestType helpRequestType, CycleRelation cycleRelation) {
        String sql = """
                INSERT INTO cognition_clue
                (user_id, type, intent, relation_type, help_request_type, article_id, article_title, article_type,
                 selected_text, question_type, question_text, occurred_at, cycle_relation, severity, resolved,
                 source, status, suggested_topic_id, suggested_topic_title, original_label)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, request.type().name());
            ps.setString(3, intent.name());
            ps.setString(4, enumName(request.relationType()));
            ps.setString(5, helpRequestType.name());
            ps.setString(6, request.articleId());
            ps.setString(7, request.articleTitle());
            ps.setObject(8, request.articleType());
            ps.setString(9, request.selectedText());
            ps.setString(10, enumName(request.questionType()));
            ps.setString(11, request.questionText());
            ps.setTimestamp(12, request.occurredAt() == null ? null : Timestamp.from(request.occurredAt()));
            ps.setString(13, cycleRelation.name());
            ps.setObject(14, request.severity());
            ps.setObject(15, request.resolved());
            ps.setString(16, ClueSource.KNOWLEDGE_ARTICLE.name());
            ps.setString(17, status.name());
            ps.setString(18, request.suggestedTopicId());
            ps.setString(19, request.suggestedTopicTitle());
            ps.setString(20, request.originalLabel());
            return ps;
        }, key);
        return requiredKey(key);
    }

    public Optional<ClueRow> findClue(long userId, long clueId) {
        return first(jdbc.query("SELECT * FROM cognition_clue WHERE user_id=? AND id=? AND deleted=0",
                CLUE_ROW, userId, clueId));
    }

    public List<ClueRow> findClues(long userId, List<Long> clueIds) {
        if (clueIds.isEmpty()) return List.of();
        return namedJdbc.query("SELECT * FROM cognition_clue WHERE user_id=:userId AND id IN (:ids) AND deleted=0 ORDER BY id",
                new MapSqlParameterSource("userId", userId).addValue("ids", clueIds), CLUE_ROW);
    }

    /** Section 8.3 view + optional precise filters, page is 1-based. */
    public List<ClueRow> listClues(long userId, ClueListView view, ClueIntent intent, ClueStatus status,
                                   String articleId, int offset, int limit) {
        Clause clause = clueClause(userId, view, intent, status, articleId);
        return jdbc.query(clause.sql + " ORDER BY id DESC LIMIT ? OFFSET ?",
                CLUE_ROW, clause.argsWith(limit, offset));
    }

    public long countClues(long userId, ClueListView view, ClueIntent intent, ClueStatus status, String articleId) {
        Clause clause = clueClause(userId, view, intent, status, articleId);
        return Objects.requireNonNull(jdbc.queryForObject(
                clause.countSql(), Long.class, clause.args()));
    }

    private record Clause(String sql, String countSql, Object[] args) {
        Object[] argsWith(Object... extra) {
            Object[] all = new Object[args.length + extra.length];
            System.arraycopy(args, 0, all, 0, args.length);
            System.arraycopy(extra, 0, all, args.length, extra.length);
            return all;
        }
    }

    private Clause clueClause(long userId, ClueListView view, ClueIntent intent, ClueStatus status, String articleId) {
        StringBuilder where = new StringBuilder(" FROM cognition_clue WHERE user_id=? AND deleted=0");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (view != null) {
            switch (view) {
                case PENDING -> where.append(" AND intent='RELATED' AND status='PENDING'");
                case ORGANIZED -> where.append(" AND status='ORGANIZED'");
                case QUESTIONS -> where.append(" AND intent='QUESTION'");
                case ALL -> {
                }
            }
        }
        if (intent != null) {
            where.append(" AND intent=?");
            args.add(intent.name());
        }
        if (status != null) {
            where.append(" AND status=?");
            args.add(status.name());
        }
        if (articleId != null && !articleId.isBlank()) {
            where.append(" AND article_id=?");
            args.add(articleId);
        }
        return new Clause("SELECT *" + where, "SELECT COUNT(*)" + where, args.toArray());
    }

    /** Section 10.1 deterministic candidate grouping: RELATED + PENDING clues of the same group. */
    public List<ClueRow> findPendingRelatedCluesForCandidate(long userId, String suggestedTopicId, String suggestedTopicTitle) {
        if (suggestedTopicId != null && !suggestedTopicId.isBlank()) {
            return jdbc.query("""
                    SELECT * FROM cognition_clue
                    WHERE user_id=? AND deleted=0 AND intent='RELATED' AND status='PENDING' AND suggested_topic_id=?
                    ORDER BY id
                    """, CLUE_ROW, userId, suggestedTopicId);
        }
        if (suggestedTopicTitle != null && !suggestedTopicTitle.isBlank()) {
            return jdbc.query("""
                    SELECT * FROM cognition_clue
                    WHERE user_id=? AND deleted=0 AND intent='RELATED' AND status='PENDING'
                      AND TRIM(suggested_topic_title)=TRIM(?)
                    ORDER BY id
                    """, CLUE_ROW, userId, suggestedTopicTitle);
        }
        return List.of();
    }

    public void updateClueStatus(long userId, List<Long> clueIds, ClueStatus status) {
        if (clueIds.isEmpty()) return;
        namedJdbc.update("UPDATE cognition_clue SET status=:status, version=version+1 WHERE user_id=:userId AND id IN (:ids) AND deleted=0",
                new MapSqlParameterSource("userId", userId).addValue("ids", clueIds).addValue("status", status.name()));
    }

    public void logicalDeleteClue(long userId, long clueId) {
        jdbc.update("UPDATE cognition_clue SET deleted=1, version=version+1 WHERE user_id=? AND id=? AND deleted=0",
                userId, clueId);
    }

    /** A clue referenced by any digest cannot be revoked (section 6.4). */
    public boolean isClueUsedInDigest(long userId, long clueId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cognition_digest_clue WHERE user_id=? AND clue_id=?", Long.class, userId, clueId);
        return count != null && count > 0;
    }

    // ---------- digest ----------

    public long insertDigest(long userId, String title, String generatorVersion) {
        return insertAndReturnKey(
                "INSERT INTO cognition_digest (user_id,title,status,generator_version) VALUES (?,?,'PROCESSING',?)",
                userId, title, generatorVersion);
    }

    public void completeDigest(long userId, long digestId, String commonPoint, String possibleRelation,
                               String uncertainty, String suggestedAction) {
        jdbc.update("""
                UPDATE cognition_digest
                SET status='READY', common_point=?, possible_relation=?, uncertainty=?, suggested_action=?,
                    generated_at=?, failure_code=NULL, version=version+1
                WHERE user_id=? AND id=?
                """, commonPoint, possibleRelation, uncertainty, suggestedAction,
                Timestamp.from(Instant.now()), userId, digestId);
    }

    public void updateDigestTitle(long userId, long digestId, String title) {
        jdbc.update("UPDATE cognition_digest SET title=? WHERE user_id=? AND id=?", title, userId, digestId);
    }

    public void markDigestProcessing(long userId, long digestId) {
        jdbc.update("UPDATE cognition_digest SET status='PROCESSING', failure_code=NULL, version=version+1 WHERE user_id=? AND id=?",
                userId, digestId);
    }

    public void failDigest(long userId, long digestId, String failureCode) {
        jdbc.update("UPDATE cognition_digest SET status='FAILED', failure_code=?, version=version+1 WHERE user_id=? AND id=?",
                failureCode, userId, digestId);
    }

    public void decideDigest(long userId, long digestId, DigestStatus status) {
        jdbc.update("UPDATE cognition_digest SET status=?, version=version+1 WHERE user_id=? AND id=?",
                status.name(), userId, digestId);
    }

    public Optional<DigestRow> findDigest(long userId, long digestId, boolean forUpdate) {
        String suffix = forUpdate ? " FOR UPDATE" : "";
        return first(jdbc.query("SELECT * FROM cognition_digest WHERE user_id=? AND id=? AND deleted=0" + suffix,
                DIGEST_ROW, userId, digestId));
    }

    public List<DigestRow> listDigests(long userId, DigestStatus status, int offset, int limit) {
        if (status == null) {
            return jdbc.query("SELECT * FROM cognition_digest WHERE user_id=? AND deleted=0 ORDER BY id DESC LIMIT ? OFFSET ?",
                    DIGEST_ROW, userId, limit, offset);
        }
        return jdbc.query("SELECT * FROM cognition_digest WHERE user_id=? AND status=? AND deleted=0 ORDER BY id DESC LIMIT ? OFFSET ?",
                DIGEST_ROW, userId, status.name(), limit, offset);
    }

    public long countDigests(long userId, DigestStatus status) {
        if (status == null) {
            return Objects.requireNonNull(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM cognition_digest WHERE user_id=? AND deleted=0", Long.class, userId));
        }
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cognition_digest WHERE user_id=? AND status=? AND deleted=0", Long.class, userId, status.name()));
    }

    /** Latest open digest (READY first, then PROCESSING) for the inbox aggregate. */
    public Optional<DigestRow> findActiveDigest(long userId) {
        return first(jdbc.query("""
                SELECT * FROM cognition_digest
                WHERE user_id=? AND deleted=0 AND status IN ('READY','PROCESSING')
                ORDER BY CASE status WHEN 'READY' THEN 0 ELSE 1 END, id DESC LIMIT 1
                """, DIGEST_ROW, userId));
    }

    public boolean hasDigestWithStatus(long userId, DigestStatus status) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cognition_digest WHERE user_id=? AND status=? AND deleted=0",
                Long.class, userId, status.name());
        return count != null && count > 0;
    }

    public long countPendingDigests(long userId) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cognition_digest WHERE user_id=? AND deleted=0 AND status IN ('READY','PROCESSING')",
                Long.class, userId));
    }

    // ---------- digest <-> clue ----------

    public void linkDigestClues(long userId, long digestId, List<Long> clueIds) {
        jdbc.batchUpdate("INSERT INTO cognition_digest_clue (user_id,digest_id,clue_id) VALUES (?,?,?)",
                clueIds, clueIds.size(), (ps, clueId) -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, digestId);
                    ps.setLong(3, clueId);
                });
    }

    public List<Long> findDigestClueIds(long userId, long digestId) {
        return jdbc.query("SELECT clue_id FROM cognition_digest_clue WHERE user_id=? AND digest_id=? ORDER BY clue_id",
                (rs, row) -> rs.getLong("clue_id"), userId, digestId);
    }

    public List<ClueRow> findDigestClues(long userId, long digestId) {
        return jdbc.query("""
                SELECT c.* FROM cognition_clue c
                JOIN cognition_digest_clue dc ON dc.clue_id=c.id
                WHERE dc.user_id=? AND dc.digest_id=? ORDER BY c.id
                """, CLUE_ROW, userId, digestId);
    }

    /** Section 10.1: at most one open digest per candidate topic group. */
    public boolean hasOpenDigestForClues(long userId, List<Long> clueIds) {
        if (clueIds.isEmpty()) return false;
        Long count = namedJdbc.queryForObject("""
                SELECT COUNT(*) FROM cognition_digest_clue dc
                JOIN cognition_digest d ON d.id=dc.digest_id
                WHERE dc.user_id=:userId AND dc.clue_id IN (:ids)
                  AND d.deleted=0 AND d.status IN ('PROCESSING','READY')
                """, new MapSqlParameterSource("userId", userId).addValue("ids", clueIds), Long.class);
        return count != null && count > 0;
    }

    // ---------- evidence ----------

    public long insertEvidence(long userId, EvidenceSourceType sourceType, String sourceId, FactLevel factLevel,
                               String summary, Instant occurredAt) {
        return insertAndReturnKey("""
                INSERT INTO cognition_evidence (user_id,source_type,source_id,fact_level,summary,occurred_at,linked_at)
                VALUES (?,?,?,?,?,?,?)
                """, userId, sourceType.name(), sourceId, factLevel.name(), summary,
                occurredAt == null ? null : Timestamp.from(occurredAt), Timestamp.from(Instant.now()));
    }

    public void linkDigestEvidence(long userId, long digestId, List<Long> evidenceIds) {
        jdbc.batchUpdate("INSERT INTO cognition_digest_evidence (user_id,digest_id,evidence_id) VALUES (?,?,?)",
                evidenceIds, evidenceIds.size(), (ps, evidenceId) -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, digestId);
                    ps.setLong(3, evidenceId);
                });
    }

    public void linkTopicEvidence(long userId, long topicId, List<Long> evidenceIds) {
        jdbc.batchUpdate("INSERT INTO cognition_topic_evidence (user_id,topic_id,evidence_id) VALUES (?,?,?)",
                evidenceIds, evidenceIds.size(), (ps, evidenceId) -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, topicId);
                    ps.setLong(3, evidenceId);
                });
    }

    private static final String EVIDENCE_SELECT = """
            SELECT e.*, c.article_id AS clue_article_id, c.article_title AS clue_article_title,
                   c.article_type AS clue_article_type
            FROM cognition_evidence e
            LEFT JOIN cognition_clue c ON e.source_type='CLUE'
                AND c.id=CAST(SUBSTRING_INDEX(e.source_id, '_', -1) AS UNSIGNED)
            """;

    public List<EvidenceRow> findDigestEvidence(long userId, long digestId) {
        return jdbc.query(EVIDENCE_SELECT + """
                JOIN cognition_digest_evidence de ON de.evidence_id=e.id
                WHERE de.user_id=? AND de.digest_id=? AND e.deleted=0 ORDER BY e.id
                """, EVIDENCE_ROW, userId, digestId);
    }

    public List<EvidenceRow> findTopicEvidence(long userId, long topicId) {
        return jdbc.query(EVIDENCE_SELECT + """
                JOIN cognition_topic_evidence te ON te.evidence_id=e.id
                WHERE te.user_id=? AND te.topic_id=? AND e.deleted=0 AND e.active=1 ORDER BY e.id
                """, EVIDENCE_ROW, userId, topicId);
    }

    // ---------- digest task ----------

    public long insertTask(long userId, TriggerType triggerType, String generatorVersion) {
        return insertAndReturnKey(
                "INSERT INTO cognition_digest_task (user_id,status,trigger_type,generator_version) VALUES (?,'PENDING',?,?)",
                userId, triggerType.name(), generatorVersion);
    }

    public Optional<TaskRow> findTask(long userId, long taskId) {
        return first(jdbc.query("SELECT * FROM cognition_digest_task WHERE user_id=? AND id=? AND deleted=0",
                TASK_ROW, userId, taskId));
    }

    /** Row lock for re-entrant-safe retry (section 12). */
    public Optional<TaskRow> findTaskForUpdate(long userId, long taskId) {
        return first(jdbc.query("SELECT * FROM cognition_digest_task WHERE user_id=? AND id=? AND deleted=0 FOR UPDATE",
                TASK_ROW, userId, taskId));
    }

    /**
     * Retry bumps retry_count only: trigger_type keeps the original value
     * (RULE_THRESHOLD / USER_REQUEST) so the task record stays truthful about
     * how the digest was first triggered. The RETRY enum value is reserved for
     * a future manual re-trigger that creates a new task record.
     */
    public void markTaskRunning(long userId, long taskId, boolean retry) {
        jdbc.update("""
                UPDATE cognition_digest_task
                SET status='RUNNING', retry_count=retry_count+?, failure_code=NULL, version=version+1
                WHERE user_id=? AND id=?
                """, retry ? 1 : 0, userId, taskId);
    }

    public void markTaskSucceeded(long userId, long taskId, long digestId) {
        jdbc.update("UPDATE cognition_digest_task SET status='SUCCEEDED', digest_id=?, version=version+1 WHERE user_id=? AND id=?",
                digestId, userId, taskId);
    }

    public void markTaskFailed(long userId, long taskId, long digestId, String failureCode) {
        jdbc.update("UPDATE cognition_digest_task SET status='FAILED', digest_id=?, failure_code=?, version=version+1 WHERE user_id=? AND id=?",
                digestId, failureCode, userId, taskId);
    }

    public Optional<TaskRow> findLatestTask(long userId) {
        return first(jdbc.query(
                "SELECT * FROM cognition_digest_task WHERE user_id=? AND deleted=0 ORDER BY id DESC LIMIT 1",
                TASK_ROW, userId));
    }

    public boolean hasProcessingTask(long userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cognition_digest_task WHERE user_id=? AND deleted=0 AND status IN ('PENDING','RUNNING')",
                Long.class, userId);
        return count != null && count > 0;
    }

    public int countFailedTasks(long userId) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cognition_digest_task WHERE user_id=? AND deleted=0 AND status='FAILED'",
                Integer.class, userId));
    }

    // ---------- topic ----------

    public long insertTopic(long userId, long sourceDigestId, String title, String domain, String stageUnderstanding,
                            String knownFactsJson, String openQuestionsJson, int evidenceCount, int articleClueCount,
                            int bodyRecordCount, int cycleCount) {
        return insertAndReturnKey("""
                INSERT INTO cognition_topic
                (user_id,source_digest_id,title,domain,maturity,user_progress,risk_status,stage_understanding,
                 known_facts,open_questions,evidence_count,article_clue_count,body_record_count,cycle_count,last_updated_at)
                VALUES (?,?,?,?,'CLUE','OBSERVING','NONE',?,?,?,?,?,?,?,?)
                """, userId, sourceDigestId, title, domain, stageUnderstanding, knownFactsJson, openQuestionsJson,
                evidenceCount, articleClueCount, bodyRecordCount, cycleCount, Timestamp.from(Instant.now()));
    }

    public Optional<TopicRow> findTopic(long userId, long topicId, boolean forUpdate) {
        String suffix = forUpdate ? " FOR UPDATE" : "";
        return first(jdbc.query("SELECT * FROM cognition_topic WHERE user_id=? AND id=? AND deleted=0" + suffix,
                TOPIC_ROW, userId, topicId));
    }

    public Optional<TopicRow> findTopicByDigest(long userId, long digestId) {
        return first(jdbc.query("SELECT * FROM cognition_topic WHERE user_id=? AND source_digest_id=? AND deleted=0",
                TOPIC_ROW, userId, digestId));
    }

    public List<TopicRow> listTopics(long userId, int offset, int limit) {
        return jdbc.query("SELECT * FROM cognition_topic WHERE user_id=? AND deleted=0 ORDER BY id DESC LIMIT ? OFFSET ?",
                TOPIC_ROW, userId, limit, offset);
    }

    public long countTopics(long userId) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cognition_topic WHERE user_id=? AND deleted=0", Long.class, userId));
    }

    public void updateTopicNextAction(long userId, long topicId, long actionId) {
        jdbc.update("UPDATE cognition_topic SET next_action_id=? WHERE user_id=? AND id=?", actionId, userId, topicId);
    }

    /** Section 7.3: feedback bumps version, lastUpdatedAt and counters. */
    public void updateTopicAfterFeedback(long userId, long topicId, boolean evidenceAdded) {
        jdbc.update("""
                UPDATE cognition_topic
                SET version=version+1, last_updated_at=?,
                    evidence_count=evidence_count+?,
                    body_record_count=body_record_count+?
                WHERE user_id=? AND id=?
                """, Timestamp.from(Instant.now()), evidenceAdded ? 1 : 0, evidenceAdded ? 1 : 0, userId, topicId);
    }

    public Optional<TopicRow> findPrimaryTopic(long userId) {
        return first(jdbc.query("""
                SELECT * FROM cognition_topic WHERE user_id=? AND deleted=0
                  AND user_progress IN ('FOLLOWING','OBSERVING','PENDING_CONFIRMATION')
                ORDER BY last_updated_at DESC LIMIT 1
                """, TOPIC_ROW, userId));
    }

    // ---------- action ----------

    public long insertAction(long userId, long topicId, String title, String description, ActionType actionType,
                             Instant dueAt, String feedbackOptionsJson) {
        return insertAndReturnKey("""
                INSERT INTO cognition_action (user_id,topic_id,title,description,action_type,status,due_at,feedback_options)
                VALUES (?,?,?,?,?,'PENDING',?,?)
                """, userId, topicId, title, description, actionType.name(),
                dueAt == null ? null : Timestamp.from(dueAt), feedbackOptionsJson);
    }

    public Optional<ActionRow> findAction(long userId, long actionId, boolean forUpdate) {
        String suffix = forUpdate ? " FOR UPDATE" : "";
        return first(jdbc.query("SELECT * FROM cognition_action WHERE user_id=? AND id=? AND deleted=0" + suffix,
                ACTION_ROW, userId, actionId));
    }

    public void updateActionStatus(long userId, long actionId, ActionStatus status) {
        jdbc.update("UPDATE cognition_action SET status=?, version=version+1 WHERE user_id=? AND id=?",
                status.name(), userId, actionId);
    }

    // ---------- action feedback ----------

    public long insertFeedback(long userId, long actionId, long topicId, ActionFeedbackResult result,
                               String note, Instant occurredAt) {
        return insertAndReturnKey("""
                INSERT INTO cognition_action_feedback (user_id,action_id,topic_id,result,note,occurred_at)
                VALUES (?,?,?,?,?,?)
                """, userId, actionId, topicId, result.name(), note, Timestamp.from(occurredAt));
    }

    public void updateFeedbackEvidence(long userId, long feedbackId, long evidenceId) {
        jdbc.update("UPDATE cognition_action_feedback SET evidence_id=? WHERE user_id=? AND id=?",
                evidenceId, userId, feedbackId);
    }

    public Optional<FeedbackRow> findFeedbackByAction(long userId, long actionId) {
        return first(jdbc.query(
                "SELECT * FROM cognition_action_feedback WHERE user_id=? AND action_id=? AND deleted=0",
                FEEDBACK_ROW, userId, actionId));
    }

    public List<FeedbackRow> findRecentFeedback(long userId, long topicId, int limit) {
        return jdbc.query(
                "SELECT * FROM cognition_action_feedback WHERE user_id=? AND topic_id=? AND deleted=0 ORDER BY id DESC LIMIT ?",
                FEEDBACK_ROW, userId, topicId, limit);
    }

    // ---------- decision log ----------

    public void insertDecisionLog(long userId, long digestId, DigestDecision decision, String reason, Integer clientVersion) {
        jdbc.update("INSERT INTO cognition_decision_log (user_id,digest_id,decision,reason,client_version) VALUES (?,?,?,?,?)",
                userId, digestId, decision.name(), reason, clientVersion);
    }

    public Optional<DecisionLogRow> findLatestDecision(long userId) {
        return first(jdbc.query(
                "SELECT * FROM cognition_decision_log WHERE user_id=? ORDER BY id DESC LIMIT 1",
                (rs, row) -> new DecisionLogRow(rs.getLong("digest_id"),
                        DigestDecision.valueOf(rs.getString("decision")), instant(rs.getTimestamp("created_at"))),
                userId));
    }

    // ---------- helpers ----------

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

    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    // ---------- row mappers ----------

    private static final RowMapper<ClueRow> CLUE_ROW = (rs, row) -> new ClueRow(
            rs.getLong("id"), ClueType.valueOf(rs.getString("type")), ClueIntent.valueOf(rs.getString("intent")),
            rs.getString("relation_type") == null ? null : RelationType.valueOf(rs.getString("relation_type")),
            HelpRequestType.valueOf(rs.getString("help_request_type")),
            rs.getString("article_id"), rs.getString("article_title"), nullableInt(rs, "article_type"),
            rs.getString("selected_text"),
            rs.getString("question_type") == null ? null : QuestionType.valueOf(rs.getString("question_type")),
            rs.getString("question_text"), instant(rs.getTimestamp("occurred_at")),
            CycleRelation.valueOf(rs.getString("cycle_relation")), nullableInt(rs, "severity"),
            nullableBoolean(rs, "resolved"), ClueSource.valueOf(rs.getString("source")),
            ClueStatus.valueOf(rs.getString("status")),
            rs.getString("suggested_topic_id"), rs.getString("suggested_topic_title"), rs.getString("original_label"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));

    private static final RowMapper<DigestRow> DIGEST_ROW = (rs, row) -> new DigestRow(
            rs.getLong("id"), rs.getString("title"), DigestStatus.valueOf(rs.getString("status")),
            rs.getString("common_point"), rs.getString("possible_relation"), rs.getString("uncertainty"),
            rs.getString("suggested_action"), rs.getString("generator_version"),
            instant(rs.getTimestamp("generated_at")), rs.getString("failure_code"),
            instant(rs.getTimestamp("expires_at")), rs.getInt("version"), instant(rs.getTimestamp("created_at")));

    private static final RowMapper<TaskRow> TASK_ROW = (rs, row) -> new TaskRow(
            rs.getLong("id"), nullableLong(rs, "digest_id"), DigestTaskStatus.valueOf(rs.getString("status")),
            TriggerType.valueOf(rs.getString("trigger_type")), rs.getString("generator_version"),
            rs.getInt("retry_count"), rs.getString("failure_code"), instant(rs.getTimestamp("created_at")));

    private static final RowMapper<TopicRow> TOPIC_ROW = (rs, row) -> new TopicRow(
            rs.getLong("id"), rs.getLong("source_digest_id"), rs.getString("title"), rs.getString("domain"),
            Maturity.valueOf(rs.getString("maturity")), UserProgress.valueOf(rs.getString("user_progress")),
            RiskStatus.valueOf(rs.getString("risk_status")), rs.getString("stage_understanding"),
            rs.getString("known_facts"), rs.getString("open_questions"),
            rs.getInt("evidence_count"), rs.getInt("article_clue_count"), rs.getInt("body_record_count"),
            rs.getInt("cycle_count"), nullableLong(rs, "next_action_id"),
            instant(rs.getTimestamp("last_updated_at")), rs.getInt("version"), instant(rs.getTimestamp("created_at")));

    private static final RowMapper<ActionRow> ACTION_ROW = (rs, row) -> new ActionRow(
            rs.getLong("id"), rs.getLong("topic_id"), rs.getString("title"), rs.getString("description"),
            ActionType.valueOf(rs.getString("action_type")), ActionStatus.valueOf(rs.getString("status")),
            instant(rs.getTimestamp("due_at")), rs.getString("feedback_options"), instant(rs.getTimestamp("created_at")));

    private static final RowMapper<FeedbackRow> FEEDBACK_ROW = (rs, row) -> new FeedbackRow(
            rs.getLong("id"), rs.getLong("action_id"), rs.getLong("topic_id"),
            ActionFeedbackResult.valueOf(rs.getString("result")), rs.getString("note"),
            instant(rs.getTimestamp("occurred_at")), instant(rs.getTimestamp("created_at")),
            nullableLong(rs, "evidence_id"));

    private static final RowMapper<EvidenceRow> EVIDENCE_ROW = (rs, row) -> new EvidenceRow(
            rs.getLong("id"), EvidenceSourceType.valueOf(rs.getString("source_type")), rs.getString("source_id"),
            FactLevel.valueOf(rs.getString("fact_level")), rs.getString("summary"),
            instant(rs.getTimestamp("occurred_at")), instant(rs.getTimestamp("linked_at")),
            rs.getBoolean("active"),
            rs.getString("clue_article_id"), rs.getString("clue_article_title"), nullableInt(rs, "clue_article_type"));

    // ---------- row records (internal numeric ids) ----------

    public record ClueRow(long id, ClueType type, ClueIntent intent, RelationType relationType,
                          HelpRequestType helpRequestType, String articleId, String articleTitle, Integer articleType,
                          String selectedText, QuestionType questionType, String questionText, Instant occurredAt,
                          CycleRelation cycleRelation, Integer severity, Boolean resolved, ClueSource source,
                          ClueStatus status, String suggestedTopicId, String suggestedTopicTitle, String originalLabel,
                          Instant createdAt, Instant updatedAt) {
    }

    public record DigestRow(long id, String title, DigestStatus status, String commonPoint, String possibleRelation,
                            String uncertainty, String suggestedAction, String generatorVersion, Instant generatedAt,
                            String failureCode, Instant expiresAt, int version, Instant createdAt) {
    }

    public record TaskRow(long id, Long digestId, DigestTaskStatus status, TriggerType triggerType,
                          String generatorVersion, int retryCount, String failureCode, Instant createdAt) {
    }

    public record TopicRow(long id, long sourceDigestId, String title, String domain, Maturity maturity,
                           UserProgress userProgress, RiskStatus riskStatus, String stageUnderstanding,
                           String knownFactsJson, String openQuestionsJson, int evidenceCount, int articleClueCount,
                           int bodyRecordCount, int cycleCount, Long nextActionId, Instant lastUpdatedAt,
                           int version, Instant createdAt) {
    }

    public record ActionRow(long id, long topicId, String title, String description, ActionType actionType,
                            ActionStatus status, Instant dueAt, String feedbackOptionsJson, Instant createdAt) {
    }

    public record FeedbackRow(long id, long actionId, long topicId, ActionFeedbackResult result, String note,
                              Instant occurredAt, Instant createdAt, Long evidenceId) {
    }

    public record EvidenceRow(long id, EvidenceSourceType sourceType, String sourceId, FactLevel factLevel,
                              String summary, Instant occurredAt, Instant linkedAt, boolean active,
                              String articleId, String articleTitle, Integer articleType) {
    }

    public record DecisionLogRow(long digestId, DigestDecision decision, Instant createdAt) {
    }
}
