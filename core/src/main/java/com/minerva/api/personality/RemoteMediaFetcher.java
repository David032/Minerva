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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class RemoteMediaFetcher {
    private static final int MAX_MEDIA_COUNT = 8;
    private static final int MAX_MEDIA_BYTES = 5 * 1024 * 1024;
    private static final int MAX_TOTAL_MEDIA_BYTES = 20 * 1024 * 1024;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "video/ogg"
    );
    private final CloseableHttpClient httpClient;
    private final AddressResolver addressResolver;

    public RemoteMediaFetcher() {
        this(createHttpClient(), RemoteMediaFetcher::resolvePublicAddresses);
    }

    RemoteMediaFetcher(CloseableHttpClient httpClient, AddressResolver addressResolver) {
        this.httpClient = httpClient;
        this.addressResolver = addressResolver;
    }

    private static CloseableHttpClient createHttpClient() {
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

    public List<MediaAsset> fetch(List<URI> references) throws IOException {
        if (references.size() > MAX_MEDIA_COUNT) {
            throw new IllegalArgumentException("The HTML file references more than 8 media items.");
        }
        List<MediaAsset> assets = new ArrayList<>();
        long totalBytes = 0;
        for (URI uri : references) {
            validateRemoteUri(uri, addressResolver);
            var request = new HttpGet(uri);
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
                    body = stream.readNBytes(MAX_MEDIA_BYTES + 1);
                }
                if (body.length == 0) {
                    throw new IllegalArgumentException("Referenced media was empty: " + safeHost(uri));
                }
                if (body.length > MAX_MEDIA_BYTES) {
                    throw new IllegalArgumentException("Referenced media exceeds the 5 MB limit: " + safeHost(uri));
                }
                totalBytes += body.length;
                if (totalBytes > MAX_TOTAL_MEDIA_BYTES) {
                    throw new IllegalArgumentException("Referenced media exceeds the 20 MB total limit.");
                }
                assets.add(new MediaAsset(uri, contentType, body));
            } catch (org.apache.hc.core5.http.NoHttpResponseException e) {
                throw new IOException("The media server did not respond for " + safeHost(uri) + ".", e);
            }
        }
        return List.copyOf(assets);
    }

    @PreDestroy
    void close() throws IOException {
        httpClient.close();
    }

    static void validateRemoteUri(URI uri) throws IOException {
        validateRemoteUri(uri, RemoteMediaFetcher::resolvePublicAddresses);
    }

    private static void validateRemoteUri(URI uri, AddressResolver resolver) throws IOException {
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

    private static InetAddress[] resolvePublicAddresses(String host) throws IOException {
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

    private static boolean isPublicAddress(InetAddress address) {
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

    public record MediaAsset(URI source, String contentType, byte[] content) {
        public MimeType mimeType() {
            return MediaType.parseMediaType(contentType);
        }

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
