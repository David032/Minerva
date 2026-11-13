package com.minerva.api.personality;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("Unit")
class MediaDocumentExtractorTests {
    private final MediaDocumentExtractor extractor = new MediaDocumentExtractor();

    @Test
    void extractsReadableHtmlTagsAndOnlyHttpMediaReferences() throws Exception {
        String html = """
                <html><head><base href="https://media.example.test/gallery/"></head>
                <body><h1>Art and gardens</h1>
                <span class="tag">  Botanical   art </span><span class="tag">botanical art</span>
                <img src="https://images.example.test/photo.png">
                <video poster="cover.gif"><source src="clip.webm" type="video/webm"></video>
                <audio><source src="sound.ogg"></audio>
                <img src="javascript:alert(1)"><script>ignore me</script></body></html>
                """;
        var upload = new MockMultipartFile("file", "interests.html", "text/html", html.getBytes());

        var extraction = extractor.extract(upload);

        assertTrue(extraction.text().contains("Art and gardens"));
        assertFalse(extraction.text().contains("ignore me"));
        assertEquals(java.util.List.of("Botanical art"), extraction.tags());
        assertEquals(java.util.List.of(
                URI.create("https://images.example.test/photo.png"),
                URI.create("https://media.example.test/gallery/cover.gif"),
                URI.create("https://media.example.test/gallery/clip.webm")
        ), extraction.mediaUrls());
    }

    @Test
    void extractsPdfText() throws Exception {
        byte[] pdf = createPdf("Preference: quiet landscapes");
        var upload = new MockMultipartFile("file", "preferences.pdf", "application/pdf", pdf);

        var extraction = extractor.extract(upload);

        assertTrue(extraction.text().contains("quiet landscapes"));
        assertTrue(extraction.tags().isEmpty());
    }

    @Test
    void rejectsUnsupportedFilesEmptyFilesAndForgedPdfs() {
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(
                new MockMultipartFile("file", "notes.txt", "text/plain", "notes".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(
                new MockMultipartFile("file", "empty.html", "text/html", new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(
                new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes())));
    }

    private static byte[] createPdf(String content) throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(content);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
