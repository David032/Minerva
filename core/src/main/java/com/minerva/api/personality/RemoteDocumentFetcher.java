package com.minerva.api.personality;

import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;

/**
 * Fetches size-bounded PDF documents from public HTTP(S) destinations.
 */
@Component
public class RemoteDocumentFetcher {
    private static final int MAX_DOCUMENT_BYTES = 20 * 1024 * 1024;
    private static final int MAX_URL_LENGTH = 2_048;
    private final CloseableHttpClient httpClient;
    private final RemoteMediaFetcher.AddressResolver addressResolver;

    /**
     * Creates a fetcher with redirect-disabled HTTP and public-address validation.
     */
    public RemoteDocumentFetcher() {
        this(RemoteMediaFetcher.createHttpClient(), RemoteMediaFetcher::resolvePublicAddresses);
    }

    RemoteDocumentFetcher(
            CloseableHttpClient httpClient,
            RemoteMediaFetcher.AddressResolver addressResolver
    ) {
        this.httpClient = httpClient;
        this.addressResolver = addressResolver;
    }

    /**
     * Downloads a public PDF while enforcing URL, destination, type, and size limits.
     *
     * @param documentUrl public PDF URL
     * @return the source URI, filename, and bounded PDF bytes
     * @throws IllegalArgumentException if the URL or returned content is unsupported
     * @throws IOException if the destination cannot be resolved or the document cannot be fetched
     */
    public RemoteDocument fetch(String documentUrl) throws IOException {
        if (documentUrl == null || documentUrl.isBlank() || documentUrl.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Enter a PDF URL of 2,048 characters or fewer.");
        }
        URI uri;
        try {
            uri = URI.create(documentUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Enter a valid PDF URL.", e);
        }
        if (uri.getPath() == null || !uri.getPath().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("The document URL must end in .pdf.");
        }
        RemoteMediaFetcher.validateRemoteUri(uri, addressResolver);

        var request = new HttpGet(uri);
        request.setHeader("Accept", "application/pdf");
        try (var response = httpClient.execute(request)) {
            if (response.getCode() < 200 || response.getCode() >= 300) {
                throw new IOException("Document server returned HTTP " + response.getCode() + ".");
            }
            var contentTypeHeader = response.getFirstHeader("Content-Type");
            String contentType = contentTypeHeader == null ? ""
                    : contentTypeHeader.getValue().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!contentType.equals("application/pdf")) {
                throw new IllegalArgumentException("The document URL must return a PDF.");
            }
            var entity = response.getEntity();
            if (entity == null) {
                throw new IllegalArgumentException("The PDF document was empty.");
            }
            if (entity.getContentLength() > MAX_DOCUMENT_BYTES) {
                throw new IllegalArgumentException("The PDF document exceeds the 20 MB limit.");
            }
            byte[] body;
            try (InputStream stream = entity.getContent()) {
                body = stream.readNBytes(MAX_DOCUMENT_BYTES + 1);
            }
            if (body.length == 0) {
                throw new IllegalArgumentException("The PDF document was empty.");
            }
            if (body.length > MAX_DOCUMENT_BYTES) {
                throw new IllegalArgumentException("The PDF document exceeds the 20 MB limit.");
            }
            if (!startsWithPdfSignature(body)) {
                throw new IllegalArgumentException("The document URL did not return a valid PDF.");
            }
            String path = uri.getPath();
            return new RemoteDocument(uri, path.substring(path.lastIndexOf('/') + 1), body);
        } catch (org.apache.hc.core5.http.NoHttpResponseException e) {
            throw new IOException("The document server did not respond.", e);
        }
    }

    @PreDestroy
    void close() throws IOException {
        httpClient.close();
    }

    private static boolean startsWithPdfSignature(byte[] bytes) {
        byte[] expected = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (bytes.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (bytes[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * A validated remote PDF and its original source.
     *
     * @param source original public URL
     * @param fileName source filename
     * @param content bounded PDF bytes
     */
    public record RemoteDocument(URI source, String fileName, byte[] content) {
    }
}
