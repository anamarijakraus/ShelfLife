package com.shelflife.backend.link;

import java.time.Instant;

public record LinkResponse(Long id, String url, Instant savedAt, Instant expiresAt) {

    public static LinkResponse from(Link link) {
        return new LinkResponse(link.getId(), link.getUrl(), link.getSavedAt(), link.getExpiresAt());
    }
}
