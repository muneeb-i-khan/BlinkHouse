package io.blinkhouse.example.analytics;

import io.blinkhouse.core.annotation.ChColumn;
import io.blinkhouse.core.annotation.ChEngine;
import io.blinkhouse.core.annotation.ChTable;
import io.blinkhouse.core.annotation.Engine;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single page-view event ingested from the front-end.
 *
 * <p>Stored in a {@code ReplacingMergeTree} keyed by session + path so that
 * duplicate events are deduplicated by ClickHouse during background merges.
 * Call {@code ChTemplate.optimize(PageViewEvent.class, false)} after bulk
 * imports to force immediate deduplication.
 */
@ChTable(name = "page_view_events", database = "analytics")
@ChEngine(value = Engine.ReplacingMergeTree, orderBy = {"session_id", "path", "ts"})
public class PageViewEvent {

    @ChColumn(name = "event_id")
    private UUID eventId;

    @ChColumn(name = "session_id")
    private String sessionId;

    @ChColumn(name = "user_id")
    private String userId;

    @ChColumn(name = "path")
    private String path;

    @ChColumn(name = "referrer")
    private String referrer;

    @ChColumn(name = "ts")
    private LocalDateTime ts;

    @ChColumn(name = "duration_ms")
    private long durationMs;

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
