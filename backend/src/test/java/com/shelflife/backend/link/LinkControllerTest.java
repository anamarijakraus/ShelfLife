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

    @Test
    void controllerExposesOnlyGetPostAndDeleteNoRescueResurrectOrPinEndpoint() throws Exception {
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

    private static long idFrom(String json) {
        return Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }
}
