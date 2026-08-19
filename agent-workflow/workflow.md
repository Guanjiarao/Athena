# Workflow V1

## Nodes

```text
backend eligible-input query
-> input schema validation
-> intent classification
-> existing-topic candidate match
-> draft eligibility gate
-> structured digest generation
-> safety and evidence validation
-> persist PENDING_CONFIRMATION digest
-> user decision in ordinary backend transaction
```

## 1. Input assembly

The backend supplies only the current user's authorized records. It removes direct identifiers and
never supplies browsing, likes, ordinary collections, community posts, or open-web text.

Evidence weights are semantic constraints, not scores shown to users:

- confirmed body record: high;
- cycle record: high for timing only;
- authorized reliable device record: high for the measured value only;
- `RELATED` article mark: low;
- `QUESTION`: low and question-context only.

## 2. Intent classification

Allowed intents:

- `OBSERVE_PERSONAL_CHANGE`: user explicitly wants to observe something possibly related to them;
- `UNDERSTAND_QUESTION`: user asks for explanation without confirming a symptom;
- `SAVE_KNOWLEDGE`: no personal topic requested;
- `OUT_OF_SCOPE`: diagnosis, treatment prescription, emergency assessment, or missing context.

A question must stay a question unless a separate confirmed body record supports a personal event.

## 3. Topic matching

Return at most three existing-topic candidates. A match requires a specific shared object and scope,
not merely both being about women's health. When uncertain, return no match and create a new draft
title; never merge topics silently.

## 4. Draft eligibility

A user-triggered organize request may create a draft from one or more eligible clues. This is not an
automatic health conclusion. Automatic rule-triggered drafting requires at least one confirmed body
or cycle record plus an explicit rule result. A count such as “three clues” is never sufficient by
itself.

Content-only inputs may produce a knowledge/question digest, but may not raise maturity above
`CLUE`, set risk, or state that the user experienced the content.

## 5. Generation

Output must contain: title, common point, possible link, uncertainty, one low-burden next action,
evidence references, confidence, and `requiresProfessionalHelpRule=false`.

The next action may ask for observation, a body record, reading reviewed content, or professional
help when the backend has already supplied an explicit professional-help rule. It may not prescribe
medication, diagnose, or claim causality.

## 6. Validation

Reject output when:

- JSON Schema validation fails;
- an evidence ID was not in the input;
- a question or article mark is rewritten as a confirmed body fact;
- uncertainty is absent;
- diagnostic or overly certain language appears;
- a risk state is invented;
- confidence is below `0.55`;
- title is too broad to guide a next action.

Rejected output marks the digest task `FAILED` with a safe code. Original clues remain and the user
can retry. No raw sensitive text enters failure logs.

## 7. Runtime failure paths

- timeout: one automatic retry with jitter, then `AGENT_TIMEOUT`;
- refusal: `AGENT_REFUSED`, preserve inputs;
- invalid JSON: one repair attempt using schema only, then `AGENT_SCHEMA_INVALID`;
- low confidence: `AGENT_LOW_CONFIDENCE`, no draft;
- safety violation: `AGENT_SAFETY_REJECTED`, no repair with expanded context;
- duplicate task: return the existing task by idempotency key.

## 8. User confirmation

The Agent never performs `ACCEPT_TOPIC`, `SAVE_KNOWLEDGE`, or `REJECT`. These remain ordinary
backend endpoints and transactions. Replacing the fixed generator must not change Android DTOs,
URLs, database objects, or confirmation thresholds.
