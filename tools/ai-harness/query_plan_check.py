#!/usr/bin/env python3
"""마이그레이션이 인덱스를 만들거나 지우는데, EXPLAIN으로 쓰는 인덱스를 확인하는 테스트가 함께 바뀌지 않았으면 실패한다.

CLAUDE.md의 DB 규칙("인덱스나 쿼리를 바꾸면 EXPLAIN으로 쓰는 인덱스를 확인하는 통합 테스트를 함께 둔다") 중 인덱스 쪽만 게이트로 옮겼다.
매퍼의 조건·정렬 변경까지 막으면 최근 매퍼를 바꾼 사람 PR 19개 중 14개가 걸렸다. 작은 표의 정리 쿼리에도 EXPLAIN 테스트를
쓰게 되어 신호가 흐려지므로, 그쪽은 규칙으로만 둔다.
사용: query_plan_check.py <기준 ref>  (작업 디렉터리는 검사할 워크트리)
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

MIGRATION_DIR = "backend/src/main/resources/db/migration/"
TEST_DIR = "backend/src/test/"
INDEX_SHAPE = re.compile(r"^[+-]\s*(?!--)(?!.*PRIMARY\s+KEY).*\b(INDEX|KEY)\b", re.IGNORECASE)
EXAMPLE_TEST = "backend/src/test/java/com/moewadeul/review/infra/SpotReviewIndexIntegrationTest.java"


def git(*args: str) -> str:
    return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout


def changed_lines(base: str, directory: str) -> list[tuple[str, str]]:
    lines, path = [], None
    for line in git("diff", "-U0", f"{base}...HEAD", "--", directory).splitlines():
        if line.startswith("+++ "):
            path = line[6:] if line.startswith("+++ b/") else None
        elif path and line[:1] in "+-" and not line.startswith("--- "):
            lines.append((path, line))
    return lines


def triggers(base: str) -> list[tuple[str, str]]:
    return [(p, l) for p, l in changed_lines(base, MIGRATION_DIR) if INDEX_SHAPE.search(l)]


def explain_tests(base: str) -> list[str]:
    changed = git("diff", "--name-only", "--diff-filter=AM", f"{base}...HEAD", "--", TEST_DIR).splitlines()
    return [p for p in changed if "EXPLAIN" in Path(p).read_text(errors="replace")]


def main(base: str) -> int:
    found = triggers(base)
    if not found:
        print("마이그레이션의 인덱스 변경이 없다.")
        return 0
    tests = explain_tests(base)
    if tests:
        print("인덱스 변경과 함께 바뀐 EXPLAIN 테스트: " + ", ".join(tests))
        return 0
    print("마이그레이션이 인덱스를 바꾸는데 EXPLAIN으로 쓰는 인덱스를 확인하는 테스트가 함께 바뀌지 않았다.")
    print("CLAUDE.md DB 규칙: 인덱스나 쿼리를 바꾸면 EXPLAIN으로 쓰는 인덱스를 확인하는 통합 테스트를 함께 둔다.")
    print(f"예: {EXAMPLE_TEST}")
    print("바뀐 줄:")
    for path, line in found[:30]:
        print(f"  {path}: {line.strip()[:160]}")
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
