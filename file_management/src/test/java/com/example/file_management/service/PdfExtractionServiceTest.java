package com.example.file_management.service;

import com.example.file_management.exception.PdfHasNoExtractableTextException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExtractionServiceTest {

    private final PdfExtractionService service = new PdfExtractionService(20_971_520L);

    @Test
    void extractsTextFromAPdfWithContent() throws IOException {
        byte[] pdf = buildPdf("Hello world");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.pdf", "application/pdf", pdf);

        String text = service.extractText(file);

        assertTrue(text.contains("Hello world"));
    }

    @Test
    void rejectsAnEmptyUpload() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> service.extractText(file));
    }

    @Test
    void rejectsUploadsOverTheConfiguredLimit() throws IOException {
        PdfExtractionService tightService = new PdfExtractionService(10L);
        byte[] pdf = buildPdf("Hello world");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.pdf", "application/pdf", pdf);

        assertThrows(IllegalArgumentException.class, () -> tightService.extractText(file));
    }

    @Test
    void rejectsANonPdfContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.txt", "text/plain", "not a pdf".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.extractText(file));
    }

    @Test
    void rejectsAPdfWithNoExtractableText() throws IOException {
        byte[] pdf = buildBlankPdf();
        MockMultipartFile file = new MockMultipartFile(
                "file", "scanned.pdf", "application/pdf", pdf);

        assertThrows(PdfHasNoExtractableTextException.class, () -> service.extractText(file));
    }

    @Test
    void constructorRejectsANonPositiveByteLimit() {
        assertThrows(IllegalArgumentException.class, () -> new PdfExtractionService(0L));
    }

    private byte[] buildPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildBlankPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
