package com.shelflife.backend.link;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "links", indexes = {
        @Index(name = "idx_link_expires_at", columnList = "expires_at")
})
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "page_title", length = 512)
    private String pageTitle;

    @Column(name = "title_fetched_at")
    private Instant titleFetchedAt;

    @Column(name = "favicon_url")
    private String faviconUrl;

    // columnDefinition includes an explicit DEFAULT so that Hibernate's ddl-auto=update generates
    // "ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE" against an already-populated table (an
    // upgrade from Features 1-3's schema) — without a DEFAULT, H2 (and most databases) reject a
    // NOT NULL column add on a non-empty table since existing rows have no value to satisfy it.
    @Column(name = "pinned", nullable = false, columnDefinition = "boolean not null default false")
    private boolean pinned = false;

    @Column(name = "pinned_at")
    private Instant pinnedAt;

    protected Link() {
        // required by JPA
    }

    public Link(String url, Instant savedAt, Instant expiresAt) {
        this.url = url;
        this.savedAt = savedAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public Instant getTitleFetchedAt() {
        return titleFetchedAt;
    }

    public void setTitleFetchedAt(Instant titleFetchedAt) {
        this.titleFetchedAt = titleFetchedAt;
    }

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public Instant getPinnedAt() {
        return pinnedAt;
    }

    public void setPinnedAt(Instant pinnedAt) {
        this.pinnedAt = pinnedAt;
    }
}
