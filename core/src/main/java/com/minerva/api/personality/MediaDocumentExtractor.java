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

/**
 * Extracts readable text, interest tags, and eligible media URLs from PDF and HTML uploads.
 */
@Component
public class MediaDocumentExtractor {
    /**
     * Extracts content from a supported document upload.
     *
     * @param file uploaded PDF or HTML document
     * @return extracted text, tags, media URLs, and image alt text
     * @throws IllegalArgumentException if the upload is empty, invalid, or unsupported
     * @throws IOException if a valid PDF cannot be read
     */
    public Extraction extract(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty PDF or HTML file.");
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
            return new Extraction(text, List.of(), List.of(), List.of());
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

        List<String> imageAltTexts = document.select("img[alt]").stream()
                .map(element -> element.attr("alt").replaceAll("\\s+", " ").trim())
                .filter(value -> !value.isEmpty())
                .toList();

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
        if (text.isBlank() && tagsByKey.isEmpty() && media.isEmpty() && imageAltTexts.isEmpty()) {
            throw new IllegalArgumentException("The HTML file contains no readable text, interest tags, media references, or image alt text.");
        }
        return new Extraction(text, List.copyOf(tagsByKey.values()), List.copyOf(media), imageAltTexts);
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

    /**
     * Content extracted from one uploaded document.
     *
     * @param text readable document text
     * @param tags unique interest tags found in the document
     * @param mediaUrls eligible HTTP(S) media references found in the document
     * @param imageAltTexts non-blank alt text from HTML image elements
     */
    public record Extraction(String text, List<String> tags, List<URI> mediaUrls, List<String> imageAltTexts) {
    }
}
