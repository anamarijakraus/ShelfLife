package com.shelflife.backend.link;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class LinkServiceTest {

    @Autowired
    private LinkService linkService;

    @Autowired
    private LinkRepository linkRepository;

    @Test
    void savesAValidUrlWithComputedExpiryExactly168HoursLater() {
        Link link = linkService.createLink("https://example.com");

        assertThat(link.getId()).isNotNull();
        assertThat(link.getUrl()).isEqualTo("https://example.com");
        assertThat(link.getExpiresAt()).isEqualTo(link.getSavedAt().plus(168, ChronoUnit.HOURS));
    }

    @Test
    void normalizesAUrlMissingASchemeByPrependingHttps() {
        Link link = linkService.createLink("example.com");

        assertThat(link.getUrl()).isEqualTo("https://example.com");
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> linkService.createLink("   "))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsInputThatIsNotAValidUrlEvenAfterSchemeNormalization() {
        assertThatThrownBy(() -> linkService.createLink("not a url at all!!"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsASingleLabelHostWithNoDot() {
        assertThatThrownBy(() -> linkService.createLink("https://a"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsLocalhostAsASingleLabelHost() {
        assertThatThrownBy(() -> linkService.createLink("https://localhost"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void acceptsAMultiLabelHost() {
        Link link = linkService.createLink("https://a.com");

        assertThat(link.getUrl()).isEqualTo("https://a.com");
    }

    @Test
    void acceptsAMultiPartTopLevelDomainHost() {
        Link link = linkService.createLink("https://example.co.uk");

        assertThat(link.getUrl()).isEqualTo("https://example.co.uk");
    }

    @Test
    void allowsSubmittingTheSameUrlTwiceAsIndependentLinks() {
        Link first = linkService.createLink("https://example.com");
        Link second = linkService.createLink("https://example.com");

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(linkRepository.findAll()).hasSize(2);
    }

    @Test
    void linkAt167Hours59MinutesIsStillActive() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/almost-expired",
                now.minus(167, ChronoUnit.HOURS).minus(59, ChronoUnit.MINUTES),
                now.plusSeconds(60)
        ));

        List<Link> active = linkRepository.findByPinnedFalseAndExpiresAtAfterOrderByExpiresAtAsc(now);

        assertThat(active).extracting(Link::getId).contains(link.getId());
    }

    @Test
    void linkAtExactly168HoursIsExcluded() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/exactly-expired",
                now.minus(168, ChronoUnit.HOURS),
                now
        ));

        List<Link> active = linkRepository.findByPinnedFalseAndExpiresAtAfterOrderByExpiresAtAsc(now);

        assertThat(active).extracting(Link::getId).doesNotContain(link.getId());
    }

    @Test
    void linkAt168Hours1MinuteIsExcluded() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/past-expired",
                now.minus(168, ChronoUnit.HOURS).minus(1, ChronoUnit.MINUTES),
                now.minusSeconds(60)
        ));

        List<Link> active = linkRepository.findByPinnedFalseAndExpiresAtAfterOrderByExpiresAtAsc(now);

        assertThat(active).extracting(Link::getId).doesNotContain(link.getId());
    }

    @Test
    void linkAtExactly168HoursIsAbsentFromActiveAndPresentInGraveyard() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/just-entered-graveyard",
                now.minus(168, ChronoUnit.HOURS),
                now
        ));

        List<Link> active = linkService.listActiveLinks();
        List<Link> graveyard = linkService.listGraveyardLinks();

        assertThat(active).extracting(Link::getId).doesNotContain(link.getId());
        assertThat(graveyard).extracting(Link::getId).contains(link.getId());
    }

    @Test
    void graveyardMembershipIsComputedFromExpiresAtIndependentOfSavedAt() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // savedAt is deliberately unrelated to the normal savedAt+168h=expiresAt relationship,
        // proving graveyard membership (and its 30-day deadline) depends only on expiresAt.
        Link link = linkRepository.save(new Link(
                "https://example.com/graveyard-membership",
                now.minus(1000, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS)
        ));

        List<Link> graveyard = linkService.listGraveyardLinks();

        assertThat(graveyard).extracting(Link::getId).contains(link.getId());
    }

    @Test
    void linkThatExpiredWhileAppWasClosedIsAlreadyInGraveyardOnNextRead() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/expired-while-closed",
                now.minus(200, ChronoUnit.HOURS),
                now.minus(10, ChronoUnit.HOURS)
        ));

        List<Link> graveyard = linkService.listGraveyardLinks();

        assertThat(graveyard).extracting(Link::getId).contains(link.getId());
    }

    @Test
    void linkAt29Days23Hours59MinutesInGraveyardIsStillIncluded() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/almost-permanently-deleted",
                now.minus(200, ChronoUnit.HOURS),
                now.minus(29, ChronoUnit.DAYS).minus(23, ChronoUnit.HOURS).minus(59, ChronoUnit.MINUTES)
        ));

        List<Link> graveyard = linkService.listGraveyardLinks();

        assertThat(graveyard).extracting(Link::getId).contains(link.getId());
    }

    @Test
    void linkAtExactly30DaysInGraveyardIsExcludedAndDeleted() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/exactly-permanently-deleted",
                now.minus(200, ChronoUnit.HOURS),
                now.minus(30, ChronoUnit.DAYS)
        ));

        List<Link> graveyard = linkService.listGraveyardLinks();

        assertThat(graveyard).extracting(Link::getId).doesNotContain(link.getId());
        assertThat(linkRepository.findById(link.getId())).isEmpty();
    }

    @Test
    void linkAt30Days1MinuteInGraveyardIsExcludedAndDeleted() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/past-permanently-deleted",
                now.minus(200, ChronoUnit.HOURS),
                now.minus(30, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES)
        ));

        List<Link> graveyard = linkService.listGraveyardLinks();

        assertThat(graveyard).extracting(Link::getId).doesNotContain(link.getId());
        assertThat(linkRepository.findById(link.getId())).isEmpty();
    }

    @Test
    void faviconUrlIsPopulatedSynchronouslyAtSaveTime() {
        Link link = linkService.createLink("https://example.com");

        assertThat(link.getFaviconUrl()).isNotNull();
        assertThat(link.getFaviconUrl()).contains("domain=example.com");
    }

    @Test
    void createLinkNeverInvokesTheNetworkCallingTitleFetchMethod() {
        LinkMetadataFetcher spyFetcher = mock(LinkMetadataFetcher.class);
        when(spyFetcher.buildFaviconUrl(anyString())).thenReturn("https://favicon.example/x");
        LinkService serviceWithSpy = new LinkService(linkRepository, spyFetcher);

        serviceWithSpy.createLink("https://example.com/spy-check");

        verify(spyFetcher, never()).fetchTitle(anyString());
        verify(spyFetcher).buildFaviconUrl("https://example.com/spy-check");
    }

    @Test
    void retrievedTitleIsUsedAsTheResponseTitleAfterBackfill() {
        LinkMetadataFetcher mockFetcher = mock(LinkMetadataFetcher.class);
        when(mockFetcher.fetchTitle(anyString())).thenReturn(Optional.of("A Real Title"));
        when(mockFetcher.buildFaviconUrl(anyString())).thenReturn("https://favicon.example/x");
        LinkService serviceWithMock = new LinkService(linkRepository, mockFetcher);

        Link created = serviceWithMock.createLink("https://example.com/needs-title");
        List<Link> active = serviceWithMock.listActiveLinks();

        Link backfilled = active.stream().filter(l -> l.getId().equals(created.getId())).findFirst().orElseThrow();
        assertThat(backfilled.getPageTitle()).isEqualTo("A Real Title");
        assertThat(backfilled.getTitleFetchedAt()).isNotNull();
        assertThat(LinkResponse.from(backfilled).title()).isEqualTo("A Real Title");
    }

    @Test
    void missingTitleFallsBackToUrlInTheResponse() {
        LinkMetadataFetcher mockFetcher = mock(LinkMetadataFetcher.class);
        when(mockFetcher.fetchTitle(anyString())).thenReturn(Optional.empty());
        when(mockFetcher.buildFaviconUrl(anyString())).thenReturn(null);
        LinkService serviceWithMock = new LinkService(linkRepository, mockFetcher);

        Link created = serviceWithMock.createLink("https://example.com/no-title");
        serviceWithMock.listActiveLinks();

        Link reloaded = linkRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getPageTitle()).isNull();
        assertThat(LinkResponse.from(reloaded).title()).isEqualTo("https://example.com/no-title");
    }

    @Test
    void aPreExistingLinkWithNoMetadataAttemptIsBackfilledOnItsNextListRead() {
        LinkMetadataFetcher mockFetcher = mock(LinkMetadataFetcher.class);
        when(mockFetcher.fetchTitle(anyString())).thenReturn(Optional.of("Backfilled Title"));
        when(mockFetcher.buildFaviconUrl(anyString())).thenReturn("https://favicon.example/x");
        LinkService serviceWithMock = new LinkService(linkRepository, mockFetcher);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link preExisting = linkRepository.save(new Link("https://pre-existing.example.com", now, now.plusSeconds(3600)));
        assertThat(preExisting.getTitleFetchedAt()).isNull();
        assertThat(preExisting.getFaviconUrl()).isNull();

        List<Link> active = serviceWithMock.listActiveLinks();

        Link backfilled = active.stream().filter(l -> l.getId().equals(preExisting.getId())).findFirst().orElseThrow();
        assertThat(backfilled.getTitleFetchedAt()).isNotNull();
        assertThat(backfilled.getFaviconUrl()).isNotNull();
        assertThat(backfilled.getPageTitle()).isEqualTo("Backfilled Title");
    }

    @Test
    void aLinkIsNeverReFetchedOnceTitleFetchedAtIsSet() {
        LinkMetadataFetcher mockFetcher = mock(LinkMetadataFetcher.class);
        when(mockFetcher.fetchTitle(anyString())).thenReturn(Optional.empty());
        when(mockFetcher.buildFaviconUrl(anyString())).thenReturn("https://favicon.example/x");
        LinkService serviceWithMock = new LinkService(linkRepository, mockFetcher);

        serviceWithMock.createLink("https://example.com/fetch-once");
        serviceWithMock.listActiveLinks();
        serviceWithMock.listActiveLinks();

        verify(mockFetcher, times(1)).fetchTitle("https://example.com/fetch-once");
    }

    @Test
    void deletingAnActiveLinkRemovesIt() {
        Link link = linkService.createLink("https://example.com/to-delete-active");

        linkService.deleteLink(link.getId());

        assertThat(linkRepository.existsById(link.getId())).isFalse();
    }

    @Test
    void deletingAGraveyardLinkRemovesIt() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/to-delete-graveyard", now.minus(200, ChronoUnit.HOURS), now.minus(1, ChronoUnit.DAYS)));

        linkService.deleteLink(link.getId());

        assertThat(linkRepository.existsById(link.getId())).isFalse();
    }

    @Test
    void deletingANonExistentIdIsANoOpRatherThanAnError() {
        long nonExistentId = 999_999L;
        assertThat(linkRepository.existsById(nonExistentId)).isFalse();

        linkService.deleteLink(nonExistentId);

        assertThat(linkRepository.existsById(nonExistentId)).isFalse();
    }

    @Test
    void deletingOneLinkLeavesEveryOtherLinksExpiryOrderAndCountUnaffected() {
        Link survivorA = linkService.createLink("https://example.com/survivor-a");
        Link toDelete = linkService.createLink("https://example.com/doomed");
        Link survivorB = linkService.createLink("https://example.com/survivor-b");
        long countBefore = linkRepository.count();

        linkService.deleteLink(toDelete.getId());

        assertThat(linkRepository.count()).isEqualTo(countBefore - 1);
        Link reloadedA = linkRepository.findById(survivorA.getId()).orElseThrow();
        Link reloadedB = linkRepository.findById(survivorB.getId()).orElseThrow();
        assertThat(reloadedA.getExpiresAt()).isEqualTo(survivorA.getExpiresAt());
        assertThat(reloadedB.getExpiresAt()).isEqualTo(survivorB.getExpiresAt());
        assertThat(linkService.listActiveLinks()).extracting(Link::getId)
                .containsExactly(survivorA.getId(), survivorB.getId());
    }

    @Test
    void pinningAnActiveLinkRemovesItFromTheActiveList() {
        Link link = linkService.createLink("https://example.com/pin-from-active");

        linkService.pinLink(link.getId());

        assertThat(linkService.listActiveLinks()).extracting(Link::getId).doesNotContain(link.getId());
        assertThat(linkRepository.findById(link.getId()).orElseThrow().isPinned()).isTrue();
    }

    @Test
    void pinningAGraveyardLinkRemovesItFromTheGraveyard() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/pin-from-graveyard", now.minus(200, ChronoUnit.HOURS), now.minus(1, ChronoUnit.DAYS)));

        linkService.pinLink(link.getId());

        assertThat(linkService.listGraveyardLinks()).extracting(Link::getId).doesNotContain(link.getId());
        assertThat(linkRepository.findById(link.getId()).orElseThrow().isPinned()).isTrue();
    }

    @Test
    void pinningAnAlreadyPinnedLinkIsATrueNoOpPinnedAtUnchanged() {
        Link link = linkService.createLink("https://example.com/already-pinned");
        linkService.pinLink(link.getId());
        Instant firstPinnedAt = linkRepository.findById(link.getId()).orElseThrow().getPinnedAt();

        linkService.pinLink(link.getId());

        assertThat(linkRepository.findById(link.getId()).orElseThrow().getPinnedAt()).isEqualTo(firstPinnedAt);
    }

    @Test
    void pinningANonExistentIdIsANoOp() {
        long nonExistentId = 999_999L;

        linkService.pinLink(nonExistentId);

        assertThat(linkRepository.existsById(nonExistentId)).isFalse();
    }

    @Test
    void aPinnedLinkSurvivesPastBoth168HourAnd30DayBoundariesWithoutBeingSweptOrExcluded() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/pinned-survives-boundaries",
                now.minus(1000, ChronoUnit.HOURS),
                now.minus(60, ChronoUnit.DAYS)));
        linkService.pinLink(link.getId());

        // Re-read active and graveyard (which triggers the graveyard's automatic sweep) to prove
        // the pinned row is never excluded/deleted despite its stale expiresAt implying it is long
        // past both the 168-hour and 30-day thresholds (research.md §1, the correctness-critical case).
        linkService.listActiveLinks();
        linkService.listGraveyardLinks();

        assertThat(linkRepository.findByPinnedTrueOrderByPinnedAtDesc()).extracting(Link::getId).contains(link.getId());
        assertThat(linkRepository.findById(link.getId())).isPresent();
    }

    @Test
    void listFavoriteLinksOrdersMultiplePinnedLinksByPinnedAtDescending() throws InterruptedException {
        Link older = linkService.createLink("https://example.com/pinned-older");
        linkService.pinLink(older.getId());
        Thread.sleep(5);
        Link newer = linkService.createLink("https://example.com/pinned-newer");
        linkService.pinLink(newer.getId());

        List<Link> favorites = linkService.listFavoriteLinks();

        assertThat(favorites).extracting(Link::getId).containsExactly(newer.getId(), older.getId());
    }

    @Test
    void listFavoriteLinksBackfillsMetadataForAPreExistingPinnedLinkWithNoAttemptYet() {
        LinkMetadataFetcher mockFetcher = mock(LinkMetadataFetcher.class);
        when(mockFetcher.fetchTitle(anyString())).thenReturn(Optional.of("Pinned Title"));
        when(mockFetcher.buildFaviconUrl(anyString())).thenReturn("https://favicon.example/x");
        LinkService serviceWithMock = new LinkService(linkRepository, mockFetcher);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link preExisting = linkRepository.save(new Link("https://pre-existing-pinned.example.com", now, now.plusSeconds(3600)));
        serviceWithMock.pinLink(preExisting.getId());
        assertThat(linkRepository.findById(preExisting.getId()).orElseThrow().getTitleFetchedAt()).isNull();

        List<Link> favorites = serviceWithMock.listFavoriteLinks();

        Link backfilled = favorites.stream().filter(l -> l.getId().equals(preExisting.getId())).findFirst().orElseThrow();
        assertThat(backfilled.getTitleFetchedAt()).isNotNull();
        assertThat(backfilled.getPageTitle()).isEqualTo("Pinned Title");
    }

    @Test
    void unpinningAPinnedLinkAssignsAFresh168HourCountdownIndependentOfSavedAtAndTimeSpentPinned() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // savedAt and the pre-pin expiresAt are both deliberately unrelated to "now," and pinnedAt
        // simulates the link having sat pinned for a very long time, proving the fresh countdown is
        // computed purely from the moment of unpinning (FR-011).
        Link link = linkRepository.save(new Link("https://example.com/long-pinned", now.minus(500, ChronoUnit.HOURS), now.minus(400, ChronoUnit.HOURS)));
        link.setPinned(true);
        link.setPinnedAt(now.minus(300, ChronoUnit.HOURS));
        linkRepository.save(link);

        linkService.unpinLink(link.getId());

        Link reloaded = linkRepository.findById(link.getId()).orElseThrow();
        assertThat(reloaded.isPinned()).isFalse();
        assertThat(reloaded.getExpiresAt()).isAfter(now.plus(167, ChronoUnit.HOURS));
        assertThat(reloaded.getExpiresAt()).isBefore(now.plus(169, ChronoUnit.HOURS));
    }

    @Test
    void unpinningAnAlreadyUnpinnedLinkIsATrueNoOpExpiresAtUnchanged() {
        Link link = linkService.createLink("https://example.com/already-unpinned");
        Instant originalExpiresAt = link.getExpiresAt();

        linkService.unpinLink(link.getId());

        assertThat(linkRepository.findById(link.getId()).orElseThrow().getExpiresAt()).isEqualTo(originalExpiresAt);
    }

    @Test
    void unpinningANonExistentIdIsANoOp() {
        long nonExistentId = 999_999L;

        linkService.unpinLink(nonExistentId);

        assertThat(linkRepository.existsById(nonExistentId)).isFalse();
    }

    @Test
    void deletingAPinnedLinkRemovesItEntirelyRegardlessOfPinnedState() {
        Link link = linkService.createLink("https://example.com/to-delete-pinned");
        linkService.pinLink(link.getId());
        assertThat(linkRepository.findById(link.getId()).orElseThrow().isPinned()).isTrue();

        linkService.deleteLink(link.getId());

        assertThat(linkRepository.existsById(link.getId())).isFalse();
    }
}
