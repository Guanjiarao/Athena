#!/usr/bin/env python3
"""Deterministic, network-free prototype for workflow and fixture review."""

import json
import sys
from pathlib import Path

ALLOWED_KINDS = {"RELATED", "QUESTION", "BODY_RECORD", "CYCLE_RECORD", "DEVICE_RECORD"}


def generate(payload: dict) -> dict:
    evidence = payload.get("evidence") or []
    if not evidence:
        raise ValueError("evidence is required")
    forbidden = [item.get("kind") for item in evidence if item.get("kind") not in ALLOWED_KINDS]
    if forbidden:
        raise ValueError("forbidden evidence kind")
    question_only = all(item.get("kind") == "QUESTION" for item in evidence)
    refs = [
        {
            "evidenceId": item["evidenceId"],
            "role": "QUESTION_CONTEXT" if item["kind"] == "QUESTION" else
                    ("CONFIRMED_RECORD" if item["kind"] == "BODY_RECORD" else "OBSERVATION_CONTEXT"),
        }
        for item in evidence
    ]
    return {
        "schemaVersion": "1.0",
        "eligible": True,
        "reasonCode": None,
        "intent": "UNDERSTAND_QUESTION" if question_only else "OBSERVE_PERSONAL_CHANGE",
        "matchedTopicId": None,
        "title": "一个正在了解的问题" if question_only else "一项值得继续观察的身体线索",
        "commonPoint": "这些输入表达了你想继续弄明白的问题。" if question_only else "这些输入都被标记为值得进一步观察。",
        "possibleLink": "目前只存在内容或时间上的初步联系，可以结合后续记录继续观察。",
        "uncertainty": "这些输入不能确认你出现了相同情况，也不能说明原因或形成诊断。",
        "action": {"title": "完成一次观察记录", "instruction": "在接下来 7 天完成一次相关身体记录。", "windowDays": 7},
        "evidence": refs,
        "confidence": 0.65,
        "requiresProfessionalHelpRule": False,
    }


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: prototype.py <fixture.json>", file=sys.stderr)
        return 2
    payload = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    try:
        print(json.dumps(generate(payload), ensure_ascii=False, indent=2))
    except ValueError as exc:
        print(json.dumps({"status": "REJECTED", "reasonCode": str(exc)}, ensure_ascii=False))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
