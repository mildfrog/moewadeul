package com.moewadeul.publicdata.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SyncJobRunRepository {

    SyncJobRun save(SyncJobRun run);

    Optional<SyncJobRun> findById(Long id);

    Optional<SyncJobRun> findFirstByJobTypeOrderByIdDesc(SyncJobType jobType);

    List<SyncJobRun> findByStatus(SyncJobStatus status);

    boolean existsByJobTypeAndStatusAndFinishedAtAfter(SyncJobType jobType, SyncJobStatus status, Instant after);

    void flush();
}
