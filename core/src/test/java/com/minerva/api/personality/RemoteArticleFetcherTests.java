package com.minerva.api.personality;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("Unit")
class RemoteArticleFetcherTests {
    @Test
    void extractsBoundedReadableArticleTextFromPublicHtml() throws Exception {
        var client = mock(CloseableHttpClient.class);
        var html = """
                <html><head><title>Garden ideas</title><script>ignore script</script></head>
                <body><nav>ignore navigation</nav><article><h1>Small gardens</h1>
                <p>Thoughtful planting.</p></article><footer>ignore footer</footer></body></html>
                """.getBytes(StandardCharsets.UTF_8);
        var response = response(200, html, "text/html; charset=UTF-8");
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class))).thenReturn(response);
        var fetcher = new RemoteArticleFetcher(client, RemoteMediaFetcherTests::publicAddress);

        var article = fetcher.fetch("https://news.example.test/gardens");

        assertEquals(URI.create("https://news.example.test/gardens"), article.source());
        assertEquals("Garden ideas", article.title());
        assertTrue(article.text().contains("Small gardens Thoughtful planting."));
        assertFalse(article.text().contains("ignore"));
    }

    @Test
    void rejectsUnsafeUrlsAndNonHtmlResponses() throws Exception {
        var client = mock(CloseableHttpClient.class);
        var fetcher = new RemoteArticleFetcher(client, RemoteMediaFetcher::resolvePublicAddresses);

        assertThrows(IllegalArgumentException.class, () -> fetcher.fetch("http://127.0.0.1/story"));
        assertThrows(IllegalArgumentException.class, () -> fetcher.fetch("https://user@example.test/story"));

        var nonHtmlResponse = response(
                200, "<p>Not a page</p>".getBytes(StandardCharsets.UTF_8), "text/plain");
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class)))
                .thenReturn(nonHtmlResponse);
        var publicFetcher = new RemoteArticleFetcher(client, RemoteMediaFetcherTests::publicAddress);
        assertThrows(IllegalArgumentException.class, () -> publicFetcher.fetch("https://news.example.test/story"));
    }

    @Test
    void rejectsNonSuccessfulResponsesAndOversizedArticleBodies() throws Exception {
        var client = mock(CloseableHttpClient.class);
        var fetcher = new RemoteArticleFetcher(client, RemoteMediaFetcherTests::publicAddress);
        var redirectResponse = response(302, new byte[0], "text/html");
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class)))
                .thenReturn(redirectResponse);

        assertThrows(java.io.IOException.class, () -> fetcher.fetch("https://news.example.test/story"));

        var oversized = mock(org.apache.hc.core5.http.HttpEntity.class);
        when(oversized.getContentLength()).thenReturn(2L * 1024 * 1024 + 1);
        var oversizedResponse = mock(CloseableHttpResponse.class);
        when(oversizedResponse.getCode()).thenReturn(200);
        when(oversizedResponse.getFirstHeader("Content-Type"))
                .thenReturn(new BasicHeader("Content-Type", "text/html"));
        when(oversizedResponse.getEntity()).thenReturn(oversized);
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class))).thenReturn(oversizedResponse);
        assertThrows(IllegalArgumentException.class,
                () -> fetcher.fetch("https://news.example.test/story"));
    }

    private static CloseableHttpResponse response(int code, byte[] body, String contentType) throws Exception {
        var response = mock(CloseableHttpResponse.class);
        when(response.getCode()).thenReturn(code);
        when(response.getFirstHeader("Content-Type")).thenReturn(new BasicHeader("Content-Type", contentType));
        when(response.getEntity()).thenReturn(new ByteArrayEntity(
                body, ContentType.create(contentType.split(";", 2)[0])));
        return response;
    }
}
