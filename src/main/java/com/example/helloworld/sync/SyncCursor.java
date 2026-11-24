package com.example.helloworld.sync;

import com.example.helloworld.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Where the last sync of a polling configuration stopped (page token, watermark, ETag). */
@Entity
@Table(name = "sync_cursors")
public class SyncCursor extends BaseEntity {

    public static final String DEFAULT_KEY = "default";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "polling_configuration_id", nullable = false)
    private PollingConfiguration pollingConfiguration;

    @Column(name = "cursor_key", nullable = false, length = 100)
    private String cursorKey = DEFAULT_KEY;

    @Column(name = "page_token", columnDefinition = "text")
    private String pageToken;

    @Column(name = "high_watermark")
    private Instant highWatermark;

    @Column(length = 255)
    private String etag;

    protected SyncCursor() {}

    public SyncCursor(PollingConfiguration pollingConfiguration, String cursorKey) {
        this.pollingConfiguration = pollingConfiguration;
        this.cursorKey = cursorKey;
    }

    public PollingConfiguration getPollingConfiguration() { return pollingConfiguration; }
    public String getCursorKey() { return cursorKey; }
    public String getPageToken() { return pageToken; }
    public void setPageToken(String pageToken) { this.pageToken = pageToken; }
    public Instant getHighWatermark() { return highWatermark; }
    public void setHighWatermark(Instant highWatermark) { this.highWatermark = highWatermark; }
    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
}
