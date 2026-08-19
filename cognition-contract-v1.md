# Athena Cognition Contract V1

> Status: backend handoff baseline  
> Source: `athena-product-plan-v2-current.md` V2.1  
> Rule: clients never submit or select a `userId`; the service obtains it from the authenticated request context.

## 1. Domain invariants

1. A `RELATED` article clue means only that the user thinks the text may relate to them.
2. A `QUESTION` clue means only that the user wants to understand something.
3. Neither kind of article clue is a confirmed body fact or a diagnosis.
4. A digest is always a draft. A topic is created only after an explicit `ACCEPT_TOPIC` decision.
5. `SAVE_KNOWLEDGE` and `REJECT` never create a topic or action.
6. Maturity, user progress, and risk are independent state axes.
7. Every mutation is user-scoped and idempotent. Ownership is checked in the same database transaction as the mutation.
8. Logs must not contain full excerpts, questions, body notes, prompts, or model outputs.

## 2. Identifiers and time

- IDs are positive 64-bit integers serialized as JSON numbers.
- Times use ISO-8601 with an offset, for example `2026-08-19T10:30:00+08:00`.
- Mutation requests accept an `Idempotency-Key` header (1-64 visible ASCII characters).
- Reusing a key for the same operation returns the original result. Reusing it with a different payload returns `409 COGNITION_IDEMPOTENCY_CONFLICT`.

The main trace is:

```text
clueId -> digestTaskId -> digestId -> topicId -> actionId -> feedbackId
                                      \-> evidenceId
```

## 3. Enumerations

### Clue

- `ClueType`: `ARTICLE_MARK`, `BODY_RECORD`, `CYCLE_RECORD`, `DEVICE_RECORD`
- `MarkIntent`: `RELATED`, `QUESTION`, `KNOWLEDGE`
- `RelationDetail`: `CURRENT`, `HISTORICAL`, `UNCERTAIN_OBSERVE`, `KNOWLEDGE_ONLY`
- `ClueStatus`: `PENDING`, `IN_DIGEST`, `ORGANIZED`, `KNOWLEDGE_ONLY`, `REJECTED`, `WITHDRAWN`

`MarkIntent` is required for `ARTICLE_MARK`. `QUESTION` requires `questionType` or `questionText`. Article content requires `articleId`, `articleTitle`, `sourceName`, and `excerpt`.

### Digest task and digest

- `DigestTaskStatus`: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`
- `GeneratorType`: `FIXED_V1`, `AGENT_V1`
- `DigestStatus`: `PENDING_CONFIRMATION`, `ACCEPTED`, `SAVED_KNOWLEDGE`, `REJECTED`
- `DigestDecision`: `ACCEPT_TOPIC`, `SAVE_KNOWLEDGE`, `REJECT`

Only a `FAILED` task may be retried. A successful task has exactly one digest.

### Topic

- `CognitionMaturity`: `CLUE`, `INSUFFICIENT`, `EARLY_LINK`, `REPEATED_PATTERN`, `RELATIVELY_STABLE`
- `TopicProgress`: `PENDING_CONFIRMATION`, `FOLLOWING`, `OBSERVING`, `PAUSED`, `ARCHIVED`
- `RiskStatus`: `NONE`, `WATCH`, `PROFESSIONAL_HELP`

Valid user progress transitions:

```text
FOLLOWING <-> OBSERVING
FOLLOWING|OBSERVING -> PAUSED -> FOLLOWING|OBSERVING
FOLLOWING|OBSERVING|PAUSED -> ARCHIVED
```

Risk is changed only by an explicit, auditable backend rule. The fixed generator always creates `NONE`.

### Action

- `ActionStatus`: `PENDING`, `COMPLETED`, `SKIPPED`, `CANCELLED`
- `FeedbackAccuracy`: `ACCURATE`, `INACCURATE`, `DID_NOT_HAPPEN`, `NOT_SURE`

An action accepts at most one feedback. Repeated submission with the same idempotency key returns the first feedback.

## 4. API

All endpoints are under `/athena/cognition`. Successful responses use the repository's standard `Result<T>` envelope.

### 4.1 Clues

```http
POST /clues
Idempotency-Key: <key>
```

```json
{
  "clueType": "ARTICLE_MARK",
  "markIntent": "RELATED",
  "relationDetail": "UNCERTAIN_OBSERVE",
  "desiredHelp": "OBSERVE",
  "articleId": "article-42",
  "articleTitle": "经期前情绪变化",
  "sourceName": "reviewed-content",
  "excerpt": "...",
  "questionType": null,
  "questionText": null,
  "occurredAt": "2026-08-19T10:30:00+08:00"
}
```

Returns the created clue. Payload text is size-limited and never logged.

```http
GET /clues?section=PENDING|ORGANIZED|QUESTIONS&cursor=<id>&limit=20
```

Returns an ID-descending cursor page scoped to the current user.

### 4.2 Digest tasks

```http
POST /digest-tasks
Idempotency-Key: <key>

{"clueIds":[101,102]}
```

The service verifies ownership and eligibility, then creates a task. V1 uses a synchronous fixed generator behind the task boundary but still persists `PENDING/RUNNING/SUCCEEDED/FAILED` transitions.

```http
GET  /digest-tasks/{digestTaskId}
POST /digest-tasks/{digestTaskId}/retry
GET  /digests/{digestId}
GET  /digests?status=PENDING_CONFIRMATION&cursor=<id>&limit=20
```

A digest contains:

```json
{
  "digestId": 201,
  "digestTaskId": 151,
  "status": "PENDING_CONFIRMATION",
  "title": "经期前情绪变化",
  "commonPoint": "你保存的内容都在关注经期前的情绪变化。",
  "possibleLink": "这些线索可能值得结合后续身体记录继续观察。",
  "uncertainty": "仅凭文章标记不能确认你出现了相同情况，也不能说明原因。",
  "suggestedAction": "在接下来 7 天记录一次睡眠和情绪。",
  "evidence": [{"evidenceId":301,"clueId":101,"evidenceLevel":"LOW"}],
  "generatorType": "FIXED_V1",
  "version": 1
}
```

### 4.3 Digest decision

```http
POST /digests/{digestId}/decisions
Idempotency-Key: <key>

{"decision":"ACCEPT_TOPIC","reasonCode":null}
```

- `ACCEPT_TOPIC`: atomically changes the digest, creates one topic, links evidence, creates one action, and writes a decision log.
- `SAVE_KNOWLEDGE`: atomically changes the digest and clues to knowledge-only and writes a decision log.
- `REJECT`: atomically rejects the digest and writes a decision log; raw clues and the decision audit remain.
- A second, different decision returns `409 COGNITION_DIGEST_ALREADY_DECIDED`.

### 4.4 Topics and actions

```http
GET   /topics?progress=OBSERVING&cursor=<id>&limit=20
GET   /topics/{topicId}
PATCH /topics/{topicId}/progress

{"progress":"PAUSED"}

POST /actions/{actionId}/feedback
Idempotency-Key: <key>

{
  "accuracy":"ACCURATE",
  "completed":true,
  "note":"optional, never logged"
}
```

Topic detail includes its current version, evidence, actions, feedback, and version history. A feedback mutation updates the action and appends a new topic version in one transaction.

### 4.5 Health home

```http
GET /home
```

The backend, not Android, selects the current priority. Response:

```json
{
  "mode": "CALM|OBSERVE|NOTICE",
  "headline": "今天没有需要特别处理的变化",
  "summary": "最近记录仍在你的个人范围内。",
  "latestFinding": null,
  "primaryTopic": null,
  "pendingDigestCount": 0,
  "nextAction": null,
  "failedTaskCount": 0,
  "generatedAt": "2026-08-19T10:30:00+08:00"
}
```

Selection order is: explicit professional-help rule, watch rule, active topic with pending action, pending digest, calm state. V1 does not infer risk from article marks, questions, browsing, likes, collections, or community content.

## 5. Error codes

- `COGNITION_UNAUTHENTICATED` (401)
- `COGNITION_NOT_FOUND` (404; also used for another user's resource)
- `COGNITION_VALIDATION_FAILED` (400)
- `COGNITION_IDEMPOTENCY_CONFLICT` (409)
- `COGNITION_DIGEST_ALREADY_DECIDED` (409)
- `COGNITION_INVALID_STATE_TRANSITION` (409)
- `COGNITION_FEEDBACK_ALREADY_SUBMITTED` (409)
- `COGNITION_TASK_NOT_RETRYABLE` (409)
- `COGNITION_GENERATION_FAILED` (500)

## 6. Persistence and privacy requirements

- Every user-owned table has `user_id` and an index beginning with `user_id`.
- Unique constraints enforce one digest per task, one accepted topic per digest, and one feedback per action.
- Decisions and topic versions are append-only audit records.
- Raw sensitive text is stored only where required; list/home projections do not return it unnecessarily.
- Soft withdrawal records user intent and removes the clue from future generation. Physical deletion/export is a separate privacy endpoint milestone.
- The Android app contains no model key. The fixed generator and later Agent run only on the backend.

## 7. Generator seam

The backend calls a replaceable `DigestGenerator` with a versioned input and validates a versioned output. `FixedDigestGenerator` is the first implementation. A later `AgentDigestGenerator` must preserve this contract and all confirmation, ownership, idempotency, uncertainty, and safety rules.
