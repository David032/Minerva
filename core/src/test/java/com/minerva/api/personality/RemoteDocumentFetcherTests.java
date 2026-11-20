package com.minerva.api.personality;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("Unit")
class RemoteDocumentFetcherTests {
    @Test
    void rejectsPrivateDestinationsBeforeMakingARequest() throws Exception {
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        AtomicBoolean resolved = new AtomicBoolean();
        var fetcher = new RemoteDocumentFetcher(httpClient, host -> {
            resolved.set(true);
            return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
        });

        assertThrows(IllegalArgumentException.class,
                () -> fetcher.fetch("https://example.com/document.pdf"));

        assertTrue(resolved.get());
        verifyNoInteractions(httpClient);
    }
}
