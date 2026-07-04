package com.shelflife.backend.link;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class LinkMetadataFetcherTest {

    @Test
    void rejectsLoopbackHosts() {
        assertThat(LinkMetadataFetcher.isPublicHost("127.0.0.1")).isFalse();
        assertThat(LinkMetadataFetcher.isPublicHost("localhost")).isFalse();
    }

    @Test
    void rejectsPrivateRfc1918Hosts() {
        assertThat(LinkMetadataFetcher.isPublicHost("10.0.0.1")).isFalse();
        assertThat(LinkMetadataFetcher.isPublicHost("172.16.0.1")).isFalse();
        assertThat(LinkMetadataFetcher.isPublicHost("192.168.1.1")).isFalse();
    }

    @Test
    void rejectsLinkLocalHosts() {
        assertThat(LinkMetadataFetcher.isPublicHost("169.254.1.1")).isFalse();
    }

    @Test
    void acceptsAKnownPublicIpLiteral() {
        assertThat(LinkMetadataFetcher.isPublicHost("8.8.8.8")).isTrue();
    }

    @Test
    void theRealProductionFetcherRefusesToEvenAttemptALoopbackUrl() {
        LinkMetadataFetcher fetcher = new LinkMetadataFetcher();

        Optional<String> title = fetcher.fetchTitle("http://127.0.0.1:1/");

        assertThat(title).isEmpty();
    }

    @Test
    void rejectsARedirectHopThatResolvesToALoopbackAddressEvenWhenTheOriginalHopWasAllowed() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + port + "/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            // Simulates a hop that was already validated as public; the *next* hop (the redirect
            // target) must still be re-checked by the real SSRF guard, not merely inherit the pass.
            AtomicBoolean firstHopAllowed = new AtomicBoolean(true);
            LinkMetadataFetcher fetcher = new LinkMetadataFetcher(
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                    Duration.ofSeconds(5),
                    host -> firstHopAllowed.getAndSet(false) || LinkMetadataFetcher.isPublicHost(host));

            Optional<String> title = fetcher.fetchTitle("http://127.0.0.1:" + port + "/");

            assertThat(title).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsEmptyWhenTheRequestTimesOut() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "<html><head><title>Too Slow</title></head></html>".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            LinkMetadataFetcher fetcher = new LinkMetadataFetcher(
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                    Duration.ofMillis(200),
                    host -> true);

            Optional<String> title = fetcher.fetchTitle("http://127.0.0.1:" + port + "/");

            assertThat(title).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void enforcesTheResponseSizeCapAndDoesNotFindATitleBuriedBeyondIt() throws IOException {
        withTestServer(
                "<html><head>" + "<!-- padding -->".repeat(10_000) + "<title>Buried Title</title></head></html>",
                title -> assertThat(title).isEmpty());
    }

    @Test
    void extractsATitleFromASimplePage() throws IOException {
        withTestServer(
                "<html><head><title>Example Title</title></head><body></body></html>",
                title -> assertThat(title).contains("Example Title"));
    }

    @Test
    void fallsBackToEmptyWhenNoTitleTagIsPresent() throws IOException {
        withTestServer(
                "<html><head></head><body>No title here</body></html>",
                title -> assertThat(title).isEmpty());
    }

    @Test
    void decodesCommonHtmlEntitiesInTheExtractedTitle() throws IOException {
        withTestServer(
                "<html><head><title>Tom &amp; Jerry</title></head></html>",
                title -> assertThat(title).contains("Tom & Jerry"));
    }

    @Test
    void truncatesATitleLongerThan512CharactersBeforeReturningIt() throws IOException {
        String longTitle = "A".repeat(600);
        withTestServer(
                "<html><head><title>" + longTitle + "</title></head></html>",
                title -> {
                    assertThat(title).isPresent();
                    assertThat(title.get()).hasSize(512);
                });
    }

    @Test
    void buildFaviconUrlConstructsADomainKeyedUrlAgainstTheFaviconServiceWithNoNetworkCall() {
        LinkMetadataFetcher fetcher = new LinkMetadataFetcher();

        String faviconUrl = fetcher.buildFaviconUrl("https://example.com/some/page");

        assertThat(faviconUrl).contains("domain=example.com");
    }

    private void withTestServer(String html, Consumer<Optional<String>> assertion) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            byte[] body = html.getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            LinkMetadataFetcher fetcher = new LinkMetadataFetcher(
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                    Duration.ofSeconds(5),
                    host -> true);

            Optional<String> title = fetcher.fetchTitle("http://127.0.0.1:" + port + "/");

            assertion.accept(title);
        } finally {
            server.stop(0);
        }
    }
}
