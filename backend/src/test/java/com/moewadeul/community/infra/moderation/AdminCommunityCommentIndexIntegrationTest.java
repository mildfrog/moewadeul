package com.moewadeul.community.infra.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MapperExplain;
import com.moewadeul.community.domain.comment.CommunityCommentStatus;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class AdminCommunityCommentIndexIntegrationTest {

    private static final int ACTIVE = 3000;
    private static final int HIDDEN = 20;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("[F-29][CM-08] 관리자 댓글 검토 목록은 status 인덱스로 그 상태의 댓글만 읽고 표 전체를 훑지 않는다")
    void adminCommentListReadsOnlyThatStatusThroughStatusIndex() {
        // given
        long member = new ProgramApplyFixture(jdbc, memberRepository).saveMember();
        jdbc.update(
                "INSERT INTO community_post (member_id, title, content, status, created_at, updated_at)"
                        + " VALUES (?, '제목', '본문', 'ACTIVE', NOW(6), NOW(6))",
                member);
        long post = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < ACTIVE + HIDDEN; i++) {
            rows.add(new Object[] {post, member, i < HIDDEN ? "HIDDEN" : "ACTIVE"});
        }
        jdbc.batchUpdate(
                "INSERT INTO community_comment (post_id, member_id, content, status, created_at, updated_at)"
                        + " VALUES (?, ?, '댓글', ?, NOW(6), NOW(6))",
                rows);
        jdbc.execute("ANALYZE TABLE community_comment");

        // when
        Map<String, Object> plan = new MapperExplain(sqlSessionFactory, jdbc)
                .rowOf(
                        "c",
                        AdminCommunityMapper.class,
                        "selectComments",
                        Map.of("status", CommunityCommentStatus.HIDDEN, "offset", 0L, "limit", 20));

        // then
        assertThat(plan.get("key")).as("plan %s", plan).isEqualTo("idx_community_comment_status");
        assertThat(((Number) plan.get("rows")).longValue()).as("plan %s", plan).isLessThan(ACTIVE);
    }
}
