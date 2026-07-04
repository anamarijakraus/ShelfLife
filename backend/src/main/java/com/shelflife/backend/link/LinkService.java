package com.shelflife.backend.link;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@Service
public class LinkService {

    static final long EXPIRY_HOURS = 168;
    static final long GRAVEYARD_DAYS = 30;

    private static final Pattern SCHEME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://");

    private final LinkRepository linkRepository;
    private final LinkMetadataFetcher linkMetadataFetcher;

    public LinkService(LinkRepository linkRepository, LinkMetadataFetcher linkMetadataFetcher) {
        this.linkRepository = linkRepository;
        this.linkMetadataFetcher = linkMetadataFetcher;
    }

    public Link createLink(String rawUrl) {
        String normalizedUrl = normalize(rawUrl);

        // Truncated to millisecond precision so stored values compare consistently
        // with query parameters (H2/JDBC timestamp binding does not reliably
        // preserve sub-millisecond precision), which matters for exact-boundary
        // correctness at the 168-hour mark.
        Instant savedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = savedAt.plus(EXPIRY_HOURS, ChronoUnit.HOURS);

        Link link = new Link(normalizedUrl, savedAt, expiresAt);
        // Favicon-URL construction is a pure, network-free string operation (research.md §2), so it's
        // safe to compute inline at save time; the title fetch is deliberately NOT called here — it is
        // an outbound network request and must never delay the instant-save flow (FR-009).
        link.setFaviconUrl(linkMetadataFetcher.buildFaviconUrl(normalizedUrl));

        return linkRepository.save(link);
    }

    public List<Link> listActiveLinks() {
        List<Link> links = linkRepository.findByExpiresAtAfterOrderByExpiresAtAsc(Instant.now());
        backfillMetadata(links);
        return links;
    }

    public List<Link> listGraveyardLinks() {
        Instant now = Instant.now();
        Instant graveyardThreshold = now.minus(GRAVEYARD_DAYS, ChronoUnit.DAYS);

        linkRepository.deleteByExpiresAtLessThanEqual(graveyardThreshold);

        List<Link> links = linkRepository.findByExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc(now, graveyardThreshold);
        backfillMetadata(links);
        return links;
    }

    // Idempotent: a missing id is a successful no-op, not an error (contracts/link-metadata-and-delete-api.md §3).
    // deleteById alone would throw for a missing id, so the existence check is what makes this idempotent.
    public void deleteLink(Long id) {
        if (linkRepository.existsById(id)) {
            linkRepository.deleteById(id);
        }
    }

    // Fans out metadata retrieval concurrently (virtual threads) across every link in the result set
    // that has never had it attempted, then persists the enriched rows before the caller maps them to
    // a response — mirrors this project's read-time-computation philosophy (research.md §4).
    private void backfillMetadata(List<Link> links) {
        List<Link> needsBackfill = links.stream()
                .filter(link -> link.getTitleFetchedAt() == null || link.getFaviconUrl() == null)
                .toList();

        if (needsBackfill.isEmpty()) {
            return;
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = needsBackfill.stream()
                    .<Callable<Void>>map(link -> () -> {
                        backfillOne(link);
                        return null;
                    })
                    .toList();
            executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        linkRepository.saveAll(needsBackfill);
    }

    private void backfillOne(Link link) {
        if (link.getTitleFetchedAt() == null) {
            Optional<String> title = linkMetadataFetcher.fetchTitle(link.getUrl());
            link.setPageTitle(title.orElse(null));
            link.setTitleFetchedAt(Instant.now());
        }
        if (link.getFaviconUrl() == null) {
            link.setFaviconUrl(linkMetadataFetcher.buildFaviconUrl(link.getUrl()));
        }
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
