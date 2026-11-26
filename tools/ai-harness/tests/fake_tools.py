"""테스트용 가짜 gh, claude, docker, gradlew. 호출을 FAKE_CALLS에 한 줄씩 남기고 FAKE_GH_STATE의 JSON을 GitHub 대신 쓴다."""
import json
import os
import re
import shlex
import subprocess
import sys
import urllib.parse


def record(tool, args):
    with open(os.environ["FAKE_CALLS"], "a") as f:
        f.write(shlex.join([tool, *args]).replace("\n", "\\n") + "\n")


def load():
    with open(os.environ["FAKE_GH_STATE"]) as f:
        return json.load(f)


def save(state):
    with open(os.environ["FAKE_GH_STATE"], "w") as f:
        json.dump(state, f)


def next_id(state):
    state["next_id"] += 1
    return state["next_id"]


def gh(args):
    state = load()
    actor = os.environ.get("FAKE_ACTOR", "mildfrog")
    if args[:2] == ["repo", "view"]:
        print(json.dumps({"name": "moewadeul", "owner": {"login": "mildfrog"}}))
    elif args[:2] == ["label", "create"]:
        if args[2] not in state["labels"]:
            state["labels"].append(args[2])
    elif args[:2] == ["pr", "list"]:
        head = args[args.index("--head") + 1]
        print(json.dumps([{"number": p["number"], "url": p["url"]} for p in state["prs"] if p["head"] == head]))
    elif args[:2] == ["pr", "create"]:
        opt = lambda name: args[args.index(name) + 1]
        number = next_id(state)
        url = f"https://github.com/mildfrog/moewadeul/pull/{number}"
        state["prs"].append(
            {"number": number, "url": url, "head": opt("--head"), "base": opt("--base"), "title": opt("--title"), "body": sys.stdin.read()}
        )
        state["comments"][str(number)] = []
        print(url)
    elif args[0] == "api":
        gh_api(state, args[1:], actor)
    else:
        sys.exit(f"가짜 gh가 모르는 명령: {args}")
    save(state)


def gh_api(state, args, actor):
    method, fields, path = None, {}, None
    i = 0
    while i < len(args):
        a = args[i]
        if a in ("-X", "--method"):
            method, i = args[i + 1], i + 2
        elif a in ("-f", "-F"):
            key, value = args[i + 1].split("=", 1)
            fields.setdefault(key, []).append(value)
            i += 2
        elif a.startswith("--"):
            i += 1
        else:
            path, i = a, i + 1
    method = method or ("POST" if fields else "GET")
    path, _, _query = path.partition("?")
    path = re.sub(r"^repos/[^/]+/[^/]+/", "", path)
    out = lambda data: print(json.dumps([data] if "--slurp" in args and isinstance(data, list) else data))

    def issue_json(n):
        item = state["issues"][str(n)]
        return {k: item[k] for k in ("number", "title", "body", "user", "labels")}

    if path == "issues" and method == "GET":
        out([issue_json(n) for n in sorted(state["issues"], key=int)])
    elif m := re.fullmatch(r"issues/(\d+)", path):
        out(issue_json(m.group(1)))
    elif m := re.fullmatch(r"issues/(\d+)/comments", path):
        number = m.group(1)
        if method == "GET":
            out(state["comments"].get(number, []))
        else:
            comment = {"id": next_id(state), "user": {"login": actor}, "body": fields["body"][0]}
            state["comments"].setdefault(number, []).append(comment)
            print(json.dumps(comment))
    elif m := re.fullmatch(r"issues/(\d+)/timeline", path):
        out(state["issues"][m.group(1)]["timeline"])
    elif m := re.fullmatch(r"issues/(\d+)/labels", path):
        item = state["issues"][m.group(1)]
        for name in fields["labels[]"]:
            item["labels"].append({"name": name})
            item["timeline"].append({"event": "labeled", "label": {"name": name}, "actor": {"login": actor}})
        print("[]")
    elif m := re.fullmatch(r"issues/(\d+)/labels/(.+)", path):
        item = state["issues"][m.group(1)]
        name = urllib.parse.unquote(m.group(2))
        item["labels"] = [label for label in item["labels"] if label["name"] != name]
        print("[]")
    elif m := re.fullmatch(r"statuses/(\w+)", path):
        state["statuses"].append({"sha": m.group(1), **{k: v[0] for k, v in fields.items()}})
        print("{}")
    else:
        sys.exit(f"가짜 gh api가 모르는 경로: {method} {path}")


def first_line(text):
    return text.strip().splitlines()[0] if text.strip() else ""


def git_commit(cwd, filename, content):
    full = os.path.join(cwd, filename)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        f.write(content)
    subprocess.run(["git", "add", filename], cwd=cwd, check=True)
    subprocess.run(["git", "commit", "-q", "-m", "feat: 가짜 구현"], cwd=cwd, check=True)


def claude(args):
    prompt = sys.stdin.read()
    task = first_line(prompt)
    record("claude", [task, *args])
    with open(os.environ["FAKE_PROMPTS"], "a") as f:
        f.write(json.dumps({"task": task, "prompt": prompt}, ensure_ascii=False) + "\n")
    result = {
        "# 작업: 계획": "1. SpotController를 고친다\n2. 테스트를 먼저 쓴다",
        "# 작업: 보안 리뷰": os.environ.get("FAKE_REVIEW", '{"findings":[{"file":"A.java","line":3,"severity":"low","summary":"요약|표","scenario":"시나리오"}]}'),
        "# 작업: PR 본문": "## 관련 이슈\nCloses #\n\n## 해결하려는 문제\n가짜",
    }.get(task)
    if os.environ.get("FAKE_CLAUDE_FAIL") == task:
        print(json.dumps({"is_error": True, "result": "가짜 실패"}))
        return
    if task in ("# 작업: 구현", "# 작업: 게이트 실패 수정"):
        existing = subprocess.run(["git", "rev-list", "--count", "HEAD"], capture_output=True, text=True).stdout.strip()
        git_commit(os.getcwd(), f"backend/src/main/java/com/moewadeul/spot/api/Gen{existing}.java", "class Gen {}\n")
        result = "커밋했다"
    print(json.dumps({"is_error": False, "result": result}))


def docker(args):
    if os.environ.get("FAKE_GITLEAKS_FAIL"):
        print("Finding: 가짜 비밀값")
        sys.exit(1)


def gradlew(args):
    if os.environ.get("FAKE_GRADLE_FAIL") and "test" in args:
        print("\n".join(f"line {i}" for i in range(300)))
        print("FAILED: 가짜 테스트 실패")
        sys.exit(1)


if __name__ == "__main__":
    tool, rest = sys.argv[1], sys.argv[2:]
    if tool != "claude":
        record(tool, rest)
    {"gh": gh, "claude": claude, "docker": docker, "gradlew": gradlew}[tool](rest)
