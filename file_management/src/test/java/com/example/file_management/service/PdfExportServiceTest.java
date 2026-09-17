package com.example.file_management.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExportServiceTest {

    private static final String TINY_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    private final PdfExportService service =
            new PdfExportService(new ClassPathResource("fonts/NotoSansSC-Regular.ttf"));

    PdfExportServiceTest() throws IOException {
    }

    @Test
    void exportsPlainTextContentToAReadablePdf() throws IOException {
        byte[] pdf = service.export("导出验证", "第一行正文\n第二行正文，包含标点：，。！？");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() >= 1);
        }
    }

    @Test
    void exportsContentWithAnEmbeddedImageMarker() throws IOException {
        String content = "图片之前的文字\n"
                + "[[IMAGE:data:image/png;base64," + TINY_PNG_BASE64 + "]]\n"
                + "图片之后的文字";

        byte[] pdf = service.export("含图片的文档", content);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() >= 1);
        }
    }

    @Test
    void exportsEmptyContentAsATitleOnlyPdfWithoutThrowing() throws IOException {
        byte[] pdf = service.export("空文档", "");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() >= 1);
        }
    }

    @Test
    void exportsNullContentAsATitleOnlyPdfWithoutThrowing() throws IOException {
        byte[] pdf = service.export("空文档", null);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() >= 1);
        }
    }
}
