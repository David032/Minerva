package com.minerva.api.personality;

import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.util.Timeout;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fetches size-bounded public HTTP(S) media referenced by uploaded HTML documents.
 */
@Component
public class RemoteMediaFetcher {
    private static final int MAX_MEDIA_COUNT = 50;
    private static final int MAX_MEDIA_BYTES = 5 * 1024 * 1024;
    private static final int MAX_TOTAL_MEDIA_BYTES = 20 * 1024 * 1024;
    private static final int MAX_CONCURRENT_DOWNLOADS = 4;
    private static final ThreadPoolExecutor DOWNLOADS = createDownloadExecutor();
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "video/ogg"
    );
    private final CloseableHttpClient httpClient;
    private final AddressResolver addressResolver;

    /**
     * Creates a fetcher with redirect-disabled HTTP and public-address validation.
     */
    public RemoteMediaFetcher() {
        this(createHttpClient(), RemoteMediaFetcher::resolvePublicAddresses);
    }

    /**
     * Creates a fetcher with an injected HTTP client and address resolver.
     *
     * @param httpClient HTTP client used to retrieve media
     * @param addressResolver resolves and validates destination addresses
     */
    RemoteMediaFetcher(CloseableHttpClient httpClient, AddressResolver addressResolver) {
        this.httpClient = httpClient;
        this.addressResolver = addressResolver;
    }

    private static ThreadPoolExecutor createDownloadExecutor() {
        var executor = new ThreadPoolExecutor(MAX_CONCURRENT_DOWNLOADS, MAX_CONCURRENT_DOWNLOADS,
                30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
                    Thread thread = new Thread(runnable, "media-download");
                    thread.setDaemon(true);
                    return thread;
                }, (task, pool) -> {
                    if (pool.isShutdown()) {
                        throw new RejectedExecutionException("Media downloader is shut down.");
                    }
                    try {
                        pool.getQueue().put(task);
                        if (pool.isShutdown() && pool.remove(task)) {
                            throw new RejectedExecutionException("Media downloader is shut down.");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RejectedExecutionException("Media download interrupted.", e);
                    }
                });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    static CloseableHttpClient createHttpClient() {
        DnsResolver resolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                try {
                    return resolvePublicAddresses(host);
                } catch (IOException | IllegalArgumentException e) {
                    var failure = new UnknownHostException("Media host is not a public destination.");
                    failure.initCause(e);
                    throw failure;
                }
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(resolver)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(3))
                        .setSocketTimeout(Timeout.ofSeconds(8))
                        .build())
                .setMaxConnTotal(8)
                .setMaxConnPerRoute(2)
                .build();
        var requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(3))
                .setResponseTimeout(Timeout.ofSeconds(8))
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .build();
    }

    /**
     * Downloads supported media references using a shared bounded worker pool while enforcing
     * destination, count, per-item, and aggregate size limits. If downloads fail concurrently,
     * the first observed failure is reported.
     *
     * @param references HTTP(S) media URLs discovered in a document
     * @return fetched assets in reference order
     * @throws IllegalArgumentException if a URL, content type, or media size is unsupported
     * @throws IOException if a host cannot be resolved or a media server cannot be read
     */
    public List<MediaAsset> fetch(List<URI> references) throws IOException {
        if (references.size() > MAX_MEDIA_COUNT) {
            throw new IllegalArgumentException("The HTML file references more than 50 media items.");
        }
        var completion = new ExecutorCompletionService<IndexedAsset>(DOWNLOADS);
        var tasks = new ArrayList<Future<IndexedAsset>>();
        var requests = new DownloadRequests();
        var totalBytes = new AtomicLong();
        MediaAsset[] assets = new MediaAsset[references.size()];
        int submitted = 0;
        int completed = 0;
        try {
            while (completed < references.size()) {
                while (submitted < references.size() && submitted - completed < MAX_CONCURRENT_DOWNLOADS) {
                    int index = submitted++;
                    tasks.add(completion.submit(() -> new IndexedAsset(index,
                            fetchOne(references.get(index), totalBytes, requests))));
                }
                IndexedAsset asset = completion.take().get();
                assets[asset.index()] = asset.asset();
                completed++;
            }
            return List.of(assets);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Media download was interrupted.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IOException("Media download failed.", cause);
        } catch (RejectedExecutionException e) {
            throw new IOException("Media downloader is unavailable.", e);
        } finally {
            requests.cancel();
            tasks.forEach(task -> task.cancel(true));
        }
    }

    private MediaAsset fetchOne(URI uri, AtomicLong totalBytes, DownloadRequests requests) throws IOException {
        validateRemoteUri(uri, addressResolver);
        var request = new HttpGet(uri);
        requests.add(request);
        request.setHeader("Accept", String.join(", ", SUPPORTED_TYPES));
        try (var response = httpClient.execute(request)) {
            if (response.getCode() < 200 || response.getCode() >= 300) {
                throw new IOException("Media server returned HTTP " + response.getCode() + " for " + safeHost(uri) + ".");
            }
            String contentType = response.getFirstHeader("Content-Type") == null
                    ? ""
                    : response.getFirstHeader("Content-Type").getValue().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!SUPPORTED_TYPES.contains(contentType)) {
                throw new IllegalArgumentException("Referenced media has an unsupported content type: " + safeHost(uri));
            }
            var entity = response.getEntity();
            if (entity == null) {
                throw new IllegalArgumentException("Referenced media was empty: " + safeHost(uri));
            }
            long contentLength = entity.getContentLength();
            if (contentLength > MAX_MEDIA_BYTES) {
                throw new IllegalArgumentException("Referenced media exceeds the 5 MB limit: " + safeHost(uri));
            }
            byte[] body;
            try (InputStream stream = entity.getContent()) {
                var output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                int itemBytes = 0;
                while ((read = stream.read(buffer)) != -1) {
                    itemBytes += read;
                    if (itemBytes > MAX_MEDIA_BYTES) {
                        throw new IllegalArgumentException("Referenced media exceeds the 5 MB limit: " + safeHost(uri));
                    }
                    if (totalBytes.addAndGet(read) > MAX_TOTAL_MEDIA_BYTES) {
                        throw new IllegalArgumentException("Referenced media exceeds the 20 MB total limit.");
                    }
                    output.write(buffer, 0, read);
                }
                body = output.toByteArray();
            }
            if (body.length == 0) {
                throw new IllegalArgumentException("Referenced media was empty: " + safeHost(uri));
            }
            return new MediaAsset(uri, contentType, body);
        } catch (org.apache.hc.core5.http.NoHttpResponseException e) {
            throw new IOException("The media server did not respond for " + safeHost(uri) + ".", e);
        } finally {
            requests.remove(request);
        }
    }

    @PreDestroy
    void close() throws IOException {
        httpClient.close();
    }

    private record IndexedAsset(int index, MediaAsset asset) {
    }

    private static final class DownloadRequests {
        private final Set<HttpGet> active = new HashSet<>();
        private boolean cancelled;

        synchronized void add(HttpGet request) throws IOException {
            if (cancelled) {
                throw new IOException("Media download was cancelled.");
            }
            active.add(request);
        }

        synchronized void remove(HttpGet request) {
            active.remove(request);
        }

        synchronized void cancel() {
            cancelled = true;
            active.forEach(HttpGet::cancel);
        }
    }

    static void validateRemoteUri(URI uri) throws IOException {
        validateRemoteUri(uri, RemoteMediaFetcher::resolvePublicAddresses);
    }

    static void validateRemoteUri(URI uri, AddressResolver resolver) throws IOException {
        if (uri == null || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("Only public HTTP(S) media URLs on standard ports are supported.");
        }
        String host = uri.getHost();
        if (host.equalsIgnoreCase("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw new IllegalArgumentException("Local and private-network media URLs are not allowed.");
        }
        InetAddress[] addresses = resolver.resolve(host);
        if (addresses.length == 0) {
            throw new IOException("Referenced media host has no addresses: " + host);
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new IllegalArgumentException("Local and private-network media URLs are not allowed.");
            }
        }
    }

    static InetAddress[] resolvePublicAddresses(String host) throws IOException {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IOException("Could not resolve referenced media host: " + host, e);
        }
        if (addresses.length == 0) {
            throw new IOException("Referenced media host has no addresses: " + host);
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new IllegalArgumentException("Local and private-network media URLs are not allowed.");
            }
        }
        return addresses;
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0 && first != 10 && first != 127 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 169 && second == 254)
                    && !(first == 172 && second >= 16 && second <= 31)
                    && !(first == 192 && (second == 0 || second == 168))
                    && !(first == 198 && (second == 18 || second == 19 || second == 51))
                    && !(first == 203 && second == 0 && third == 113);
        }
        if (bytes.length == 16) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first >= 0x20 && first <= 0x3f
                    && !(first == 0x20 && second == 0x01 && Byte.toUnsignedInt(bytes[2]) == 0x0d
                    && Byte.toUnsignedInt(bytes[3]) == 0xb8);
        }
        return false;
    }

    private static String safeHost(URI uri) {
        return uri.getHost() == null ? "unknown host" : uri.getHost();
    }

    /**
     * Fetched media bytes and the metadata needed to pass them to an analyser.
     *
     * @param source original media URI
     * @param contentType validated media content type
     * @param content media bytes
     */
    public record MediaAsset(URI source, String contentType, byte[] content) {
        /**
         * Returns this asset's parsed Spring MIME type.
         *
         * @return the content type as a MIME type
         */
        public MimeType mimeType() {
            return MediaType.parseMediaType(contentType);
        }

        /**
         * Wraps the asset bytes as a Spring resource for model attachment.
         *
         * @return an in-memory resource with a source-based filename
         */
        public ByteArrayResource resource() {
            return new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return source.getHost() + "-media";
                }
            };
        }
    }

    @FunctionalInterface
    interface AddressResolver {
        InetAddress[] resolve(String host) throws IOException;
    }
}
