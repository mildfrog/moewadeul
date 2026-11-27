package com.moewadeul.notification.infra.event;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class NotificationMailPickLockIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final int MEMBERS = 50;
    private static final int PENDING = 300;
    private static final int BATCH = 20;

    @Autowired
    private NotificationMailMapper mapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-20][NFR-04] 알림 설정 행이 하나도 없어도, 메일을 집은 트랜잭션은 집은 묶음만 잠가서 다른 트랜잭션이 다음 묶음을 집는다")
    void pickLocksOnlyPickedBatchEvenWithoutNotificationSettings() throws Exception {
        // given
        seedPendingMails();
        CountDownLatch picked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // when
        CompletableFuture<List<Long>> first =
                CompletableFuture.supplyAsync(() -> transactionTemplate.execute(status -> {
                    List<Long> ids = ids(mapper.selectDueForUpdate(NOW, BATCH, false, true));
                    picked.countDown();
                    await(release);
                    return ids;
                }));
        assertThat(picked.await(30, TimeUnit.SECONDS)).isTrue();
        List<Long> second =
                transactionTemplate.execute(status -> ids(mapper.selectDueForUpdate(NOW, BATCH, false, true)));
        release.countDown();
        List<Long> firstIds = first.get(30, TimeUnit.SECONDS);

        // then
        assertThat(firstIds).hasSize(BATCH).isSorted();
        assertThat(second).hasSize(BATCH).isSorted().doesNotContainAnyElementsOf(firstIds);
    }

    private void seedPendingMails() {
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < MEMBERS; i++) {
            members.add(memberRepository.saveAndFlush(aMember().build()).getId());
        }
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < PENDING; i++) {
            rows.add(new Object[] {members.get(i % MEMBERS), "pick-" + i});
        }
        jdbc.batchUpdate(
                "INSERT INTO notification (member_id, type, title, body, link, dedup_key, email_status, email_bulk,"
                        + " created_at) VALUES (?, 'BASECAMP_APPROVED', '합류 승인', '승인됐습니다.', '/basecamps/1', ?,"
                        + " 'PENDING', FALSE, UTC_TIMESTAMP(6))",
                rows);
        jdbc.execute("ANALYZE TABLE notification, notification_setting, member");
    }

    private static List<Long> ids(List<NotificationMailRow> rows) {
        return rows.stream().map(NotificationMailRow::notificationId).toList();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
