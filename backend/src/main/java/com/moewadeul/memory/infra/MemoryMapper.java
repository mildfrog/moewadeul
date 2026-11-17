package com.moewadeul.memory.infra;

import com.moewadeul.memory.domain.MemoryVisibility;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryMapper {

    Optional<MemoryRow> selectById(@Param("memoryId") long memoryId, @Param("viewerId") Long viewerId);

    List<MemorySummaryRow> selectByMember(
            @Param("memberId") long memberId, @Param("visibility") MemoryVisibility visibility);

    List<MemoryFeedRow> selectPublicFeed(
            @Param("viewerId") Long viewerId, @Param("cursor") Long cursor, @Param("limit") int limit);

    boolean existsActiveMember(@Param("memberId") long memberId);

    int deleteByMember(@Param("memberId") long memberId);
}
