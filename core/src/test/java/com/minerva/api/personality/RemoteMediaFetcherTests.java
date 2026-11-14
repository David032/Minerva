package com.minerva.api.personality;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("Unit")
class RemoteMediaFetcherTests {
    @Test
    void rejectsLocalPrivateAndNonHttpDestinations() {
        assertThrows(IllegalArgumentException.class,
                () -> RemoteMediaFetcher.validateRemoteUri(URI.create("http://127.0.0.1/image.png")));
        assertThrows(IllegalArgumentException.class,
                () -> RemoteMediaFetcher.validateRemoteUri(URI.create("http://169.254.169.254/latest/meta-data")));
        assertThrows(IllegalArgumentException.class,
                () -> RemoteMediaFetcher.validateRemoteUri(URI.create("http://localhost/image.png")));
        assertThrows(IllegalArgumentException.class,
                () -> RemoteMediaFetcher.validateRemoteUri(URI.create("file:///etc/passwd")));
        assertThrows(IllegalArgumentException.class,
                () -> RemoteMediaFetcher.validateRemoteUri(URI.create("https://user@example.com/image.png")));
        assertThrows(IllegalArgumentException.class,
                () -> RemoteMediaFetcher.validateRemoteUri(URI.create("https://example.com:8443/image.png")));
    }

    @Test
    void fetchesSupportedPublicImageContentWithinTheConfiguredLimit() throws Exception {
        var client = mock(CloseableHttpClient.class);
        var response = imageResponse(200, new byte[] {1, 2, 3}, "image/png");
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class))).thenReturn(response);
        var fetcher = new RemoteMediaFetcher(client, RemoteMediaFetcherTests::publicAddress);

        var media = fetcher.fetch(List.of(URI.create("https://media.example.test/image.png")));

        assertEquals(1, media.size());
        assertEquals("image/png", media.getFirst().contentType());
        assertArrayEquals(new byte[] {1, 2, 3}, media.getFirst().content());
    }

    @Test
    void rejectsUnsupportedContentAndOversizedMedia() throws Exception {
        var client = mock(CloseableHttpClient.class);
        var unsupportedResponse = imageResponse(200, new byte[] {1}, "text/html");
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class))).thenReturn(unsupportedResponse);
        var fetcher = new RemoteMediaFetcher(client, RemoteMediaFetcherTests::publicAddress);
        var mediaUrl = URI.create("https://media.example.test/image.png");

        assertThrows(IllegalArgumentException.class, () -> fetcher.fetch(List.of(mediaUrl)));

        var oversizedEntity = mock(org.apache.hc.core5.http.HttpEntity.class);
        when(oversizedEntity.getContentLength()).thenReturn(5L * 1024 * 1024 + 1);
        var oversizedResponse = mock(CloseableHttpResponse.class);
        when(oversizedResponse.getCode()).thenReturn(200);
        when(oversizedResponse.getFirstHeader("Content-Type")).thenReturn(new BasicHeader("Content-Type", "image/png"));
        when(oversizedResponse.getEntity()).thenReturn(oversizedEntity);
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class))).thenReturn(oversizedResponse);

        assertThrows(IllegalArgumentException.class, () -> fetcher.fetch(List.of(mediaUrl)));
    }

    private static CloseableHttpResponse imageResponse(int code, byte[] bytes, String contentType) throws IOException {
        var response = mock(CloseableHttpResponse.class);
        when(response.getCode()).thenReturn(code);
        when(response.getFirstHeader("Content-Type")).thenReturn(new BasicHeader("Content-Type", contentType));
        when(response.getEntity()).thenReturn(new ByteArrayEntity(bytes, ContentType.create(contentType)));
        return response;
    }

    private static InetAddress[] publicAddress(String host) throws IOException {
        return new InetAddress[] {InetAddress.getByAddress(new byte[] {8, 8, 8, 8})};
    }
}
