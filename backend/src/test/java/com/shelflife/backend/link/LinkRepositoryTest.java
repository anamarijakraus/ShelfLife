package com.shelflife.backend.link;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LinkRepositoryTest {

    @Autowired
    private LinkRepository linkRepository;

    @Test
    void ordersMultipleActiveLinksBySoonestToExpireFirst() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Link expiresSoonest = linkRepository.save(new Link(
                "https://soonest.example.com", now, now.plusSeconds(60)));
        Link expiresMiddle = linkRepository.save(new Link(
                "https://middle.example.com", now, now.plusSeconds(3600)));
        Link expiresLatest = linkRepository.save(new Link(
                "https://latest.example.com", now, now.plusSeconds(7200)));

        List<Link> active = linkRepository.findByExpiresAtAfterOrderByExpiresAtAsc(now);

        assertThat(active).extracting(Link::getId)
                .containsExactly(expiresSoonest.getId(), expiresMiddle.getId(), expiresLatest.getId());
    }

    @Test
    void excludesALinkAtExactly167Hours59MinutesBoundaryCorrectly() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link almostExpired = linkRepository.save(new Link(
                "https://almost-expired.example.com",
                now.minus(167, ChronoUnit.HOURS).minus(59, ChronoUnit.MINUTES),
                now.plusSeconds(60)
        ));

        List<Link> active = linkRepository.findByExpiresAtAfterOrderByExpiresAtAsc(now);

        assertThat(active).extracting(Link::getId).contains(almostExpired.getId());
    }

    @Test
    void excludesALinkAtExactlyThe168HourBoundary() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link exactlyExpired = linkRepository.save(new Link(
                "https://exactly-expired.example.com",
                now.minus(168, ChronoUnit.HOURS),
                now
        ));

        List<Link> active = linkRepository.findByExpiresAtAfterOrderByExpiresAtAsc(now);

        assertThat(active).extracting(Link::getId).doesNotContain(exactlyExpired.getId());
    }

    @Test
    void excludesALinkPastThe168HourBoundary() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link pastExpired = linkRepository.save(new Link(
                "https://past-expired.example.com",
                now.minus(168, ChronoUnit.HOURS).minus(1, ChronoUnit.MINUTES),
                now.minusSeconds(60)
        ));

        List<Link> active = linkRepository.findByExpiresAtAfterOrderByExpiresAtAsc(now);

        assertThat(active).extracting(Link::getId).doesNotContain(pastExpired.getId());
    }

    @Test
    void anExpiredLinksRecordRemainsUnmodifiedInStorageAfterBeingExcluded() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant originalSavedAt = now.minus(170, ChronoUnit.HOURS);
        Instant originalExpiresAt = now.minus(2, ChronoUnit.HOURS);
        Link expired = linkRepository.save(new Link(
                "https://expired-but-persisted.example.com", originalSavedAt, originalExpiresAt));

        List<Link> active = linkRepository.findByExpiresAtAfterOrderByExpiresAtAsc(now);
        assertThat(active).extracting(Link::getId).doesNotContain(expired.getId());

        Link stillInStorage = linkRepository.findById(expired.getId()).orElseThrow();
        assertThat(stillInStorage.getUrl()).isEqualTo("https://expired-but-persisted.example.com");
        assertThat(stillInStorage.getSavedAt()).isEqualTo(originalSavedAt);
        assertThat(stillInStorage.getExpiresAt()).isEqualTo(originalExpiresAt);
    }
}
