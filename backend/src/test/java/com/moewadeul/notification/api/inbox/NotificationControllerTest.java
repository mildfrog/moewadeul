package com.moewadeul.notification.api.inbox;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.paging.CursorSlice;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.trace.TraceIdFilter;
import com.moewadeul.notification.application.inbox.NotificationItem;
import com.moewadeul.notification.application.inbox.NotificationQueryService;
import com.moewadeul.notification.application.inbox.NotificationReadService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(NotificationController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class NotificationControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String PATH = "/api/me/notifications";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private NotificationQueryService notificationQueryService;

    @MockitoBean
    private NotificationReadService notificationReadService;

    @Test
    @DisplayName("[F-20] 로그인하지 않으면 401 AUTHENTICATION_REQUIRED이고 서비스를 부르지 않는다")
    void anonymousIsUnauthorized() {
        MvcTestResult list = mvc.get().uri(PATH).exchange();
        MvcTestResult count = mvc.get().uri(PATH + "/unread-count").exchange();

        assertThat(list).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(list).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(count).hasStatus(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(notificationQueryService, notificationReadService);
    }

    @Test
    @DisplayName("[F-20] 목록은 content·size·hasNext·nextCursor 형식이고 cursor와 size를 생략하면 null과 20이다")
    void listReturnsCursorFormat() {
        Instant createdAt = Instant.parse("2026-10-05T03:00:00Z");
        when(notificationQueryService.list(MEMBER_ID, null, 20))
                .thenReturn(new CursorSlice<>(
                        List.of(new NotificationItem(
                                11L, "BASECAMP_APPROVED", "합류 승인", "승인됐습니다.", "/basecamps/3", null, createdAt)),
                        20,
                        true,
                        11L));

        MvcTestResult result = mvc.get().uri(PATH).with(login()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [
                    {
                      "notificationId": 11,
                      "type": "BASECAMP_APPROVED",
                      "title": "합류 승인",
                      "body": "승인됐습니다.",
                      "link": "/basecamps/3",
                      "readAt": null,
                      "createdAt": "2026-10-05T03:00:00Z"
                    }
                  ],
                  "size": 20,
                  "hasNext": true,
                  "nextCursor": 11
                }
                """);
    }

    @Test
    @DisplayName("[F-20] cursor와 size를 보내면 그대로 서비스에 넘긴다")
    void listPassesCursorAndSize() {
        when(notificationQueryService.list(MEMBER_ID, 11L, 50))
                .thenReturn(new CursorSlice<>(List.of(), 50, false, null));

        MvcTestResult result =
                mvc.get().uri(PATH + "?cursor=11&size=50").with(login()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.nextCursor").isNull();
        verify(notificationQueryService).list(MEMBER_ID, 11L, 50);
    }

    @Test
    @DisplayName("[F-20] 옛 클라이언트가 보내는 page 파라미터는 무시하고 첫 쪽으로 읽는다")
    void listIgnoresLegacyPage() {
        when(notificationQueryService.list(MEMBER_ID, null, 20))
                .thenReturn(new CursorSlice<>(List.of(), 20, false, null));

        MvcTestResult result = mvc.get().uri(PATH + "?page=3").with(login()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        verify(notificationQueryService).list(MEMBER_ID, null, 20);
    }

    @Test
    @DisplayName("[F-20] size가 51이거나 0이거나 cursor가 0·음수·숫자가 아니면 400 INVALID_INPUT이다")
    void listRejectsOutOfRangePaging() {
        MvcTestResult tooBig = mvc.get().uri(PATH + "?size=51").with(login()).exchange();
        MvcTestResult zero = mvc.get().uri(PATH + "?size=0").with(login()).exchange();
        MvcTestResult zeroCursor =
                mvc.get().uri(PATH + "?cursor=0").with(login()).exchange();
        MvcTestResult negativeCursor =
                mvc.get().uri(PATH + "?cursor=-1").with(login()).exchange();
        MvcTestResult textCursor =
                mvc.get().uri(PATH + "?cursor=abc").with(login()).exchange();

        for (MvcTestResult result : new MvcTestResult[] {tooBig, zero, zeroCursor, negativeCursor, textCursor}) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        verifyNoInteractions(notificationQueryService);
    }

    @Test
    @DisplayName("[F-20] 안 읽은 알림 수는 count 필드로 응답한다")
    void unreadCountReturnsCount() {
        when(notificationQueryService.countUnread(MEMBER_ID)).thenReturn(3L);

        MvcTestResult result =
                mvc.get().uri(PATH + "/unread-count").with(login()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"count\": 3 }");
    }

    @Test
    @DisplayName("[F-20] 읽음 처리와 모두 읽음 처리는 본문 없이 204로 응답한다")
    void readAndReadAllReturnNoContent() {
        MvcTestResult read =
                mvc.post().uri(PATH + "/5/read").with(login()).with(csrf()).exchange();
        MvcTestResult readAll =
                mvc.post().uri(PATH + "/read-all").with(login()).with(csrf()).exchange();

        assertThat(read).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(readAll).hasStatus(HttpStatus.NO_CONTENT);
        verify(notificationReadService).markRead(MEMBER_ID, 5L);
        verify(notificationReadService).markAllRead(MEMBER_ID);
    }

    @Test
    @DisplayName("[F-20] 없거나 남의 알림을 읽음 처리하면 서비스의 NOT_FOUND가 404로 응답된다")
    void readOfUnknownNotificationIsNotFound() {
        Mockito.doThrow(new BusinessException(CommonErrorCode.NOT_FOUND))
                .when(notificationReadService)
                .markRead(MEMBER_ID, 99L);

        MvcTestResult result =
                mvc.post().uri(PATH + "/99/read").with(login()).with(csrf()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("[F-20] CSRF 토큰 없이 읽음 처리를 요청하면 403이고 서비스를 부르지 않는다")
    void readWithoutCsrfIsForbidden() {
        MvcTestResult read = mvc.post().uri(PATH + "/5/read").with(login()).exchange();
        MvcTestResult readAll = mvc.post().uri(PATH + "/read-all").with(login()).exchange();

        assertThat(read).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(readAll).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(notificationReadService);
    }

    private static RequestPostProcessor login() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", false);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
