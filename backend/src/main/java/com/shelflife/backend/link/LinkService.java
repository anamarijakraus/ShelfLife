package com.shelflife.backend.link;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class LinkService {

    static final long EXPIRY_HOURS = 168;
    static final long GRAVEYARD_DAYS = 30;

    private static final Pattern SCHEME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://");

    private final LinkRepository linkRepository;

    public LinkService(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    public Link createLink(String rawUrl) {
        String normalizedUrl = normalize(rawUrl);

        // Truncated to millisecond precision so stored values compare consistently
        // with query parameters (H2/JDBC timestamp binding does not reliably
        // preserve sub-millisecond precision), which matters for exact-boundary
        // correctness at the 168-hour mark.
        Instant savedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = savedAt.plus(EXPIRY_HOURS, ChronoUnit.HOURS);

        return linkRepository.save(new Link(normalizedUrl, savedAt, expiresAt));
    }

    public List<Link> listActiveLinks() {
        return linkRepository.findByExpiresAtAfterOrderByExpiresAtAsc(Instant.now());
    }

    public List<Link> listGraveyardLinks() {
        Instant now = Instant.now();
        Instant graveyardThreshold = now.minus(GRAVEYARD_DAYS, ChronoUnit.DAYS);

        linkRepository.deleteByExpiresAtLessThanEqual(graveyardThreshold);

        return linkRepository.findByExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc(now, graveyardThreshold);
    }

    private String normalize(String rawUrl) {
        if (rawUrl == null) {
            throw new InvalidUrlException("The submitted value is not a valid URL.");
        }

        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidUrlException("The submitted value is not a valid URL.");
        }

        String candidate = SCHEME_PATTERN.matcher(trimmed).find() ? trimmed : "https://" + trimmed;

        try {
            URI uri = new URI(candidate);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()
                    || !isDomainLikeHost(uri.getHost())) {
                throw new InvalidUrlException("The submitted value is not a valid URL.");
            }
            return candidate;
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("The submitted value is not a valid URL.");
        }
    }

    // Rejects single-label hosts (e.g. "a", "localhost") per FR-003: this app is for
    // saving real web content, not local or intranet addresses, so the host must have
    // at least one dot separating a label from a TLD-like suffix.
    private static boolean isDomainLikeHost(String host) {
        return host.contains(".") && !host.startsWith(".") && !host.endsWith(".") && !host.contains("..");
    }
}
