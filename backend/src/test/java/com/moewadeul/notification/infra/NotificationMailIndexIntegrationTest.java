package com.moewadeul.notification.infra;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MapperExplain;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.notification.infra.event.NotificationMailMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class NotificationMailIndexIntegrationTest {

    private static final int MEMBERS = 50;
    private static final int SENT = 3000;
    private static final int PENDING_PER_LANE = 300;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("[F-20][NFR-04] 메일 발송 작업은 (email_status, email_bulk, id) 인덱스로 한 줄의 대기 메일을 id 순서대로 정렬 없이 읽는다")
    void mailPickReadsLaneInIdOrderThroughStatusBulkIndex() {
        // given
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < MEMBERS; i++) {
            members.add(memberRepository.saveAndFlush(aMember().build()).getId());
        }
        insert(members, "SENT", false, SENT);
        insert(members, "PENDING", true, PENDING_PER_LANE);
        insert(members, "PENDING", false, PENDING_PER_LANE);
        jdbc.execute("ANALYZE TABLE notification");

        // when
        Map<String, Object> plan = new MapperExplain(sqlSessionFactory, jdbc)
                .rowOf(
                        "notification",
                        NotificationMailMapper.class,
                        "selectDueIdsForUpdate",
                        Map.of("now", MutableClock.DEFAULT_INSTANT, "limit", 20, "bulk", false));

        // then
        assertThat(plan.get("key")).as("plan %s", plan).isEqualTo("idx_notification_email_status_bulk_id");
        assertThat((String) plan.get("Extra")).as("plan %s", plan).doesNotContain("filesort");
    }

    private void insert(List<Long> members, String emailStatus, boolean bulk, int count) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(
                    new Object[] {members.get(i % members.size()), emailStatus + "-" + bulk + "-" + i, emailStatus, bulk
                    });
        }
        jdbc.batchUpdate(
                "INSERT INTO notification (member_id, type, title, body, link, dedup_key, email_status, email_bulk,"
                        + " created_at) VALUES (?, 'BASECAMP_APPROVED', '합류 승인', '승인됐습니다.', '/basecamps/1', ?, ?, ?,"
                        + " UTC_TIMESTAMP(6))",
                rows);
    }
}
