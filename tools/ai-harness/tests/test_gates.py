import unittest
from pathlib import Path

from support import HARNESS_DIR  # noqa: F401
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
                "--console=plain",
            ],
        )

    def test_api가_안_바뀌면_권한_커버리지는_빠진다(self):
        command = selected(["backend/src/main/java/com/moewadeul/review/service/ReviewService.java"])["backend-integration"]
        self.assertNotIn("com.moewadeul.common.security.EndpointAuthorizationCoverageIntegrationTest", command)

    def test_모듈_밖_main_변경은_통합_테스트_전체를_돈다(self):
        command = selected(["backend/src/main/resources/db/migration/V99__x.sql"])["backend-integration"]
        self.assertEqual(command, ["./gradlew", "integrationTest", "--console=plain"])


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
