# Active Task Brief

## Objective
- Prepare the **next structural cut decision** after closing the primaryComplaint regression-recovery line.

## Current state
- The residual `TRIAGE-EXT-V2-035` `primaryComplaint -> null` regression has been recovered.
- Judged artifact closure is preserved: emitted judged JSON/markdown still expose complaint-truth fields/lines.
- Abdominal protection and recovered chest complaint behavior remain stable.
- The last fix was narrow and did not expand `TurnComplaintSemanticsCoordinator.complaintFromContext()`.

## Candidate next cuts

### Candidate A: Planner selected/suppressed gap cleanup
Focus on remaining old planner/downstream failures:
- `033 / 047`: planner selects `PAIN_SEVERITY` instead of expected `VOMITING_PRESENCE`
- `041 / 045`: `suppressedGaps` does not include `BODY_PART`
- `035 / 048`: planner does not select `DYSPNEA_PRESENCE` as expected

This cut should clarify how planner chooses selected gaps and suppressed gaps from stable reducer / answered / pending state without reopening complaint truth ownership.

### Candidate B: Risk semantic layer introduction
Focus on chest-chain risk semantics before planner selection:
- `034 / 049`: `riskDecision.decisionType` remains `TRIGGER_WARNING`
- unresolved dyspnea/risk concerns may need a stable semantic object before planner can choose correctly

This cut should introduce or clarify a risk concern / risk semantic object boundary before deeper planner changes.

## Scope out until a cut is chosen
- Do not reopen worker-thinning.
- Do not reopen primaryComplaint regression-recovery.
- Do not reopen complaint-truth observation/export closure.
- Do not do case-by-case patches.
- Do not change judge or case data.

## Controller decision needed
- Decide whether the next implementation window should target:
  1. planner selected/suppressed gap cleanup first, or
  2. risk semantic layer first.

## Recommended default
- Prefer planner selected/suppressed gap cleanup first if it can be kept narrow, because multiple remaining failures are currently expressed as planner-selected or suppressed-gap mismatches.
- If planner inspection shows it lacks a stable risk semantic input for chest cases, then stop and switch to a risk semantic layer cut rather than patching planner heuristics.

## Done means for this brief
- The controller chooses exactly one next structural cut and rewrites this brief into an executable development task.
