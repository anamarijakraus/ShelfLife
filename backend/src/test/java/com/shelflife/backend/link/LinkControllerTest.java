package com.shelflife.backend.link;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LinkRepository linkRepository;

    @Test
    void postValidUrlReturns201WithPersistedLink() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.url").value("https://example.com"))
                .andExpect(jsonPath("$.savedAt").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.title").value("https://example.com"))
                .andExpect(jsonPath("$.faviconUrl").value("https://www.google.com/s2/favicons?domain=example.com&sz=64"));
    }

    @Test
    void postUrlMissingSchemeIsNormalizedWithHttpsPrepended() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://example.com"));
    }

    @Test
    void postBlankUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_url"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void postUnparsableUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"not a url at all!!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_url"));
    }

    @Test
    void postSingleLabelHostUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://localhost\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_url"));
    }

    @Test
    void postingTheSameUrlTwiceYieldsTwoDistinctLinks() throws Exception {
        String firstResponse = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String secondResponse = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(idFrom(firstResponse)).isNotEqualTo(idFrom(secondResponse));
    }

    @Test
    void getReturnsEmptyArrayWhenNothingSaved() throws Exception {
        mockMvc.perform(get("/api/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links").isArray())
                .andExpect(jsonPath("$.links").isEmpty());
    }

    @Test
    void getReturnsActiveLinksOrderedSoonestToExpireFirstWithRawUrlLabel() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://first-saved.example.com\"}"))
                .andExpect(status().isCreated());

        // Ensures a distinct millisecond-truncated savedAt/expiresAt from the first
        // link, so ordering is deterministic rather than dependent on tie-breaking.
        Thread.sleep(5);

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://second-saved.example.com\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links.length()").value(2))
                .andExpect(jsonPath("$.links[0].url").value("https://first-saved.example.com"))
                .andExpect(jsonPath("$.links[1].url").value("https://second-saved.example.com"))
                // Neither subdomain resolves, so title falls back to the raw url (FR-006) and faviconUrl
                // is still the synchronously-computed, always-present favicon-service URL (FR-007).
                .andExpect(jsonPath("$.links[0].title").value("https://first-saved.example.com"))
                .andExpect(jsonPath("$.links[1].title").value("https://second-saved.example.com"))
                .andExpect(jsonPath("$.links[0].faviconUrl").exists())
                .andExpect(jsonPath("$.links[1].faviconUrl").exists());
    }

    @Test
    void getGraveyardReturnsEmptyArrayWhenNothingSaved() throws Exception {
        mockMvc.perform(get("/api/links/graveyard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links").isArray())
                .andExpect(jsonPath("$.links").isEmpty());
    }

    @Test
    void getGraveyardReturnsLinksOrderedSoonestToBeDeletedFirstWithDeletionDeadline() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link soonToBeDeleted = linkRepository.save(new Link(
                "https://soon-deleted.example.com",
                now.minus(200, ChronoUnit.HOURS),
                now.minus(29, ChronoUnit.DAYS)));
        linkRepository.save(new Link(
                "https://later-deleted.example.com",
                now.minus(200, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS)));

        String response = mockMvc.perform(get("/api/links/graveyard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links.length()").value(2))
                .andExpect(jsonPath("$.links[0].url").value("https://soon-deleted.example.com"))
                .andExpect(jsonPath("$.links[1].url").value("https://later-deleted.example.com"))
                .andExpect(jsonPath("$.links[0].title").value("https://soon-deleted.example.com"))
                .andExpect(jsonPath("$.links[1].title").value("https://later-deleted.example.com"))
                .andExpect(jsonPath("$.links[0].faviconUrl").exists())
                .andExpect(jsonPath("$.links[1].faviconUrl").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains(soonToBeDeleted.getExpiresAt().plus(30, ChronoUnit.DAYS).toString());
        assertThat(response).doesNotContain("\"expiresAt\":\"" + soonToBeDeleted.getExpiresAt() + "\"");
    }

    // Renamed from "...NoRescueResurrectOrPinEndpoint": Feature 4 intentionally and explicitly adds
    // pin/unpin endpoints (see below), superseding that narrower claim, exactly as Feature 3 did for
    // the analogous "no DELETE endpoint" assertion. PATCH/PUT remain unsupported everywhere.
    @Test
    void controllerExposesNoRescueOrResurrectEndpointViaPatchOrPut() throws Exception {
        mockMvc.perform(patch("/api/links/1")).andExpect(status().is4xxClientError());
        mockMvc.perform(put("/api/links/1")).andExpect(status().is4xxClientError());
        mockMvc.perform(patch("/api/links/graveyard/1")).andExpect(status().is4xxClientError());
        mockMvc.perform(put("/api/links/graveyard/1")).andExpect(status().is4xxClientError());
        mockMvc.perform(delete("/api/links/graveyard/1")).andExpect(status().is4xxClientError());
    }

    @Test
    void deleteActiveLinkReturns204AndRemovesIt() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link("https://example.com/delete-active", now, now.plusSeconds(3600)));

        mockMvc.perform(delete("/api/links/" + link.getId()))
                .andExpect(status().isNoContent());

        assertThat(linkRepository.existsById(link.getId())).isFalse();
    }

    @Test
    void deleteGraveyardLinkReturns204AndRemovesIt() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/delete-graveyard", now.minus(200, ChronoUnit.HOURS), now.minus(1, ChronoUnit.DAYS)));

        mockMvc.perform(delete("/api/links/" + link.getId()))
                .andExpect(status().isNoContent());

        assertThat(linkRepository.existsById(link.getId())).isFalse();
    }

    @Test
    void deleteNonExistentIdReturns204AsANoOpNotAnError() throws Exception {
        mockMvc.perform(delete("/api/links/999999"))
                .andExpect(status().isNoContent());
    }

    @Test
    void pinningAnActiveLinkReturns204AndRemovesItFromTheActiveList() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link("https://example.com/pin-active", now, now.plusSeconds(3600)));

        mockMvc.perform(post("/api/links/" + link.getId() + "/pin"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/links"))
                .andExpect(jsonPath("$.links[?(@.id == " + link.getId() + ")]").isEmpty());
    }

    @Test
    void pinningAGraveyardLinkReturns204AndRemovesItFromTheGraveyard() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link(
                "https://example.com/pin-graveyard", now.minus(200, ChronoUnit.HOURS), now.minus(1, ChronoUnit.DAYS)));

        mockMvc.perform(post("/api/links/" + link.getId() + "/pin"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/links/graveyard"))
                .andExpect(jsonPath("$.links[?(@.id == " + link.getId() + ")]").isEmpty());
    }

    @Test
    void pinningANonExistentIdReturns204AsANoOp() throws Exception {
        mockMvc.perform(post("/api/links/999999/pin"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getFavoritesReturnsEmptyArrayWhenNothingPinned() throws Exception {
        mockMvc.perform(get("/api/links/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links").isArray())
                .andExpect(jsonPath("$.links").isEmpty());
    }

    @Test
    void getFavoritesReturnsPinnedLinksOrderedMostRecentlyPinnedFirstWithNullExpiresAt() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link olderPin = linkRepository.save(new Link("https://older-pin.example.com", now, now.plusSeconds(3600)));
        Link newerPin = linkRepository.save(new Link("https://newer-pin.example.com", now, now.plusSeconds(3600)));

        mockMvc.perform(post("/api/links/" + olderPin.getId() + "/pin")).andExpect(status().isNoContent());
        Thread.sleep(5);
        mockMvc.perform(post("/api/links/" + newerPin.getId() + "/pin")).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/links/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links.length()").value(2))
                .andExpect(jsonPath("$.links[0].url").value("https://newer-pin.example.com"))
                .andExpect(jsonPath("$.links[1].url").value("https://older-pin.example.com"))
                .andExpect(jsonPath("$.links[0].expiresAt").value((Object) null))
                .andExpect(jsonPath("$.links[1].expiresAt").value((Object) null));
    }

    @Test
    void unpinningAPinnedLinkReturns204AndItReappearsInActiveListWithFreshExpiresAt() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link("https://example.com/unpin-me", now, now.plusSeconds(3600)));
        mockMvc.perform(post("/api/links/" + link.getId() + "/pin")).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/links/" + link.getId() + "/unpin"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/links"))
                .andExpect(jsonPath("$.links[?(@.id == " + link.getId() + ")]").isNotEmpty());
        Link reloaded = linkRepository.findById(link.getId()).orElseThrow();
        assertThat(reloaded.isPinned()).isFalse();
        assertThat(reloaded.getExpiresAt()).isAfter(now.plus(167, ChronoUnit.HOURS));
    }

    @Test
    void unpinningAnAlreadyUnpinnedOrNonExistentIdReturns204AsANoOp() throws Exception {
        Link link = createUnpinnedLink();

        mockMvc.perform(post("/api/links/" + link.getId() + "/unpin"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/links/999999/unpin"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletingAPinnedLinkReturns204AndItIsAbsentFromFavorites() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Link link = linkRepository.save(new Link("https://example.com/delete-pinned", now, now.plusSeconds(3600)));
        mockMvc.perform(post("/api/links/" + link.getId() + "/pin")).andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/links/" + link.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/links/favorites"))
                .andExpect(jsonPath("$.links[?(@.id == " + link.getId() + ")]").isEmpty());
    }

    private Link createUnpinnedLink() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return linkRepository.save(new Link("https://example.com/already-unpinned", now, now.plusSeconds(3600)));
    }

    private static long idFrom(String json) {
        return Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }
}
