package com.moewadeul.basecamp.infra.lifecycle;

import static com.moewadeul.common.testsupport.SpotFixture.aSpot;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.basecamp.domain.JoinEligibilityPolicy;
import com.moewadeul.basecamp.infra.query.BasecampSearchCondition;
import com.moewadeul.basecamp.infra.query.BasecampSearchMapper;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MapperExplain;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.SpotFixture;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class BasecampStatusIndexIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final int SPOTS = 50;
    private static final int FINISHED = 3000;
    private static final int RECRUITING = 40;
    private static final int DUE = 10;
    private static final String INDEX = "idx_basecamp_status_start_date";

    @Autowired
    private SpotFixture spotFixture;

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    private MapperExplain explain;

    @BeforeEach
    void seed() {
        explain = new MapperExplain(sqlSessionFactory, jdbc);
        long leader = memberFixture.save().getId();
        List<Long> spots = new ArrayList<>();
        for (int i = 0; i < SPOTS; i++) {
            spots.add(spotFixture.insert(aSpot().name("장소" + i).at(37.1 + i * 0.01, 127.1 + i * 0.01)));
        }
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < FINISHED; i++) {
            LocalDate start = TODAY.minusDays(30 + i % 300);
            rows.add(row(leader, spots.get(i % SPOTS), i % 5 == 0 ? "CANCELED" : "COMPLETED", start));
        }
        for (int i = 0; i < RECRUITING; i++) {
            rows.add(row(leader, spots.get(i % SPOTS), "RECRUITING", TODAY.plusDays(1 + i)));
        }
        for (int i = 0; i < DUE; i++) {
            rows.add(row(leader, spots.get(i % SPOTS), "CLOSED", TODAY.minusDays(i % 2)));
        }
        jdbc.batchUpdate(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " created_at, updated_at) VALUES (?, ?, '베이스캠프', '설명', ?, ?, 6, ?, NOW(6), NOW(6))",
                rows);
        jdbc.execute("ANALYZE TABLE basecamp");
    }

    @Test
    @DisplayName("[F-12] 모집 중 목록은 (status, start_date) 인덱스로 모집 중인 베이스캠프만 출발일 순서대로 정렬 없이 읽는다")
    void recruitingListReadsRecruitingInStartDateOrderThroughStatusStartDateIndex() {
        // when
        BasecampSearchCondition condition = new BasecampSearchCondition(
                new BasecampSearchCondition.Area(37.0, 127.0, 38.0, 128.0),
                null,
                null,
                null,
                false,
                Set.of(),
                Set.of(),
                null,
                JoinEligibilityPolicy.BASE_TRUST_LEVEL.level(),
                false,
                false);
        Map<String, Object> plan = explain.rowOf(
                "b",
                BasecampSearchMapper.class,
                "selectRecruiting",
                Map.of("condition", condition, "offset", 0L, "limit", 20));

        // then
        assertThat(plan.get("key")).as("plan %s", plan).isEqualTo(INDEX);
        assertThat(((Number) plan.get("rows")).longValue()).as("plan %s", plan).isLessThan(FINISHED);
    }

    @Test
    @DisplayName("[F-14][BC-10] 출발 자동 전이는 (status, start_date) 인덱스로 출발일이 된 모집 중·마감 베이스캠프만 읽는다")
    void departureScanReadsOnlyDueRowsThroughStatusStartDateIndex() {
        // when
        Map<String, Object> plan = explain.rowOf(
                "basecamp",
                BasecampAutoTransitionMapper.class,
                "selectDueForDepartureIds",
                Map.of("dueStartDate", TODAY, "afterId", 0L, "limit", 100));

        // then
        assertThat(plan.get("key")).as("plan %s", plan).isEqualTo(INDEX);
        assertThat(((Number) plan.get("rows")).longValue()).as("plan %s", plan).isLessThan(FINISHED);
    }

    private static Object[] row(long leader, long spot, String status, LocalDate start) {
        return new Object[] {leader, spot, start, start.plusDays(1), status};
    }
}
