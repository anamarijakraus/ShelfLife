package com.shelflife.backend.link;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        List<Link> active = linkRepository.findByExpiresAtAfterOrderByExpiresAtAsc(now);

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

        List<Link> active = linkRepository.findByExpiresAtAfterOrderByExpiresAtAsc(now);

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

        List<Link> active = linkRepository.findByExpiresAtAfterOrderByExpiresAtAsc(now);

        assertThat(active).extracting(Link::getId).doesNotContain(link.getId());
    }
}
