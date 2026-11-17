package com.moewadeul.notification.api.inbox;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class NotificationApiIntegrationTest {

    private static final String PATH = "/api/me/notifications";
    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private Member owner;
    private Cookie ownerSession;
    private Member other;
    private Cookie otherSession;

    @BeforeEach
    void setUp() {
        owner = memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(PASSWORD)).build());
        other = memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(PASSWORD)).build());
        ownerSession = login(owner);
        otherSession = login(other);
    }

    @Test
    @DisplayName("[F-20] 알림 목록은 최신순이고 다른 회원의 알림은 담지 않으며 size만큼 끊어 hasNext와 nextCursor를 알린다")
    void listIsNewestFirstAndOwnOnly() {
        // given
        long first = insert(owner, "n1", false);
        long second = insert(owner, "n2", false);
        long third = insert(owner, "n3", false);
        insert(other, "n4", false);

        // when
        MvcTestResult all = get(ownerSession, PATH);
        MvcTestResult firstPage = get(ownerSession, PATH + "?size=2");
        MvcTestResult lastPage = get(ownerSession, PATH + "?size=2&cursor=" + nextCursor(firstPage));

        // then
        assertThat(all).hasStatus(HttpStatus.OK);
        assertThat(ids(all)).containsExactly(third, second, first);
        assertThat(all).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(all).bodyJson().extractingPath("$.nextCursor").isNull();
        assertThat(all).bodyJson().doesNotHavePath("$.page");
        assertThat(ids(firstPage)).containsExactly(third, second);
        assertThat(firstPage).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(nextCursor(firstPage)).isEqualTo(second);
        assertThat(ids(lastPage)).containsExactly(first);
        assertThat(lastPage).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(lastPage).bodyJson().extractingPath("$.nextCursor").isNull();
    }

    @Test
    @DisplayName("[F-20] nextCursor를 따라 끝까지 읽으면 내 알림을 중복과 누락 없이 새것부터 모두 받고 다른 회원 알림은 섞이지 않는다")
    void followingCursorsReadsEveryOwnNotificationOnce() {
        // given
        ArrayList<Long> expected = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            expected.add(0, insert(owner, "own" + i, i % 2 == 0));
            insert(other, "other" + i, false);
        }

        // when
        List<Long> seen = new ArrayList<>();
        String cursor = "";
        while (true) {
            MvcTestResult page = get(ownerSession, PATH + "?size=3" + cursor);
            assertThat(page).hasStatus(HttpStatus.OK);
            seen.addAll(ids(page));
            if (!hasNext(page)) {
                assertThat(page).bodyJson().extractingPath("$.nextCursor").isNull();
                break;
            }
            cursor = "&cursor=" + nextCursor(page);
        }

        // then
        assertThat(seen).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("[F-20] 페이지 사이에 새 알림이 와도 다음 쪽은 이미 읽은 알림을 다시 주거나 건너뛰지 않고, 새 알림은 처음부터 다시 읽을 때 맨 앞에 나온다")
    void newNotificationBetweenPagesDoesNotShiftNextPage() {
        // given
        long first = insert(owner, "n1", false);
        long second = insert(owner, "n2", false);
        long third = insert(owner, "n3", false);
        long fourth = insert(owner, "n4", false);

        // when
        MvcTestResult page0 = get(ownerSession, PATH + "?size=2");
        long arrived = insert(owner, "n5", false);
        MvcTestResult page1 = get(ownerSession, PATH + "?size=2&cursor=" + nextCursor(page0));
        MvcTestResult reload = get(ownerSession, PATH + "?size=2");

        // then
        assertThat(ids(page0)).containsExactly(fourth, third);
        assertThat(ids(page1)).containsExactly(second, first);
        assertThat(page1).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(ids(reload)).containsExactly(arrived, fourth);
    }

    @Test
    @DisplayName("[F-20] 가장 오래된 알림의 id나 그보다 작은 cursor는 빈 목록과 hasNext=false를 주고, 아주 큰 cursor는 첫 페이지와 같다")
    void cursorBeyondOldestNotificationGivesEmptyLastPage() {
        // given
        long first = insert(owner, "n1", false);
        long second = insert(owner, "n2", false);

        // when
        MvcTestResult atOldest = get(ownerSession, PATH + "?cursor=" + first);
        MvcTestResult huge = get(ownerSession, PATH + "?cursor=" + Long.MAX_VALUE);

        // then
        assertThat(atOldest).hasStatus(HttpStatus.OK);
        assertThat(ids(atOldest)).isEmpty();
        assertThat(atOldest).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(atOldest).bodyJson().extractingPath("$.nextCursor").isNull();
        assertThat(ids(huge)).containsExactly(second, first);
    }

    @Test
    @DisplayName("[F-20] 옛 클라이언트가 보내는 page 파라미터는 무시하고 첫 쪽을 준다")
    void listIgnoresLegacyPageParameter() {
        // given
        long only = insert(owner, "n1", false);

        // when
        MvcTestResult result = get(ownerSession, PATH + "?page=5");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(ids(result)).containsExactly(only);
    }

    @Test
    @DisplayName("[F-20] 다른 회원의 알림 id를 cursor로 보내도 내 알림만 나온다")
    void otherMembersCursorDoesNotLeakNotifications() {
        // given
        long mine = insert(owner, "n1", false);
        long theirs = insert(other, "n2", false);

        // when
        MvcTestResult result = get(ownerSession, PATH + "?cursor=" + (theirs + 1));

        // then
        assertThat(ids(result)).containsExactly(mine);
    }

    @Test
    @DisplayName("[F-20] cursor는 1 이상의 정수여야 하고, 어기면 400 INVALID_INPUT이다")
    void rejectsBadCursor() {
        for (String bad : new String[] {"0", "-1", "abc"}) {
            MvcTestResult result = get(ownerSession, PATH + "?cursor=" + bad);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
    }

    @Test
    @DisplayName("[F-20] 안 읽은 알림 수는 내 알림 중 읽지 않은 것만 센다")
    void unreadCountIsOwnUnreadOnly() {
        // given
        insert(owner, "n1", false);
        insert(owner, "n2", true);
        insert(other, "n3", false);

        // when
        MvcTestResult result = get(ownerSession, PATH + "/unread-count");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"count\": 1 }");
    }

    @Test
    @DisplayName("[F-20] 다른 회원의 알림이나 없는 알림을 읽음 처리하면 404 NOT_FOUND이고 알림은 그대로다")
    void readOfOthersOrUnknownNotificationIsNotFound() {
        // given
        long othersNotification = insert(other, "n1", false);

        // when
        MvcTestResult others = post(ownerSession, PATH + "/" + othersNotification + "/read");
        MvcTestResult unknown = post(ownerSession, PATH + "/" + (othersNotification + 1000) + "/read");

        // then
        assertThat(others).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(others).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(readAt(othersNotification)).isNull();
    }

    @Test
    @DisplayName("[F-20] 읽음 처리는 204이고 이미 읽은 알림을 다시 읽어도 204이며 처음 읽은 시각을 유지한다")
    void readIsIdempotentAndKeepsFirstReadAt() {
        // given
        long notificationId = insert(owner, "n1", false);
        LocalDateTime firstReadAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        // when
        MvcTestResult first = post(ownerSession, PATH + "/" + notificationId + "/read");
        clock.advance(Duration.ofHours(1));
        MvcTestResult second = post(ownerSession, PATH + "/" + notificationId + "/read");

        // then
        assertThat(first).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(second).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(readAt(notificationId)).isEqualTo(firstReadAt);
        assertThat(get(ownerSession, PATH))
                .bodyJson()
                .extractingPath("$.content[0].readAt")
                .isNotNull();
    }

    @Test
    @DisplayName("[F-20] 모두 읽음 처리하면 204이고 안 읽은 알림 수가 0이 되며 다른 회원의 알림은 그대로다")
    void readAllClearsOwnUnreadOnly() {
        // given
        insert(owner, "n1", false);
        insert(owner, "n2", false);
        long othersNotification = insert(other, "n3", false);

        // when
        MvcTestResult result = post(ownerSession, PATH + "/read-all");

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(get(ownerSession, PATH + "/unread-count"))
                .bodyJson()
                .extractingPath("$.count")
                .isEqualTo(0);
        assertThat(get(otherSession, PATH + "/unread-count"))
                .bodyJson()
                .extractingPath("$.count")
                .isEqualTo(1);
        assertThat(readAt(othersNotification)).isNull();
    }

    @Test
    @DisplayName("[F-20] 로그인하지 않으면 목록과 읽음 처리 모두 401 AUTHENTICATION_REQUIRED다")
    void anonymousIsUnauthorized() {
        MvcTestResult list = mvc.get().uri(PATH).exchange();
        MvcTestResult read = mvc.post().uri(PATH + "/1/read").with(csrf()).exchange();

        assertThat(list).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(read).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(read).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    private long insert(Member member, String dedupKey, boolean read) {
        jdbc.update(
                "INSERT INTO notification (member_id, type, title, body, link, dedup_key, read_at, email_status, created_at) "
                        + "VALUES (?, 'BASECAMP_APPROVED', '합류 승인', '승인됐습니다.', '/basecamps/1', ?, "
                        + (read ? "UTC_TIMESTAMP(6)" : "NULL") + ", 'SENT', UTC_TIMESTAMP(6))",
                member.getId(),
                dedupKey);
        return jdbc.queryForObject("SELECT MAX(id) FROM notification", Long.class);
    }

    private LocalDateTime readAt(long notificationId) {
        return jdbc.queryForObject(
                "SELECT read_at FROM notification WHERE id = ?", LocalDateTime.class, notificationId);
    }

    private MvcTestResult get(Cookie session, String uri) {
        return mvc.get().uri(uri).cookie(session).exchange();
    }

    private MvcTestResult post(Cookie session, String uri) {
        return mvc.post().uri(uri).cookie(session).with(csrf()).exchange();
    }

    private static long nextCursor(MvcTestResult result) {
        return JsonPath.<Number>read(body(result), "$.nextCursor").longValue();
    }

    private static boolean hasNext(MvcTestResult result) {
        return JsonPath.<Boolean>read(body(result), "$.hasNext");
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static List<Long> ids(MvcTestResult result) {
        List<Number> ids = JsonPath.read(body(result), "$.content[*].notificationId");
        return ids.stream().map(Number::longValue).toList();
    }

    private Cookie login(Member member) {
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), PASSWORD);
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }
}
