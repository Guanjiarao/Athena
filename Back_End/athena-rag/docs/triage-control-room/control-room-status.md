# Triage Control Room Status

## Current phase
- Residual regression cleanup completed and independently accepted as **Success**

## Active cut
- Ready to leave the primaryComplaint regression-recovery line and choose the next structural cut. Candidate next line: planner selected-gaps / suppressed-gaps cleanup for remaining old failures, especially `DYSPNEA_PRESENCE`, `VOMITING_PRESENCE`, and `BODY_PART` suppression behavior.

## Last decision
- The `TRIAGE-EXT-V2-035` residual `primaryComplaint -> null` regression has been recovered.
- Judged artifact closure remains intact: actual judged JSON/markdown still expose complaint-truth fields/lines.
- Abdominal protection remains stable; chest `034 / 048 / 049` remain recovered.
- The recovery fix stayed narrow: it only added the missing complaint entry variant `胸口有点不舒服 -> 胸部不适` and did not expand `TurnComplaintSemanticsCoordinator.complaintFromContext()`.
- The primaryComplaint regression-recovery line can now be closed.

## Next acting window
- Controller

## Known blockers
- Remaining abdominal failures are old planner/downstream issues:
  - `033 / 047`: planner still selects `PAIN_SEVERITY` instead of expected `VOMITING_PRESENCE`
  - `041 / 045`: `suppressedGaps` still does not include `BODY_PART`
- Remaining chest failures are old risk/planner issues:
  - `035 / 048`: planner still does not select `DYSPNEA_PRESENCE` as expected
  - `034 / 049`: `riskDecision.decisionType` still shows `TRIGGER_WARNING`
- Risk semantic layer has not been opened yet and should be considered after choosing whether planner cleanup can be safely isolated first.

## Notes
- Do not reopen worker-thinning by default.
- Do not reopen complaint-truth observation/export design as the main task.
- PrimaryComplaint truth/recovery work is now stable enough to move on.
- Next high-value decision: choose whether to cut planner selected/suppressed gap behavior first, or open the risk semantic layer before planner changes.
