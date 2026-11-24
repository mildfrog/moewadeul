package com.moewadeul.common.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class RowLockWriteConnectionBudgetTest {

    private static final int READ_CONNECTIONS = 4;

    @Test
    @DisplayName("[NFR-05] 행 잠금 쓰기 허가를 모두 써도 운영 커넥션 풀에 읽기 요청 몫이 남는다")
    void rowLockWritesLeaveConnectionsForReads() throws IOException {
        Binder binder = prodBinder();
        int maxConcurrentWrites = binder.bindOrCreate("moewadeul.row-lock-writes", RowLockWritesProperties.class)
                .maxConcurrentWrites();
        int poolSize = binder.bind("spring.datasource.hikari.maximum-pool-size", Bindable.of(Integer.class))
                .get();

        assertThat(poolSize - maxConcurrentWrites)
                .as(
                        "허가 %d개에 읽기 몫 %d개를 더한 만큼 application-prod.yml의 spring.datasource.hikari.maximum-pool-size를 두거나 moewadeul.row-lock-writes.max-concurrent-writes를 줄인다",
                        maxConcurrentWrites, READ_CONNECTIONS)
                .isGreaterThanOrEqualTo(READ_CONNECTIONS);
    }

    private static Binder prodBinder() throws IOException {
        var loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = new ArrayList<>();
        sources.addAll(loader.load("prod", new ClassPathResource("application-prod.yml")));
        sources.addAll(loader.load("base", new ClassPathResource("application.yml")));
        return new Binder(ConfigurationPropertySources.from(sources));
    }
}
