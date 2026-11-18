package com.minerva.api.personality;

import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Fetches and extracts bounded readable text from public HTML article URLs.
 */
@Component
public class RemoteArticleFetcher {
    private static final int MAX_URL_LENGTH = 2_048;
    private static final int MAX_ARTICLE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ARTICLE_CHARS = 40_000;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final CloseableHttpClient httpClient;
    private final RemoteMediaFetcher.AddressResolver addressResolver;

    /**
     * Creates an article fetcher using the shared public-destination HTTP protections.
     */
    public RemoteArticleFetcher() {
        this(RemoteMediaFetcher.createHttpClient(), RemoteMediaFetcher::resolvePublicAddresses);
    }

    RemoteArticleFetcher(CloseableHttpClient httpClient, RemoteMediaFetcher.AddressResolver addressResolver) {
        this.httpClient = httpClient;
        this.addressResolver = addressResolver;
    }

    /**
     * Fetches a public HTML page and extracts its title and readable article text.
     *
     * @param articleUrl public HTTP(S) article URL
     * @return the article source, title, and bounded readable text
     * @throws IllegalArgumentException if the URL or returned content is unsupported
     * @throws IOException if the destination cannot be resolved or the page cannot be fetched
     */
    public Article fetch(String articleUrl) throws IOException {
        if (articleUrl == null || articleUrl.isBlank() || articleUrl.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Enter an article URL of 2,048 characters or fewer.");
        }

        URI uri;
        try {
            uri = URI.create(articleUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Enter a valid article URL.", e);
        }
        RemoteMediaFetcher.validateRemoteUri(uri, addressResolver);

        var request = new HttpGet(uri);
        request.setHeader("Accept", "text/html, application/xhtml+xml");
        try (var response = httpClient.execute(request)) {
            if (response.getCode() < 200 || response.getCode() >= 300) {
                throw new IOException("Article server returned HTTP " + response.getCode() + ".");
            }
            var contentTypeHeader = response.getFirstHeader("Content-Type");
            String contentType = contentTypeHeader == null
                    ? ""
                    : contentTypeHeader.getValue().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!contentType.equals("text/html") && !contentType.equals("application/xhtml+xml")) {
                throw new IllegalArgumentException("The article URL must return HTML content.");
            }
            var entity = response.getEntity();
            if (entity == null) {
                throw new IllegalArgumentException("The article page was empty.");
            }
            if (entity.getContentLength() > MAX_ARTICLE_BYTES) {
                throw new IllegalArgumentException("The article page exceeds the 2 MB limit.");
            }
            byte[] body;
            try (InputStream stream = entity.getContent()) {
                body = stream.readNBytes(MAX_ARTICLE_BYTES + 1);
            }
            if (body.length == 0) {
                throw new IllegalArgumentException("The article page was empty.");
            }
            if (body.length > MAX_ARTICLE_BYTES) {
                throw new IllegalArgumentException("The article page exceeds the 2 MB limit.");
            }
            return extract(uri, body);
        } catch (org.apache.hc.core5.http.NoHttpResponseException e) {
            throw new IOException("The article server did not respond.", e);
        }
    }

    private static Article extract(URI uri, byte[] body) throws IOException {
        Document document;
        try (InputStream stream = new java.io.ByteArrayInputStream(body)) {
            document = Jsoup.parse(stream, null, uri.toString());
        }
        document.select("script, style, noscript, template, nav, footer, header, aside, form").remove();
        String title = document.title().trim();
        Element content = document.selectFirst("article, main");
        if (content == null) {
            content = document.body();
        }
        String text = content == null ? "" : WHITESPACE.matcher(content.text()).replaceAll(" ").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("No readable article text could be extracted.");
        }
        if (text.length() > MAX_ARTICLE_CHARS) {
            text = text.substring(0, MAX_ARTICLE_CHARS);
        }
        return new Article(uri, title, text);
    }

    @PreDestroy
    void close() throws IOException {
        httpClient.close();
    }

    /**
     * Readable article content returned by {@link #fetch(String)}.
     *
     * @param source original article URL
     * @param title document title, if present
     * @param text bounded readable article text
     */
    public record Article(URI source, String title, String text) {
    }
}
