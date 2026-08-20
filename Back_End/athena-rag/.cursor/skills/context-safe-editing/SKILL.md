---
name: context-safe-editing
description: Edit long files safely when the whole file does not fit comfortably in context by working in scoped chunks, preserving anchors, and separating understanding, patching, and verification. Use when editing large files, long docs, overloaded classes, or whenever you risk losing structure across context windows.
---

# Context-Safe Editing

Use this skill when a file is too long, too dense, or too interconnected to edit safely in one pass.

## Why this exists

Large files create two common failure modes:
- the editor loses track of surrounding structure and makes locally plausible but globally wrong changes
- the editor keeps re-reading huge files and burns context on navigation instead of reasoning

The goal is to keep edits scoped, reversible, and structurally aware.

## Core principle

Never treat a long file as one editing unit unless the task is truly trivial.

Instead, split the work into three layers:
1. **Map the file**
2. **Edit one bounded region at a time**
3. **Re-check integration points after the edit**

## When to use

Use this when:
- a file is long enough that you cannot comfortably hold it all in working memory
- one class or document is taking on too many responsibilities
- you only need to change one section of a much larger file
- repeated re-reading is causing drift or confusion
- exact replacements are failing because context is too broad or unstable

## Standard workflow

### 1. Build a map before editing

First understand the file’s shape:
- top-level sections
- key classes/functions
- responsibilities per region
- likely edit boundary

Do not start patching the first match you see.

### 2. Choose the smallest viable edit surface

Pick one of these edit surfaces:
- one function
- one class subsection
- one document section
- one block delimited by stable headings or signatures

Prefer the smallest surface that can still keep the change coherent.

### 3. Read only the necessary windows

Read:
- the local region to edit
- its immediate callers/callees if needed
- the nearest structural anchors above and below

Avoid re-reading the full file unless the structure is unclear.

### 4. Edit with anchors

When editing existing files:
- replace against stable surrounding text
- keep exact indentation and formatting
- avoid vague replacement targets
- if a block is large, expand the old string until it is uniquely anchored

### 5. Re-read the touched region

After each substantive edit, re-read:
- the edited region
- any directly adjacent region whose behavior may have shifted

This catches accidental truncation, indentation drift, and anchor mismatch.

### 6. Verify only the touched boundary

After code edits, check:
- lints for edited files
- the narrow runtime or test surface connected to the change

Do not jump to full-suite validation unless the task requires it.

## Heuristics I usually use

### For long code files
- identify the orchestration method first
- identify which helpers are pure parsing vs state mutation vs integration
- separate extraction work from behavior change
- move one responsibility at a time

### For long markdown/docs
- map headings first
- rewrite section by section
- keep the outline stable unless restructuring is part of the task
- preserve cross-references and file-local terminology

### For overloaded classes
- first classify methods by responsibility
- decide which responsibility moves out
- avoid mixing extraction with unrelated cleanup

## Guardrails

- Do not rewrite whole files just because they are long
- Do not edit a giant file from memory after one read
- Do not mix structural refactor, behavior change, and validation expansion in one pass unless necessary
- Do not use broad replacements without strong anchors
- Do not claim understanding of a long file without a section map

## Good outputs

A good use of this skill produces:
- a quick map of the long file
- one scoped edit target
- minimal, anchored edits
- a narrow re-read and verification step

See [REFERENCE.md](REFERENCE.md) for concrete workflows and prompt templates.
