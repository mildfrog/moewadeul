package com.moewadeul.publicdata.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// 서버는 한 대이고 배포는 옛 컨테이너를 내린 뒤 새 컨테이너를 띄운다. 그래서 기동할 때 RUNNING인 실행 기록은 죽은 프로세스의 것이다.
// staleAfter(30분)를 기다리면 그동안 같은 작업을 시작할 수 없다(TS-019의 OOM 재시작). 스케줄 시작보다 먼저 돈다.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
class SyncJobRunRecovery implements ApplicationListener<ContextRefreshedEvent> {

    private final SyncJobRunService syncJobRunService;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        syncJobRunService.failRunsLeftByPreviousProcess();
    }
}
