package io.blinkhouse.spring.fixture;

import java.util.List;

public class PageViewRepositoryImpl {

    public List<PageView> findHardcoded() {
        return List.of(
                new PageView(1, "IN"),
                new PageView(2, "US")
        );
    }
}
