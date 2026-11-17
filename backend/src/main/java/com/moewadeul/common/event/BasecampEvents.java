package com.moewadeul.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;

public final class BasecampEvents {

    public static final String AGGREGATE_TYPE = "BASECAMP";

    public static final String APPLIED = "BASECAMP_APPLIED";
    public static final String APPROVED = "BASECAMP_APPROVED";
    public static final String REJECTED = "BASECAMP_REJECTED";
    public static final String KICKED = "BASECAMP_KICKED";
    public static final String MEMBER_CHANGED = "BASECAMP_MEMBER_CHANGED";
    public static final String CONFIRMED = "BASECAMP_CONFIRMED";
    public static final String CANCELED = "BASECAMP_CANCELED";
    public static final String COMPLETED = "BASECAMP_COMPLETED";

    private BasecampEvents() {}

    public record AppliedPayload(long applicationId, long applicantId, long leaderId) {}

    public record DecisionPayload(long applicationId, long applicantId, long basecampId) {}

    public record KickedPayload(long basecampId, long memberId, String reason) {}

    /**
     * automatic은 자동 처리(출발 전날 0시 확정·취소, 종료 다음 날 완료)가 만든 이벤트인지다. 한 번에 수천 통이 생기므로 알림 메일을 대량으로 보낸다.
     * startDate는 확정·취소 이벤트의 출발일이다. 출발일이 지나면 그 메일은 쓸모가 없어 보내지 않는다(EV-09).
     * 두 필드는 2026-11-17에 더했다. 그 전에 기록된 행에는 없고, 읽으면 false와 null이다. 기본값이면 JSON에 쓰지 않아 옛 모양과 같다.
     */
    public record MembersPayload(
            long basecampId,
            List<Long> memberIds,
            @JsonInclude(JsonInclude.Include.NON_DEFAULT) Boolean automatic,
            @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate startDate) {

        // 옛 행에는 automatic이 없다. Jackson은 없는 기본 타입 필드를 실패로 보므로 래퍼로 받고 false로 바꾼다.
        public MembersPayload {
            automatic = Boolean.TRUE.equals(automatic);
        }

        public MembersPayload(long basecampId, List<Long> memberIds) {
            this(basecampId, memberIds, false, null);
        }
    }
}
