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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void fetchesSupportedPublicImageContent() throws Exception {
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
    void acceptsFiftyMediaReferencesAndRejectsMoreBeforeFetching() throws Exception {
        var client = mock(CloseableHttpClient.class);
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class)))
                .thenAnswer(ignored -> imageResponse(200, new byte[] {1}, "image/png"));
        var fetcher = new RemoteMediaFetcher(client, RemoteMediaFetcherTests::publicAddress);
        List<URI> references = java.util.stream.IntStream.range(0, 50)
                .mapToObj(index -> URI.create("https://media.example.test/" + index + ".png"))
                .toList();

        assertEquals(50, fetcher.fetch(references).size());
        verify(client, org.mockito.Mockito.times(50))
                .execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class));
        assertThrows(IllegalArgumentException.class, () -> fetcher.fetch(
                java.util.stream.IntStream.range(0, 51)
                        .mapToObj(index -> URI.create("https://media.example.test/" + index + ".png"))
                        .toList()));
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

    @Test
    void boundsConcurrentDownloadsAcrossFetchersAndPreservesReferenceOrder() throws Exception {
        var client = mock(CloseableHttpClient.class);
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class))).thenAnswer(invocation -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for test release.");
                }
                return imageResponse(200, new byte[] {1}, "image/png");
            } finally {
                active.decrementAndGet();
            }
        });
        var first = new RemoteMediaFetcher(client, RemoteMediaFetcherTests::publicAddress);
        var second = new RemoteMediaFetcher(client, RemoteMediaFetcherTests::publicAddress);
        var urls = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> URI.create("https://media.example.test/" + index + ".png")).toList();
        try (var callers = Executors.newFixedThreadPool(2)) {
            var one = callers.submit(() -> first.fetch(urls));
            var two = callers.submit(() -> second.fetch(urls));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(4, maximum.get());
            } finally {
                release.countDown();
            }
            assertEquals(urls, one.get(10, TimeUnit.SECONDS).stream().map(RemoteMediaFetcher.MediaAsset::source).toList());
            assertEquals(urls, two.get(10, TimeUnit.SECONDS).stream().map(RemoteMediaFetcher.MediaAsset::source).toList());
            assertEquals(4, maximum.get());
        }
    }

    @Test
    void enforcesCombinedLimitWhileReadingUnknownLengthResponses() throws Exception {
        var client = mock(CloseableHttpClient.class);
        byte[] bytes = new byte[5 * 1024 * 1024];
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class))).thenAnswer(ignored -> {
            var response = imageResponse(200, bytes, "image/png");
            var entity = mock(org.apache.hc.core5.http.HttpEntity.class);
            when(entity.getContentLength()).thenReturn(-1L);
            when(entity.getContent()).thenReturn(new java.io.ByteArrayInputStream(bytes));
            when(response.getEntity()).thenReturn(entity);
            return response;
        });
        var fetcher = new RemoteMediaFetcher(client, RemoteMediaFetcherTests::publicAddress);
        var urls = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> URI.create("https://media.example.test/" + index + ".png")).toList();

        var failure = assertThrows(IllegalArgumentException.class, () -> fetcher.fetch(urls));

        assertTrue(failure.getMessage().contains("20 MB total"));
    }

    @Test
    void retainsReferenceOrderWhenDownloadsCompleteOutOfOrder() throws Exception {
        var client = mock(CloseableHttpClient.class);
        var firstStarted = new CountDownLatch(1);
        var secondFinished = new CountDownLatch(1);
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class))).thenAnswer(invocation -> {
            org.apache.hc.core5.http.ClassicHttpRequest request = invocation.getArgument(0);
            if (request.getPath().endsWith("/first.png")) {
                firstStarted.countDown();
                if (!secondFinished.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for the second download.");
                }
            } else {
                if (!firstStarted.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for the first download.");
                }
                secondFinished.countDown();
            }
            return imageResponse(200, new byte[] {1}, "image/png");
        });
        var fetcher = new RemoteMediaFetcher(client, RemoteMediaFetcherTests::publicAddress);
        var urls = List.of(URI.create("https://media.example.test/first.png"),
                URI.create("https://media.example.test/second.png"));

        assertEquals(urls, fetcher.fetch(urls).stream().map(RemoteMediaFetcher.MediaAsset::source).toList());
    }

    @Test
    void cancelsOtherDownloadsWhenOneFails() throws Exception {
        var client = mock(CloseableHttpClient.class);
        var blocked = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var failureResponse = imageResponse(500, new byte[] {1}, "image/png");
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class))).thenAnswer(invocation -> {
            org.apache.hc.core5.http.ClassicHttpRequest request = invocation.getArgument(0);
            if (request.getPath().endsWith("/blocked.png")) {
                blocked.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted.", e);
                }
            }
            if (!blocked.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for the other download.");
            }
            return failureResponse;
        });
        var fetcher = new RemoteMediaFetcher(client, RemoteMediaFetcherTests::publicAddress);
        var urls = List.of(URI.create("https://media.example.test/blocked.png"),
                URI.create("https://media.example.test/failure.png"));

        assertThrows(IOException.class, () -> fetcher.fetch(urls));
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
        verify(failureResponse).close();
    }

    @Test
    void interruptionCancelsActiveDownloadsAndRestoresCallerInterrupt() throws Exception {
        var client = mock(CloseableHttpClient.class);
        var started = new CountDownLatch(1);
        var workerInterrupted = new CountDownLatch(1);
        when(client.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class))).thenAnswer(invocation -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
                throw new IOException("Unexpected release.");
            } catch (InterruptedException e) {
                workerInterrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted.", e);
            }
        });
        var fetcher = new RemoteMediaFetcher(client, RemoteMediaFetcherTests::publicAddress);
        var callerInterrupted = new AtomicBoolean();
        var caller = new Thread(() -> {
            try {
                fetcher.fetch(List.of(URI.create("https://media.example.test/blocked.png")));
            } catch (IOException expected) {
                callerInterrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        caller.start();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
        } finally {
            caller.interrupt();
            caller.join(5000);
        }
        assertTrue(callerInterrupted.get());
        assertTrue(workerInterrupted.await(5, TimeUnit.SECONDS));
    }

    private static CloseableHttpResponse imageResponse(int code, byte[] bytes, String contentType) throws IOException {
        var response = mock(CloseableHttpResponse.class);
        when(response.getCode()).thenReturn(code);
        when(response.getFirstHeader("Content-Type")).thenReturn(new BasicHeader("Content-Type", contentType));
        when(response.getEntity()).thenReturn(new ByteArrayEntity(bytes, ContentType.create(contentType)));
        return response;
    }

    static InetAddress[] publicAddress(String host) throws IOException {
        return new InetAddress[] {InetAddress.getByAddress(new byte[] {8, 8, 8, 8})};
    }
}
