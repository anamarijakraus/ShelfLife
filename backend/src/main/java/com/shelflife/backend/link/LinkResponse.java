package com.shelflife.backend.link;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record LinkResponse(Long id, String url, Instant savedAt, Instant expiresAt, String title, String faviconUrl) {

    public static LinkResponse from(Link link) {
        return new LinkResponse(link.getId(), link.getUrl(), link.getSavedAt(), link.getExpiresAt(),
                resolveTitle(link), link.getFaviconUrl());
    }

    // expiresAt is reinterpreted here as the permanent-deletion deadline
    // (original active expiresAt + 30 days), per FR-002/FR-003.
    public static LinkResponse forGraveyard(Link link) {
        return new LinkResponse(link.getId(), link.getUrl(), link.getSavedAt(),
                link.getExpiresAt().plus(30, ChronoUnit.DAYS), resolveTitle(link), link.getFaviconUrl());
    }

    // Never null: the retrieved page title when available, otherwise the raw url (FR-005/FR-006).
    // Shared by both factories above so the fallback rule has exactly one implementation.
    private static String resolveTitle(Link link) {
        return link.getPageTitle() != null ? link.getPageTitle() : link.getUrl();
    }
}
