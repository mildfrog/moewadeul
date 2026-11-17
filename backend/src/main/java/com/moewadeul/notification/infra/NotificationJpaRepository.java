package com.moewadeul.notification.infra;

import com.moewadeul.notification.domain.Notification;
import com.moewadeul.notification.domain.NotificationRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long>, NotificationRepository {

    @Override
    <S extends Notification> List<S> saveAll(Iterable<S> notifications);

    @Override
    Optional<Notification> findByIdAndMemberId(long id, long memberId);

    List<Notification> findByMemberIdOrderByIdDesc(long memberId, Limit limit);

    List<Notification> findByMemberIdAndIdLessThanOrderByIdDesc(long memberId, long cursor, Limit limit);

    @Override
    default List<Notification> findRecent(long memberId, Long cursor, int limit) {
        return cursor == null
                ? findByMemberIdOrderByIdDesc(memberId, Limit.of(limit))
                : findByMemberIdAndIdLessThanOrderByIdDesc(memberId, cursor, Limit.of(limit));
    }

    @Override
    long countByMemberIdAndReadAtIsNull(long memberId);

    @Override
    @Query("SELECT n.dedupKey FROM Notification n WHERE n.dedupKey IN :dedupKeys")
    List<String> findExistingDedupKeys(@Param("dedupKeys") Collection<String> dedupKeys);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.memberId = :memberId AND n.readAt IS NULL")
    int markAllRead(@Param("memberId") long memberId, @Param("now") Instant now);
}
