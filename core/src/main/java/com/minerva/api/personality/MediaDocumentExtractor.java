package com.minerva.api.personality;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class MediaDocumentExtractor {
    public static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    public Extraction extract(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty PDF or HTML file.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("The uploaded file exceeds the 10 MB limit.");
        }

        String filename = file.getOriginalFilename();
        String extension = filename == null ? "" : filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        byte[] bytes = file.getBytes();
        if (extension.equals("pdf")) {
            if (!startsWith(bytes, "%PDF-")) {
                throw new IllegalArgumentException("The uploaded file is not a valid PDF.");
            }
            return extractPdf(filename, bytes);
        }
        if (extension.equals("html") || extension.equals("htm")) {
            return extractHtml(bytes);
        }
        throw new IllegalArgumentException("Only PDF and HTML files are supported.");
    }

    private Extraction extractPdf(String filename, byte[] bytes) throws IOException {
        var resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        try {
            String text = new TikaDocumentReader(resource).get().stream()
                    .map(document -> document.getText())
                    .filter(value -> value != null && !value.isBlank())
                    .reduce((first, second) -> first + "\n" + second)
                    .orElse("");
            if (text.isBlank()) {
                throw new IllegalArgumentException("No readable text could be extracted from the PDF.");
            }
            return new Extraction(text, List.of(), List.of());
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IOException("The uploaded PDF could not be read.", e);
        }
    }

    private Extraction extractHtml(byte[] bytes) {
        String html = new String(bytes, StandardCharsets.UTF_8);
        Document document = Jsoup.parse(html);
        document.select("script, style, noscript, template").remove();

        Map<String, String> tagsByKey = new LinkedHashMap<>();
        for (var element : document.select(".tag")) {
            String tag = element.text().replaceAll("\\s+", " ").trim();
            if (!tag.isEmpty()) {
                tagsByKey.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
            }
        }

        URI base = null;
        var baseElement = document.selectFirst("base[href]");
        if (baseElement != null) {
            try {
                URI candidate = URI.create(baseElement.attr("href").trim());
                if (candidate.isAbsolute() && isHttp(candidate)) {
                    base = candidate;
                }
            } catch (IllegalArgumentException ignored) {
                // An invalid <base> does not make otherwise readable HTML unusable.
            }
        }

        List<URI> media = new ArrayList<>();
        for (var element : document.select("img[src], video[src], video[poster], video source[src]")) {
            String reference = element.hasAttr("poster") ? element.attr("poster").trim() : element.attr("src").trim();
            if (reference.isEmpty()) {
                continue;
            }
            try {
                URI uri = URI.create(reference);
                if (!uri.isAbsolute() && base != null) {
                    uri = base.resolve(uri);
                }
                if (uri.isAbsolute() && isHttp(uri) && !media.contains(uri)) {
                    media.add(uri);
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid and non-HTTP references are not fetched.
            }
        }

        String text = document.body() == null ? "" : document.body().text().replaceAll("\\s+", " ").trim();
        if (text.isBlank() && tagsByKey.isEmpty() && media.isEmpty()) {
            throw new IllegalArgumentException("The HTML file contains no readable text, interest tags, or media references.");
        }
        return new Extraction(text, List.copyOf(tagsByKey.values()), List.copyOf(media));
    }

    private static boolean startsWith(byte[] bytes, String prefix) {
        byte[] expected = prefix.getBytes(StandardCharsets.US_ASCII);
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

    private static boolean isHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }

    public record Extraction(String text, List<String> tags, List<URI> mediaUrls) {
    }
}
