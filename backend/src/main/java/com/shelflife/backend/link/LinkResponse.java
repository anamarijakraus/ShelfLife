package com.shelflife.backend.link;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record LinkResponse(Long id, String url, Instant savedAt, Instant expiresAt) {

    public static LinkResponse from(Link link) {
        return new LinkResponse(link.getId(), link.getUrl(), link.getSavedAt(), link.getExpiresAt());
    }

    // expiresAt is reinterpreted here as the permanent-deletion deadline
    // (original active expiresAt + 30 days), per FR-002/FR-003.
    public static LinkResponse forGraveyard(Link link) {
        return new LinkResponse(link.getId(), link.getUrl(), link.getSavedAt(),
                link.getExpiresAt().plus(30, ChronoUnit.DAYS));
    }
}
