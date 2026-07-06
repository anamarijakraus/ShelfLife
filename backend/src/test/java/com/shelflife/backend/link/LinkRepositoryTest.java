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

        List<Link> active = linkRepository.findByPinnedFalseAndExpiresAtAfterOrderByExpiresAtAsc(now);

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

        List<Link> active = linkRepository.findByPinnedFalseAndExpiresAtAfterOrderByExpiresAtAsc(now);

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

        List<Link> active = linkRepository.findByPinnedFalseAndExpiresAtAfterOrderByExpiresAtAsc(now);

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

        List<Link> active = linkRepository.findByPinnedFalseAndExpiresAtAfterOrderByExpiresAtAsc(now);

        assertThat(active).extracting(Link::getId).doesNotContain(pastExpired.getId());
    }

    @Test
    void ordersMultipleGraveyardLinksBySoonestToBePermanentlyDeletedFirst() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant graveyardThreshold = now.minus(30, ChronoUnit.DAYS);

        Link deletedSoonest = linkRepository.save(new Link(
                "https://soonest.example.com", now.minus(200, ChronoUnit.HOURS), now.minus(29, ChronoUnit.DAYS)));
        Link deletedMiddle = linkRepository.save(new Link(
                "https://middle.example.com", now.minus(200, ChronoUnit.HOURS), now.minus(15, ChronoUnit.DAYS)));
        Link deletedLatest = linkRepository.save(new Link(
                "https://latest.example.com", now.minus(200, ChronoUnit.HOURS), now.minus(1, ChronoUnit.DAYS)));

        List<Link> graveyard = linkRepository.findByPinnedFalseAndExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc(
                now, graveyardThreshold);

        assertThat(graveyard).extracting(Link::getId)
                .containsExactly(deletedSoonest.getId(), deletedMiddle.getId(), deletedLatest.getId());
    }

    @Test
    void bulkDeleteAndGraveyardRangeQueryTreatBoundariesCorrectly() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant graveyardThreshold = now.minus(30, ChronoUnit.DAYS);

        Link almostDue = linkRepository.save(new Link(
                "https://almost-due.example.com", now.minus(200, ChronoUnit.HOURS),
                now.minus(29, ChronoUnit.DAYS).minus(23, ChronoUnit.HOURS).minus(59, ChronoUnit.MINUTES)));
        Link exactlyDue = linkRepository.save(new Link(
                "https://exactly-due.example.com", now.minus(200, ChronoUnit.HOURS),
                now.minus(30, ChronoUnit.DAYS)));
        Link pastDue = linkRepository.save(new Link(
                "https://past-due.example.com", now.minus(200, ChronoUnit.HOURS),
                now.minus(30, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES)));

        linkRepository.deleteByPinnedFalseAndExpiresAtLessThanEqual(graveyardThreshold);
        List<Link> graveyard = linkRepository.findByPinnedFalseAndExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc(
                now, graveyardThreshold);

        assertThat(graveyard).extracting(Link::getId).containsExactly(almostDue.getId());
        assertThat(linkRepository.findById(exactlyDue.getId())).isEmpty();
        assertThat(linkRepository.findById(pastDue.getId())).isEmpty();
    }

    @Test
    void permanentDeletionActuallyRemovesTheRowRatherThanJustFilteringIt() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link overdue = linkRepository.save(new Link(
                "https://overdue.example.com", now.minus(200, ChronoUnit.HOURS), now.minus(31, ChronoUnit.DAYS)));
        long countBefore = linkRepository.count();

        linkRepository.deleteByPinnedFalseAndExpiresAtLessThanEqual(now.minus(30, ChronoUnit.DAYS));

        assertThat(linkRepository.count()).isEqualTo(countBefore - 1);
        assertThat(linkRepository.existsById(overdue.getId())).isFalse();
    }

    @Test
    void deleteThenReadHandlesZeroOneAndManyOverdueRowsCorrectly() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant graveyardThreshold = now.minus(30, ChronoUnit.DAYS);

        // Zero overdue rows: delete is a no-op.
        linkRepository.deleteByPinnedFalseAndExpiresAtLessThanEqual(graveyardThreshold);
        assertThat(linkRepository.count()).isZero();

        // One overdue row.
        Link singleOverdue = linkRepository.save(new Link(
                "https://single-overdue.example.com", now.minus(200, ChronoUnit.HOURS), now.minus(31, ChronoUnit.DAYS)));
        linkRepository.deleteByPinnedFalseAndExpiresAtLessThanEqual(graveyardThreshold);
        assertThat(linkRepository.existsById(singleOverdue.getId())).isFalse();

        // Many overdue rows at once, alongside one that must survive.
        Link overdueA = linkRepository.save(new Link(
                "https://overdue-a.example.com", now.minus(200, ChronoUnit.HOURS), now.minus(32, ChronoUnit.DAYS)));
        Link overdueB = linkRepository.save(new Link(
                "https://overdue-b.example.com", now.minus(200, ChronoUnit.HOURS), now.minus(33, ChronoUnit.DAYS)));
        Link stillInGraveyard = linkRepository.save(new Link(
                "https://still-in-graveyard.example.com", now.minus(200, ChronoUnit.HOURS), now.minus(5, ChronoUnit.DAYS)));

        linkRepository.deleteByPinnedFalseAndExpiresAtLessThanEqual(graveyardThreshold);

        assertThat(linkRepository.existsById(overdueA.getId())).isFalse();
        assertThat(linkRepository.existsById(overdueB.getId())).isFalse();
        assertThat(linkRepository.existsById(stillInGraveyard.getId())).isTrue();
    }

    @Test
    void anExpiredLinksRecordRemainsUnmodifiedInStorageAfterBeingExcluded() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant originalSavedAt = now.minus(170, ChronoUnit.HOURS);
        Instant originalExpiresAt = now.minus(2, ChronoUnit.HOURS);
        Link expired = linkRepository.save(new Link(
                "https://expired-but-persisted.example.com", originalSavedAt, originalExpiresAt));

        List<Link> active = linkRepository.findByPinnedFalseAndExpiresAtAfterOrderByExpiresAtAsc(now);
        assertThat(active).extracting(Link::getId).doesNotContain(expired.getId());

        Link stillInStorage = linkRepository.findById(expired.getId()).orElseThrow();
        assertThat(stillInStorage.getUrl()).isEqualTo("https://expired-but-persisted.example.com");
        assertThat(stillInStorage.getSavedAt()).isEqualTo(originalSavedAt);
        assertThat(stillInStorage.getExpiresAt()).isEqualTo(originalExpiresAt);
    }

    @Test
    void aPinnedLinkIsExcludedFromTheActiveListQueryEvenIfOtherwiseEligible() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link pinned = new Link("https://pinned-active.example.com", now, now.plusSeconds(3600));
        pinned.setPinned(true);
        pinned.setPinnedAt(now);
        linkRepository.save(pinned);

        List<Link> active = linkRepository.findByPinnedFalseAndExpiresAtAfterOrderByExpiresAtAsc(now);

        assertThat(active).extracting(Link::getId).doesNotContain(pinned.getId());
    }

    @Test
    void aPinnedLinkIsExcludedFromTheGraveyardListQueryEvenIfOtherwiseEligible() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant graveyardThreshold = now.minus(30, ChronoUnit.DAYS);
        Link pinned = new Link("https://pinned-graveyard.example.com", now.minus(200, ChronoUnit.HOURS), now.minus(1, ChronoUnit.DAYS));
        pinned.setPinned(true);
        pinned.setPinnedAt(now);
        linkRepository.save(pinned);

        List<Link> graveyard = linkRepository.findByPinnedFalseAndExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc(
                now, graveyardThreshold);

        assertThat(graveyard).extracting(Link::getId).doesNotContain(pinned.getId());
    }

    @Test
    void aPinnedLinkSurvivesTheAutomaticSweepEvenPastThe30DayThreshold() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant graveyardThreshold = now.minus(30, ChronoUnit.DAYS);
        Link pinned = new Link("https://pinned-overdue.example.com", now.minus(200, ChronoUnit.HOURS), now.minus(60, ChronoUnit.DAYS));
        pinned.setPinned(true);
        pinned.setPinnedAt(now);
        linkRepository.save(pinned);

        linkRepository.deleteByPinnedFalseAndExpiresAtLessThanEqual(graveyardThreshold);

        assertThat(linkRepository.findById(pinned.getId())).isPresent();
    }

    @Test
    void findByPinnedTrueOrdersMultiplePinnedLinksByPinnedAtDescending() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link pinnedFirst = new Link("https://pinned-first.example.com", now, now.plusSeconds(3600));
        pinnedFirst.setPinned(true);
        pinnedFirst.setPinnedAt(now.minusSeconds(60));
        linkRepository.save(pinnedFirst);

        Link pinnedSecond = new Link("https://pinned-second.example.com", now, now.plusSeconds(3600));
        pinnedSecond.setPinned(true);
        pinnedSecond.setPinnedAt(now);
        linkRepository.save(pinnedSecond);

        List<Link> favorites = linkRepository.findByPinnedTrueOrderByPinnedAtDesc();

        assertThat(favorites).extracting(Link::getId).containsExactly(pinnedSecond.getId(), pinnedFirst.getId());
    }
}
