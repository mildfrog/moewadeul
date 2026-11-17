package com.moewadeul.memory.api;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.verification.EmailVerificationIssuer;
import com.moewadeul.member.domain.Member;
import com.moewadeul.memory.domain.MemoryStatus;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.infra.SpotJpaRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class MemoryFeedApiIntegrationTest {

    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";
    private static final String YESTERDAY = "2026-10-04";

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private EmailVerificationIssuer emailVerificationIssuer;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-33][MM-08] 공개 피드는 공개 기록만 새것부터 주고, 정확한 좌표 없이 대략 좌표만 준다")
    void feedHasOnlyPublicMemoriesNewestFirstWithApproximateCoordinates() {
        // given
        Member author = memberFixture.saveWithPassword(VALID_PASSWORD);
        Cookie owner = verifiedSession(author);
        long first = write(owner, "2026-09-01", "PUBLIC", 37.56789, 126.97412, null);
        write(owner, YESTERDAY, "PRIVATE", 37.1, 127.1, null);
        long second = write(owner, YESTERDAY, "PUBLIC", 35.1, 129.1, null);

        // when
        MvcTestResult feed = mvc.get().uri("/api/memories/feed").exchange();

        // then
        assertThat(feed).hasStatus(HttpStatus.OK);
        assertThat(feed).bodyJson().extractingPath("$.content").asList().hasSize(2);
        assertThat(feed).bodyJson().extractingPath("$.content[0].memoryId").isEqualTo((int) second);
        assertThat(feed).bodyJson().extractingPath("$.content[1].memoryId").isEqualTo((int) first);
        assertThat(feed).bodyJson().extractingPath("$.content[1].approxLat").isEqualTo(37.57);
        assertThat(feed).bodyJson().extractingPath("$.content[1].approxLng").isEqualTo(126.97);
        assertThat(feed).bodyJson().doesNotHavePath("$.content[0].lat");
        assertThat(feed).bodyJson().doesNotHavePath("$.content[0].lng");
        assertThat(feed)
                .bodyJson()
                .extractingPath("$.content[0].author.memberId")
                .isEqualTo(author.getId().intValue());
        assertThat(feed).bodyJson().extractingPath("$.content[0].likeCount").isEqualTo(0);
        assertThat(feed).bodyJson().doesNotHavePath("$.content[0].images");
        assertThat(feed).bodyJson().doesNotHavePath("$.content[0].thumbnailUrl");
        assertThat(feed).bodyJson().extractingPath("$.content[0].imageCount").isEqualTo(0);
        assertThat(feed).bodyJson().extractingPath("$.size").isEqualTo(20);
        assertThat(feed).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(feed).bodyJson().extractingPath("$.nextCursor").isNull();
        assertThat(feed).bodyJson().doesNotHavePath("$.page");
    }

    @Test
    @DisplayName("[F-33][MM-08] 피드는 작성자가 봐도 자기 기록에 정확한 좌표를 주지 않는다")
    void feedHidesExactCoordinatesFromAuthorToo() {
        // given
        Cookie owner = verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD));
        write(owner, YESTERDAY, "PUBLIC", 37.56789, 126.97412, null);

        // when
        MvcTestResult feed = mvc.get().uri("/api/memories/feed").cookie(owner).exchange();

        // then
        assertThat(feed).bodyJson().doesNotHavePath("$.content[0].lat");
        assertThat(feed).bodyJson().doesNotHavePath("$.content[0].lng");
        assertThat(feed).bodyJson().extractingPath("$.content[0].approxLat").isEqualTo(37.57);
    }

    @Test
    @DisplayName("[F-33] 피드는 size만큼 끊어 주고 다음 쪽이 있으면 hasNext와 nextCursor를 주며, 마지막 쪽은 false와 null이다")
    void feedPagesWithCursor() {
        // given
        Cookie owner = verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD));
        long oldest = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);
        long middle = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);
        long newest = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);

        // when
        MvcTestResult firstPage = mvc.get().uri("/api/memories/feed?size=2").exchange();
        MvcTestResult lastPage = mvc.get()
                .uri("/api/memories/feed?size=2&cursor=" + nextCursor(firstPage))
                .exchange();
        MvcTestResult exact = mvc.get().uri("/api/memories/feed?size=3").exchange();

        // then
        memoryIds(firstPage).containsExactly((int) newest, (int) middle);
        assertThat(firstPage).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(firstPage).bodyJson().extractingPath("$.nextCursor").isEqualTo((int) middle);
        memoryIds(lastPage).containsExactly((int) oldest);
        assertThat(lastPage).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(lastPage).bodyJson().extractingPath("$.nextCursor").isNull();
        assertThat(exact).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(exact).bodyJson().extractingPath("$.nextCursor").isNull();
    }

    @Test
    @DisplayName("[F-33] nextCursor를 따라 끝까지 읽으면 공개 기록을 중복과 누락 없이 새것부터 모두 받고, 비공개·검토 대기·숨김 기록은 나오지 않는다")
    void feedFollowingCursorsReadsEveryPublicMemoryOnce() throws Exception {
        // given
        Cookie owner = verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD));
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            expected.add(0, (int) write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null));
            write(owner, YESTERDAY, "PRIVATE", 37.1, 127.1, null);
        }
        long pending = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);
        long hidden = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);
        jdbc.update("UPDATE memory SET status = ? WHERE id = ?", MemoryStatus.PENDING_REVIEW.name(), pending);
        jdbc.update("UPDATE memory SET status = ? WHERE id = ?", MemoryStatus.HIDDEN.name(), hidden);

        // when
        List<Integer> seen = new ArrayList<>();
        String cursor = "";
        while (true) {
            MvcTestResult page =
                    mvc.get().uri("/api/memories/feed?size=3" + cursor).exchange();
            assertThat(page).hasStatus(HttpStatus.OK);
            String body = page.getResponse().getContentAsString(StandardCharsets.UTF_8);
            seen.addAll(JsonPath.<List<Integer>>read(body, "$.content[*].memoryId"));
            if (!JsonPath.<Boolean>read(body, "$.hasNext")) {
                assertThat(page).bodyJson().extractingPath("$.nextCursor").isNull();
                break;
            }
            cursor = "&cursor=" + nextCursor(page);
        }

        // then
        assertThat(seen).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("[F-33] 페이지 사이에 새 기록이 올라와도 다음 쪽은 이미 읽은 기록을 다시 주거나 건너뛰지 않고, 새 기록은 처음부터 다시 읽을 때 맨 앞에 나온다")
    void newMemoryBetweenPagesDoesNotShiftNextPage() {
        // given
        Cookie owner = verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD));
        long first = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);
        long second = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);
        long third = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);
        long fourth = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);
        MvcTestResult page0 = mvc.get().uri("/api/memories/feed?size=2").exchange();

        // when
        long arrived = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);
        MvcTestResult page1 = mvc.get()
                .uri("/api/memories/feed?size=2&cursor=" + nextCursor(page0))
                .exchange();
        MvcTestResult reload = mvc.get().uri("/api/memories/feed?size=2").exchange();

        // then
        memoryIds(page0).containsExactly((int) fourth, (int) third);
        memoryIds(page1).containsExactly((int) second, (int) first);
        assertThat(page1).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        memoryIds(reload).containsExactly((int) arrived, (int) fourth);
    }

    @Test
    @DisplayName("[F-33] 가장 오래된 기록의 id나 그보다 작은 cursor는 빈 목록과 hasNext=false를 주고, 아주 큰 cursor는 첫 페이지와 같다")
    void cursorBeyondOldestMemoryGivesEmptyLastPage() {
        // given
        Cookie owner = verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD));
        long first = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);
        long second = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);

        // when
        MvcTestResult atOldest =
                mvc.get().uri("/api/memories/feed?cursor=" + first).exchange();
        MvcTestResult beyond = mvc.get().uri("/api/memories/feed?cursor=1").exchange();
        MvcTestResult huge =
                mvc.get().uri("/api/memories/feed?cursor=" + Long.MAX_VALUE).exchange();

        // then
        for (MvcTestResult result : new MvcTestResult[] {atOldest, beyond}) {
            assertThat(result).hasStatus(HttpStatus.OK);
            memoryIds(result).isEmpty();
            assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
            assertThat(result).bodyJson().extractingPath("$.nextCursor").isNull();
        }
        memoryIds(huge).containsExactly((int) second, (int) first);
    }

    @Test
    @DisplayName("[F-33] 옛 클라이언트가 보내는 page 파라미터는 무시하고 첫 쪽을 준다")
    void feedIgnoresLegacyPageParameter() {
        // given
        Cookie owner = verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD));
        long only = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);

        // when
        MvcTestResult result = mvc.get().uri("/api/memories/feed?page=5").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        memoryIds(result).containsExactly((int) only);
    }

    @Test
    @DisplayName("[F-33] 피드의 size가 50을 넘거나 0이면 400 INVALID_INPUT이다")
    void feedRejectsInvalidSize() {
        MvcTestResult tooBig = mvc.get().uri("/api/memories/feed?size=51").exchange();
        MvcTestResult zero = mvc.get().uri("/api/memories/feed?size=0").exchange();

        assertThat(tooBig).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooBig).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(zero).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("[F-33] 피드의 cursor는 1 이상의 정수여야 하고, 어기면 400 INVALID_INPUT이다")
    void feedRejectsBadCursor() {
        MvcTestResult zero = mvc.get().uri("/api/memories/feed?cursor=0").exchange();
        MvcTestResult negative = mvc.get().uri("/api/memories/feed?cursor=-1").exchange();
        MvcTestResult text = mvc.get().uri("/api/memories/feed?cursor=abc").exchange();

        for (MvcTestResult result : new MvcTestResult[] {zero, negative, text}) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
    }

    @Test
    @DisplayName("[F-33][MM-09] 피드의 likedByMe는 로그인한 사람에게만 있고, 비회원에게는 필드가 없다")
    void feedLikedByMeOnlyForLoggedInViewer() {
        // given
        Cookie owner = verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD));
        Cookie viewer = verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD));
        long memoryId = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, "물가 옆 평지");
        send(mvc.put().uri("/api/memories/" + memoryId + "/like"), viewer);

        // when
        MvcTestResult anonymous = mvc.get().uri("/api/memories/feed").exchange();
        MvcTestResult liker = mvc.get().uri("/api/memories/feed").cookie(viewer).exchange();
        MvcTestResult other = mvc.get().uri("/api/memories/feed").cookie(owner).exchange();

        // then
        assertThat(anonymous).bodyJson().doesNotHavePath("$.content[0].likedByMe");
        assertThat(anonymous)
                .bodyJson()
                .extractingPath("$.content[0].likeCount")
                .isEqualTo(1);
        assertThat(anonymous).bodyJson().extractingPath("$.content[0].excerpt").isEqualTo("물가 옆 평지");
        assertThat(liker).bodyJson().extractingPath("$.content[0].likedByMe").isEqualTo(true);
        assertThat(other).bodyJson().extractingPath("$.content[0].likedByMe").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-33][MM-08] 회원의 추억지도는 공개 기록만 방문일이 늦은 순서로 대략 좌표와 함께 준다")
    void memberMemoriesHavePublicOnlyWithApproximateCoordinates() {
        // given
        Member author = memberFixture.saveWithPassword(VALID_PASSWORD);
        Cookie owner = verifiedSession(author);
        long older = write(owner, "2026-09-01", "PUBLIC", 37.56789, 126.97412, "예전 기록");
        write(owner, YESTERDAY, "PRIVATE", 37.1, 127.1, null);
        long newer = write(owner, YESTERDAY, "PUBLIC", 35.1, 129.1, null);
        write(verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD)), YESTERDAY, "PUBLIC", 36.1, 128.1, null);

        // when
        MvcTestResult anonymous =
                mvc.get().uri("/api/members/" + author.getId() + "/memories").exchange();
        MvcTestResult byAuthor = mvc.get()
                .uri("/api/members/" + author.getId() + "/memories")
                .cookie(owner)
                .exchange();

        // then
        for (MvcTestResult result : new MvcTestResult[] {anonymous, byAuthor}) {
            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(result).bodyJson().extractingPath("$").asList().hasSize(2);
            assertThat(result).bodyJson().extractingPath("$[0].memoryId").isEqualTo((int) newer);
            assertThat(result).bodyJson().extractingPath("$[1].memoryId").isEqualTo((int) older);
            assertThat(result).bodyJson().extractingPath("$[1].approxLat").isEqualTo(37.57);
            assertThat(result).bodyJson().extractingPath("$[1].approxLng").isEqualTo(126.97);
            assertThat(result).bodyJson().extractingPath("$[1].excerpt").isEqualTo("예전 기록");
            assertThat(result).bodyJson().extractingPath("$[1].imageCount").isEqualTo(0);
            assertThat(result).bodyJson().extractingPath("$[1].likeCount").isEqualTo(0);
            assertThat(result).bodyJson().doesNotHavePath("$[0].lat");
            assertThat(result).bodyJson().doesNotHavePath("$[0].lng");
            assertThat(result).bodyJson().doesNotHavePath("$[0].visibility");
            assertThat(result).bodyJson().doesNotHavePath("$[0].thumbnailUrl");
        }
    }

    @Test
    @DisplayName("[F-33] 없는 회원이나 탈퇴한 회원의 추억지도는 404이고, 공개 기록이 없는 회원은 빈 배열이다")
    void memberMemoriesNotFoundForMissingOrWithdrawnMember() {
        // given
        Member withdrawn = memberFixture.saveWithPassword(VALID_PASSWORD);
        jdbc.update("UPDATE member SET status = 'WITHDRAWN' WHERE id = ?", withdrawn.getId());
        Member empty = memberFixture.saveWithPassword(VALID_PASSWORD);

        // when
        MvcTestResult missing = mvc.get().uri("/api/members/999999/memories").exchange();
        MvcTestResult withdrawnResult =
                mvc.get().uri("/api/members/" + withdrawn.getId() + "/memories").exchange();
        MvcTestResult emptyResult =
                mvc.get().uri("/api/members/" + empty.getId() + "/memories").exchange();

        // then
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(withdrawnResult).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(emptyResult).hasStatus(HttpStatus.OK);
        assertThat(emptyResult).bodyJson().extractingPath("$").asList().isEmpty();
    }

    @Test
    @DisplayName("[F-33][MM-07][MM-08] 상세는 작성자에게 정확한 좌표만, 작성자가 아닌 사람과 비회원에게 대략 좌표만 준다")
    void detailShowsExactToAuthorAndApproximateToOthers() {
        // given
        Cookie owner = verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD));
        Cookie other = verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD));
        long memoryId = write(owner, YESTERDAY, "PUBLIC", 37.56789, 126.97412, null);

        // when
        MvcTestResult byAuthor =
                mvc.get().uri("/api/memories/" + memoryId).cookie(owner).exchange();
        MvcTestResult byOther =
                mvc.get().uri("/api/memories/" + memoryId).cookie(other).exchange();
        MvcTestResult byAnonymous = mvc.get().uri("/api/memories/" + memoryId).exchange();

        // then
        assertThat(byAuthor).bodyJson().extractingPath("$.lat").isEqualTo(37.56789);
        assertThat(byAuthor).bodyJson().extractingPath("$.lng").isEqualTo(126.97412);
        assertThat(byAuthor).bodyJson().doesNotHavePath("$.approxLat");
        assertThat(byAuthor).bodyJson().doesNotHavePath("$.approxLng");
        for (MvcTestResult result : new MvcTestResult[] {byOther, byAnonymous}) {
            assertThat(result).bodyJson().doesNotHavePath("$.lat");
            assertThat(result).bodyJson().doesNotHavePath("$.lng");
            assertThat(result).bodyJson().extractingPath("$.approxLat").isEqualTo(37.57);
            assertThat(result).bodyJson().extractingPath("$.approxLng").isEqualTo(126.97);
        }
        assertThat(result(byAnonymous)).doesNotContain("37.56789").doesNotContain("126.97412");
    }

    @Test
    @DisplayName("[F-33][MM-09] 상세의 likeCount는 모두에게, likedByMe는 로그인한 사람에게만 주고 수정 응답에도 같은 필드가 있다")
    void detailHasLikeCountAndLikedByMe() {
        // given
        Cookie owner = verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD));
        Cookie viewer = verifiedSession(memberFixture.saveWithPassword(VALID_PASSWORD));
        long memoryId = write(owner, YESTERDAY, "PUBLIC", 37.1, 127.1, null);
        send(mvc.put().uri("/api/memories/" + memoryId + "/like"), viewer);

        // when
        MvcTestResult anonymous = mvc.get().uri("/api/memories/" + memoryId).exchange();
        MvcTestResult liker =
                mvc.get().uri("/api/memories/" + memoryId).cookie(viewer).exchange();
        MvcTestResult author =
                mvc.get().uri("/api/memories/" + memoryId).cookie(owner).exchange();
        MvcTestResult patched = send(
                mvc.patch()
                        .uri("/api/memories/" + memoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nights\":2}"),
                owner);

        // then
        assertThat(anonymous).bodyJson().extractingPath("$.likeCount").isEqualTo(1);
        assertThat(anonymous).bodyJson().doesNotHavePath("$.likedByMe");
        assertThat(liker).bodyJson().extractingPath("$.likedByMe").isEqualTo(true);
        assertThat(author).bodyJson().extractingPath("$.likedByMe").isEqualTo(false);
        assertThat(patched).hasStatus(HttpStatus.OK);
        assertThat(patched).bodyJson().extractingPath("$.likeCount").isEqualTo(1);
        assertThat(patched).bodyJson().extractingPath("$.likedByMe").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-33] 연결한 장소가 숨겨지면 피드와 회원의 추억지도에서 spot을 뺀다")
    void hiddenSpotIsOmitted() {
        // given
        Member author = memberFixture.saveWithPassword(VALID_PASSWORD);
        Cookie owner = verifiedSession(author);
        long visible = saveSpot();
        long hidden = saveSpot();
        write(owner, YESTERDAY, "PUBLIC", 37.25, 127.25, null, hidden);
        write(owner, "2026-09-01", "PUBLIC", 37.25, 127.25, null, visible);
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", hidden);

        // when
        MvcTestResult feed = mvc.get().uri("/api/memories/feed").exchange();
        MvcTestResult list =
                mvc.get().uri("/api/members/" + author.getId() + "/memories").exchange();

        // then
        assertThat(feed).bodyJson().extractingPath("$.content[0].spot.spotId").isEqualTo((int) visible);
        assertThat(feed).bodyJson().doesNotHavePath("$.content[1].spot");
        assertThat(list).bodyJson().doesNotHavePath("$[0].spot");
        assertThat(list).bodyJson().extractingPath("$[1].spot.name").isEqualTo("능선 끝 평지");
    }

    private static AbstractListAssert<?, List<?>, Object, ObjectAssert<Object>> memoryIds(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.OK);
        return assertThat(result)
                .bodyJson()
                .extractingPath("$.content[*].memoryId")
                .asList();
    }

    private static long nextCursor(MvcTestResult result) {
        return JsonPath.<Number>read(result(result), "$.nextCursor").longValue();
    }

    private static String result(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private long write(Cookie session, String visitedDate, String visibility, double lat, double lng, String content) {
        return write(session, visitedDate, visibility, lat, lng, content, null);
    }

    private long write(
            Cookie session,
            String visitedDate,
            String visibility,
            double lat,
            double lng,
            String content,
            Long spotId) {
        String body = "{\"lat\":%s,\"lng\":%s,\"visitedDate\":\"%s\",\"nights\":1,\"visibility\":\"%s\"%s%s}"
                .formatted(
                        lat,
                        lng,
                        visitedDate,
                        visibility,
                        content == null ? "" : ",\"content\":\"" + content + "\"",
                        spotId == null ? "" : ",\"spotId\":" + spotId);
        MvcTestResult created = send(
                mvc.post()
                        .uri("/api/memories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                session);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return JsonPath.<Integer>read(result(created), "$.memoryId");
    }

    private MvcTestResult send(MockMvcTester.MockMvcRequestBuilder builder, Cookie session) {
        return builder.with(csrf())
                .cookie(session)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .exchange();
    }

    private long saveSpot() {
        Spot spot = Spot.bakji(
                "능선 끝 평지",
                new GeoPoint(37.25, 127.25),
                ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT),
                MutableClock.DEFAULT_INSTANT);
        return spotRepository.save(spot).getId();
    }

    private Cookie verifiedSession(Member member) {
        MvcTestResult login = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), VALID_PASSWORD))
                .exchange();
        assertThat(login).hasStatus(HttpStatus.OK);
        Cookie session = login.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        String code = emailVerificationIssuer
                .issueFor(member.getId(), LOCAL_IP, TestSequence.next())
                .orElseThrow()
                .code();
        MvcTestResult verified = mvc.post()
                .uri("/api/me/email-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code))
                .exchange();
        assertThat(verified).hasStatus(HttpStatus.OK);
        return session;
    }
}
