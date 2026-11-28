import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from support import GIT_ENV, HARNESS_DIR
from gates import GATES, GateContext, parse_review


def selected(files):
    ctx = GateContext(Path("/wt"), files)
    return {g.name: g.command(ctx) for g in GATES if g.when(files) and g.name != "secret-scan"}


class GateSelectionTest(unittest.TestCase):
    def test_프론트만_바뀌면_프론트와_비밀값_검사만_돈다(self):
        files = ["frontend/src/App.tsx"]
        self.assertEqual(list(selected(files)), ["frontend"])
        self.assertIn("secret-scan", [g.name for g in GATES if g.when(files)])

    def test_백엔드_테스트만_바뀌면_단위_테스트만_돈다(self):
        self.assertEqual(list(selected(["backend/src/test/java/com/moewadeul/spot/XTest.java"])), ["backend-unit"])

    def test_모듈마다_tests_인자가_붙고_api가_바뀌면_권한_커버리지가_붙는다(self):
        files = [
            "backend/src/main/java/com/moewadeul/spot/api/SpotController.java",
            "backend/src/main/java/com/moewadeul/review/service/ReviewService.java",
        ]
        command = selected(files)["backend-integration"]
        self.assertEqual(
            command,
            [
                "./gradlew", "integrationTest",
                "--tests", "com.moewadeul.review.*",
                "--tests", "com.moewadeul.spot.*",
                "--tests", "com.moewadeul.common.security.EndpointAuthorizationCoverageIntegrationTest",
                "--rerun", "--console=plain",
            ],
        )

    def test_api가_안_바뀌면_권한_커버리지는_빠진다(self):
        command = selected(["backend/src/main/java/com/moewadeul/review/service/ReviewService.java"])["backend-integration"]
        self.assertNotIn("com.moewadeul.common.security.EndpointAuthorizationCoverageIntegrationTest", command)

    def test_모듈_밖_main_변경은_통합_테스트_전체를_돈다(self):
        command = selected(["backend/src/main/resources/db/migration/V99__x.sql"])["backend-integration"]
        self.assertEqual(command, ["./gradlew", "integrationTest", "--rerun", "--console=plain"])

    def test_모듈_코드와_마이그레이션이_함께_바뀌면_통합_테스트_전체를_돈다(self):
        files = [
            "backend/src/main/java/com/moewadeul/spot/application/SpotService.java",
            "backend/src/main/resources/db/migration/V99__x.sql",
        ]
        self.assertEqual(selected(files)["backend-integration"], ["./gradlew", "integrationTest", "--rerun", "--console=plain"])

    def test_common_모듈이_바뀌면_통합_테스트_전체를_돈다(self):
        command = selected(["backend/src/main/java/com/moewadeul/common/time/ClockConfig.java"])["backend-integration"]
        self.assertEqual(command, ["./gradlew", "integrationTest", "--rerun", "--console=plain"])

    def test_모듈_매퍼_XML만_바뀌면_그_모듈_통합_테스트만_돈다(self):
        command = selected(["backend/src/main/resources/mapper/community/post/CommunityPostMapper.xml"])["backend-integration"]
        self.assertEqual(command, ["./gradlew", "integrationTest", "--tests", "com.moewadeul.community.*", "--rerun", "--console=plain"])

    def test_공용_매퍼_조각이_바뀌면_통합_테스트_전체를_돈다(self):
        command = selected(["backend/src/main/resources/mapper/common/retry/RetryPolicySql.xml"])["backend-integration"]
        self.assertEqual(command, ["./gradlew", "integrationTest", "--rerun", "--console=plain"])


class ReviewParseTest(unittest.TestCase):
    def test_코드_블록으로_감싼_JSON도_읽는다(self):
        review = parse_review('```json\n{"findings":[{"file":"A.java","line":1,"severity":"high","summary":"s","scenario":"c"}]}\n```')
        self.assertIsNone(review.error)
        self.assertEqual(review.findings[0].severity, "high")

    def test_형식이_틀리면_오류로_기록한다(self):
        self.assertIsNotNone(parse_review('{"findings":[{"file":"A"}]}').error)
        self.assertIsNotNone(parse_review("없음").error)


if __name__ == "__main__":
    unittest.main()


class QueryPlanCheckTest(unittest.TestCase):
    """기준 커밋을 origin/develop으로 둔 임시 저장소에서 query_plan_check.py를 실제로 돌린다."""

    MAPPER = "backend/src/main/resources/mapper/spot/SpotMapper.xml"
    MIGRATION = "backend/src/main/resources/db/migration/V99__spot_index.sql"
    EXPLAIN_TEST = "backend/src/test/java/com/moewadeul/spot/infra/SpotIndexIntegrationTest.java"

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.repo = Path(tmp.name)
        self.git("init", "-q", "-b", "develop")
        self.write(self.MIGRATION.replace("V99__spot_index", "V98__spot"), "CREATE TABLE spot (id BIGINT PRIMARY KEY, region VARCHAR(20));\n")
        self.commit("base")
        self.git("update-ref", "refs/remotes/origin/develop", "HEAD")

    def git(self, *args):
        subprocess.run(["git", *args], cwd=self.repo, check=True, capture_output=True, env={**os.environ, **GIT_ENV})

    def write(self, path, text):
        full = self.repo / path
        full.parent.mkdir(parents=True, exist_ok=True)
        full.write_text(text)

    def commit(self, message):
        self.git("add", ".")
        self.git("commit", "-q", "-m", message)

    def check(self):
        return subprocess.run(
            [sys.executable, str(HARNESS_DIR / "query_plan_check.py"), "origin/develop"],
            cwd=self.repo, capture_output=True, text=True,
        )

    def test_인덱스를_만들고_EXPLAIN_테스트가_없으면_실패하고_바뀐_줄을_알린다(self):
        self.write(self.MIGRATION, "-- 지역 검색용\nCREATE INDEX idx_spot_region ON spot (region);\n")
        self.commit("change")
        done = self.check()
        self.assertEqual(done.returncode, 1)
        self.assertIn("CREATE INDEX idx_spot_region", done.stdout)
        self.assertNotIn("지역 검색용", done.stdout)
        self.assertIn("CLAUDE.md DB 규칙", done.stdout)

    def test_인덱스와_함께_EXPLAIN_테스트가_바뀌면_통과한다(self):
        self.write(self.MIGRATION, "CREATE INDEX idx_spot_region ON spot (region);\n")
        self.write(self.EXPLAIN_TEST, 'class T { String q = "EXPLAIN SELECT id FROM spot WHERE region = ?"; }\n')
        self.commit("change")
        self.assertEqual(self.check().returncode, 0)

    def test_EXPLAIN_없는_테스트만_바뀌면_통과로_치지_않는다(self):
        self.write(self.MIGRATION, "ALTER TABLE spot ADD INDEX idx_spot_region (region);\n")
        self.write(self.EXPLAIN_TEST, "class T {}\n")
        self.commit("change")
        self.assertEqual(self.check().returncode, 1)

    def test_기본_키만_있는_새_표나_매퍼_변경은_검사하지_않는다(self):
        self.write(self.MIGRATION, "CREATE TABLE lock_bucket (bucket INT PRIMARY KEY);\n")
        self.write(self.MAPPER, "<select id=\"find\">SELECT id FROM spot WHERE region = #{region}</select>\n")
        self.commit("change")
        self.assertEqual(self.check().returncode, 0)

    def test_마이그레이션이_바뀌면_게이트가_고르고_하네스_쪽_스크립트를_돈다(self):
        command = selected([self.MIGRATION])["query-plan-test"]
        self.assertEqual(command[1], str(HARNESS_DIR / "query_plan_check.py"))
        self.assertNotIn("query-plan-test", selected([self.MAPPER]))
