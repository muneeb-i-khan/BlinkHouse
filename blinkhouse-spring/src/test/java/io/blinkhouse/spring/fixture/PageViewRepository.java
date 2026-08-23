package io.blinkhouse.spring.fixture;

import io.blinkhouse.spring.repository.ClickHouseRepository;

import java.util.List;

public interface PageViewRepository extends ClickHouseRepository<PageView, Integer> {
    List<PageView> findHardcoded();
}
