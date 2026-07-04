package com.shelflife.backend.link;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves best-effort link metadata: a page's {@code <title>} (server-side HTTP fetch, SSRF-guarded)
 * and a favicon-service URL (pure string construction, no network call — research.md §2).
 */
@Component
public class LinkMetadataFetcher {

    private static final int MAX_REDIRECT_HOPS = 3;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final int MAX_TITLE_LENGTH = 512;
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final Predicate<String> hostValidator;

    public LinkMetadataFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), REQUEST_TIMEOUT, LinkMetadataFetcher::isPublicHost);
    }

    /** Test-only seam: a shorter timeout keeps the timeout-handling test fast and deterministic. */
    LinkMetadataFetcher(HttpClient httpClient, Duration requestTimeout) {
        this(httpClient, requestTimeout, LinkMetadataFetcher::isPublicHost);
    }

    /** Test-only seam: an injectable host validator lets tests exercise the fetch/redirect/extraction
     * logic against a local (loopback) test server while a separate, real-guard test proves every hop
     * is still re-validated in production. */
    LinkMetadataFetcher(HttpClient httpClient, Duration requestTimeout, Predicate<String> hostValidator) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.hostValidator = hostValidator;
    }

    /**
     * Fetches the destination's {@code <title>}, following up to {@value MAX_REDIRECT_HOPS} redirects,
     * re-validating the resolved IP of every hop against loopback/private/link-local ranges. Returns
     * empty on any failure (unreachable, timeout, oversized response, no title, or a rejected hop) —
     * the caller falls back to the raw URL per FR-006.
     */
    public Optional<String> fetchTitle(String url) {
        try {
            String currentUrl = url;
            for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
                URI uri = URI.create(currentUrl);
                if (!hostValidator.test(uri.getHost())) {
                    return Optional.empty();
                }

                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(requestTimeout)
                        .GET()
                        .build();

                HttpResponse<InputStream> response;
                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                } catch (IOException | InterruptedException e) {
                    return Optional.empty();
                }

                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    Optional<String> location = response.headers().firstValue("Location");
                    drain(response.body());
                    if (location.isEmpty()) {
                        return Optional.empty();
                    }
                    currentUrl = uri.resolve(location.get()).toString();
                    continue;
                }

                if (status < 200 || status >= 300) {
                    drain(response.body());
                    return Optional.empty();
                }

                return extractTitle(readCapped(response.body()));
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Pure string construction against a public favicon service — no network call, cannot meaningfully fail. */
    public String buildFaviconUrl(String url) {
        String host = URI.create(url).getHost();
        return "https://www.google.com/s2/favicons?domain=" + host + "&sz=64";
    }

    static boolean isPublicHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (address.isLoopbackAddress()
                        || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress()
                        || address.isMulticastAddress()
                        || address.isAnyLocalAddress()) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static String readCapped(InputStream inputStream) {
        try (inputStream) {
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int total = 0;
            int read;
            while (total < MAX_RESPONSE_BYTES && (read = inputStream.read(buffer)) != -1) {
                int toWrite = Math.min(read, MAX_RESPONSE_BYTES - total);
                output.write(buffer, 0, toWrite);
                total += toWrite;
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static void drain(InputStream inputStream) {
        try (inputStream) {
            inputStream.readAllBytes();
        } catch (IOException e) {
            // best-effort only; the response is being discarded either way
        }
    }

    static Optional<String> extractTitle(String html) {
        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String raw = decodeHtmlEntities(matcher.group(1).trim());
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(raw.length() > MAX_TITLE_LENGTH ? raw.substring(0, MAX_TITLE_LENGTH) : raw);
    }

    private static String decodeHtmlEntities(String text) {
        return text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ");
    }
}
