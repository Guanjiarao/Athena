# Context-Safe Editing Reference

This file explains concrete tactics for editing large files or long documents without losing structure.

---

## What I usually do when a file is too large

When I feel a file is exceeding comfortable working context, I usually switch to this pattern:

1. **Map first**
   - Read the top of the file
   - Read the middle only if needed
   - Read the target region
   - Note section/function boundaries

2. **Name the edit surface**
   - Example: “only the correction helpers in `TurnUnderstandingWorker`”
   - Example: “only sections 3–5 of the plan document”

3. **Avoid full-file rewrites**
   - Prefer exact replacement in a bounded region
   - Only rewrite whole file if the file is small or the task explicitly requires a rewrite

4. **Use anchor-based edits**
   - Include enough surrounding text so replacement is unique
   - If replacement fails, expand context horizontally, not globally

5. **Re-read after edit**
   - Re-read the touched region immediately
   - Re-read neighboring code if control flow may have changed

6. **Verify narrowly**
   - Run lints only on edited files
   - Run the smallest relevant validation surface

---

## File mapping template

Use this before editing a long file.

```text
File map
- Region 1: [lines/heading] — [responsibility]
- Region 2: [lines/heading] — [responsibility]
- Region 3: [lines/heading] — [responsibility]

Target edit surface
- [exact function / section / block]

Adjacent dependencies
- Reads from: [...]
- Writes to / affects: [...]
- Must re-check after edit: [...]
```

---

## Prompt template: map a long file before editing

```text
Do not edit yet.

First map this file so we can work safely without overloading context.

Please do the following:
1. Identify the top-level regions or responsibilities in the file.
2. Tell me which exact region needs to change for this task.
3. Tell me which nearby functions/sections must be re-read after the change.
4. Do not propose broad rewrites yet.
5. Do not modify code until the edit surface is explicitly named.
```

---

## Prompt template: scoped edit only

```text
Work only inside the named edit surface.

Rules:
- Do not rewrite the whole file.
- Do not touch unrelated helpers.
- Use the smallest anchored edit that can accomplish the change.
- After editing, re-read the touched region and the nearest affected caller/callee.
- If the change expands beyond the named surface, stop and report that the boundary is too small.
```

---

## Prompt template: long document rewrite

```text
This document is too long to safely rewrite in one pass.

Please:
1. Map the document by headings.
2. Confirm which sections actually need rewriting.
3. Rewrite only those sections.
4. Keep paragraph structure tight and avoid accidental changes to untouched sections.
5. Re-read the edited headings afterward to ensure tone and terminology still match.
```

---

## How to decide between replace vs rewrite

### Prefer targeted replacement when
- the file already has the right overall structure
- only one function/section is wrong
- the change should be minimal
- you need to preserve surrounding behavior

### Prefer larger rewrite when
- the target region is already internally incoherent
- multiple neighboring blocks must change together
- the existing text is shorter to replace as a unit than to patch piecemeal
- the user explicitly asks for restructuring

### Avoid whole-file rewrite when
- the file is long and only one part is changing
- you have not mapped the file first
- the file has many subtle local invariants

---

## Tactics for overloaded implementation files

When a class or worker is too crowded, I usually break the work into these steps:

1. classify methods into roles
   - parsing
   - resolution
   - state mutation
   - orchestration

2. pick one role to extract
   - do not extract multiple roles at once unless tightly coupled

3. leave orchestration in place initially
   - extraction first
   - behavior cleanup second

4. verify that the original file truly lost responsibility
   - not just private methods moved around

This is especially useful for files like:
- large workers
- reducers with many merge paths
- prompt/template builders with embedded policy

---

## Signs I am exceeding safe context

If any of these happen, I should slow down and re-map:
- I cannot explain what the neighboring region does
- I am making edits based on memory of code I no longer see
- replacement targets stop being unique
- I keep scrolling/re-reading the same large file
- I want to “just rewrite the whole thing” to make progress
- I start mixing refactor, feature, and bugfix work in one pass

---

## Repo-specific note for this project

For triage files in this repo, especially long workers and planning docs:
- prefer extracting one responsibility at a time
- keep evaluation analysis separate from code edits when possible
- use the structural plans in `docs/重构计划/` as stable anchors instead of trying to hold the full plan in memory
- if a worker grows beyond a single clear responsibility, map it by responsibility before editing

---

## Minimal checklist

Before edit:
- [ ] I mapped the file or region
- [ ] I named the edit surface
- [ ] I know what nearby region must be re-read

During edit:
- [ ] I used anchored replacement
- [ ] I stayed within the named surface

After edit:
- [ ] I re-read the touched region
- [ ] I re-read the nearest affected boundary
- [ ] I ran narrow verification appropriate to the change
