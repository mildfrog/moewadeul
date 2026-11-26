#!/usr/bin/env python3
"""결함을 하나씩 심은 브랜치에 하네스 센서를 돌려, 결정적 검사와 AI 보안 리뷰가 각각 무엇을 잡는지 센다."""
from __future__ import annotations

import argparse
import json
import secrets
import string
import subprocess
import sys
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from gates import BASE_REF, GATES, GateContext, changed_files, run_gate, security_review  # noqa: E402
from github import git  # noqa: E402
from harness import render  # noqa: E402


def fake_openai_key() -> str:
    # 실제 키 모양이어야 gitleaks 규칙에 걸린다. 저장소에 문자열로 두면 이 파일이 비밀값 검사에 걸리므로 실행할 때 만든다.
    alphabet = string.ascii_letters + string.digits
    part = "".join(secrets.choice(alphabet) for _ in range(74))
    return f"sk-proj-{part}T3BlbkFJ{part[::-1]}"


FAKE_KEY = fake_openai_key()


@dataclass(frozen=True)
class Defect:
    id: str
    title: str
    path: str
    old: str
    new: str


def defects() -> list[Defect]:
    spot = "backend/src/main/java/com/moewadeul/spot/api/detail/SpotDetailController.java"
    detail = """    @GetMapping("/{spotId}")
    SpotDetailResponse findDetail(@PathVariable long spotId) {"""
    return [
        Defect(
            "D0",
            "대조군: 결함 없는 주석 변경",
            "backend/src/main/java/com/moewadeul/community/application/post/CommunityPostWriter.java",
            "        // 고친 내용을 MyBatis로 읽기 전에 DB에 반영한다.",
            "        // 고친 내용을 MyBatis로 다시 읽기 전에 DB에 반영한다.",
        ),
        Defect(
            "D1",
            "새 API를 권한 표에 넣지 않음",
            spot,
            detail,
            """    @GetMapping("/{spotId}/raw")
    SpotDetailResponse findRaw(@PathVariable long spotId) {
        return SpotDetailResponse.from(spotDetailQueryService.findDetail(spotId));
    }

"""
            + detail,
        ),
        Defect(
            "D2",
            "MyBatis 검색어를 ${}로 치환(SQL 주입)",
            "backend/src/main/resources/mapper/community/post/CommunityPostMapper.xml",
            "AND p.title LIKE CONCAT('%', #{condition.keyword}, '%') ESCAPE '\\\\'",
            "AND p.title LIKE '%${condition.keyword}%' ESCAPE '\\\\'",
        ),
        Defect(
            "D3",
            "글 수정·삭제의 작성자 확인 제거(IDOR)",
            "backend/src/main/java/com/moewadeul/community/application/post/CommunityPostWriter.java",
            "        post.requireWrittenBy(memberId);\n",
            "",
        ),
        Defect(
            "D4",
            "가입 로그에 이메일 출력",
            "backend/src/main/java/com/moewadeul/member/application/signup/MemberSignupService.java",
            "        Email email = Email.of(command.email());\n",
            "        Email email = Email.of(command.email());\n"
            '        org.slf4j.LoggerFactory.getLogger(MemberSignupService.class).info("signup requested email={}", command.email());\n',
        ),
        Defect(
            "D5",
            "후기 모듈이 회원 모듈에 직접 의존(모듈 경계)",
            "backend/src/main/java/com/moewadeul/review/application/SpotReviewWriter.java",
            "    private final ReviewImageAttachments reviewImageAttachments;\n",
            "    private final ReviewImageAttachments reviewImageAttachments;\n"
            "    private final com.moewadeul.member.application.MemberSessions memberSessions;\n",
        ),
        Defect(
            "D6",
            "API 키를 코드에 하드코딩",
            "backend/src/main/java/com/moewadeul/aisearch/infra/OpenAiSpotQueryInterpreter.java",
            "public class OpenAiSpotQueryInterpreter implements SpotQueryInterpreter {\n",
            "public class OpenAiSpotQueryInterpreter implements SpotQueryInterpreter {\n\n"
            f'    private static final String FALLBACK_API_KEY = "{FAKE_KEY}";\n',
        ),
        Defect(
            "D7",
            "외부 API 응답 대기 시간 제한 제거",
            "backend/src/main/java/com/moewadeul/aisearch/infra/OpenAiSpotQueryInterpreter.java",
            "        requestFactory.setReadTimeout(properties.readTimeout());\n",
            "",
        ),
        Defect(
            "D8",
            "API 계층에 @Transactional",
            spot,
            detail,
            "    @org.springframework.transaction.annotation.Transactional(readOnly = true)\n" + detail,
        ),
    ]


def plant(repo: Path, worktree: Path, defect: Defect) -> None:
    if worktree.exists():
        git(repo, "worktree", "remove", "--force", str(worktree))
    git(repo, "worktree", "add", "--detach", str(worktree), BASE_REF)
    target = worktree / defect.path
    text = target.read_text()
    if text.count(defect.old) != 1:
        raise SystemExit(f"{defect.id}: 심을 자리가 한 곳이 아니다({text.count(defect.old)}곳). 코드가 바뀌었는지 본다")
    target.write_text(text.replace(defect.old, defect.new))
    git(worktree, "commit", "-qam", f"eval: {defect.id} {defect.title}")


def evaluate(repo: Path, defect: Defect, reviews: int) -> dict:
    worktree = repo.parent / f"{repo.name}-ai" / f"eval-{defect.id}"
    plant(repo, worktree, defect)
    try:
        ctx = GateContext(worktree, changed_files(worktree))
        gates = [asdict(run_gate(g, ctx, g.command(ctx))) for g in GATES if g.when(ctx.changed)]
        prompt = render("security_review", issue_number=0, issue_title=f"평가 {defect.id}", diff_base=BASE_REF)
        runs = []
        for _ in range(reviews):
            review = security_review(worktree, prompt)
            runs.append(
                {
                    "error": review.error,
                    "findings": [asdict(f) for f in review.findings],
                    "hit": any(f.file and (f.file.endswith(defect.path) or defect.path.endswith(f.file)) for f in review.findings),
                }
            )
        return {"defect": asdict(defect), "gates": gates, "reviews": runs}
    finally:
        git(repo, "worktree", "remove", "--force", str(worktree))


def table(results: list[dict]) -> str:
    rows = [
        "| 결함 | 결정적 검사 | 실패한 검사 | AI 리뷰 적중 | AI 리뷰 지적 수(회차별) |",
        "|---|---|---|---|---|",
    ]
    for r in results:
        failed = [g["name"] for g in r["gates"] if not g["passed"]]
        hits = sum(run["hit"] for run in r["reviews"])
        counts = ", ".join(str(len(run["findings"])) if not run["error"] else "오류" for run in r["reviews"])
        rows.append(
            f"| {r['defect']['id']} {r['defect']['title']} | {'잡음' if failed else '통과'} | {', '.join(failed) or '-'} "
            f"| {hits}/{len(r['reviews'])} | {counts} |"
        )
    return "\n".join(rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", nargs="*", help="D1 D3처럼 일부만 돈다")
    parser.add_argument("--reviews", type=int, default=3)
    parser.add_argument("--repo-dir", type=Path, default=HERE.parents[2])
    parser.add_argument("--out", type=Path, default=HERE / "results")
    args = parser.parse_args()

    repo = args.repo_dir.resolve()
    git(repo, "fetch", "origin", "develop")
    base = git(repo, "rev-parse", BASE_REF).strip()
    chosen = [d for d in defects() if not args.only or d.id in args.only]
    results = []
    for defect in chosen:
        print(f"[{datetime.now():%H:%M:%S}] {defect.id} {defect.title}", flush=True)
        results.append(evaluate(repo, defect, args.reviews))
        print(table(results[-1:]).splitlines()[-1], flush=True)

    args.out.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y-%m-%d-%H%M")
    raw = json.dumps({"base": base, "results": results}, ensure_ascii=False, indent=2)
    (args.out / f"{stamp}.json").write_text(raw.replace(FAKE_KEY, "sk-proj-(실행 때 만든 가짜 키, 기록에서 지움)"))
    (args.out / f"{stamp}.md").write_text(f"기준 커밋 `{base}`, AI 리뷰 {args.reviews}회\n\n{table(results)}\n")
    print(table(results))


if __name__ == "__main__":
    main()
