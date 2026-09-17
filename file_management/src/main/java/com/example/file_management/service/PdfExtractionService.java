package com.example.file_management.service;

import com.example.file_management.exception.PdfHasNoExtractableTextException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.stream.Collectors;

@Service
public class PdfExtractionService {

    private static final String ALLOWED_CONTENT_TYPE = "application/pdf";

    private final long maxBytes;

    public PdfExtractionService(
            @Value("${rag.pdf.max-size-bytes:20971520}") long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("rag.pdf.max-size-bytes must be greater than 0");
        }
        this.maxBytes = maxBytes;
    }

    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("upload is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "PDF exceeds the " + maxBytes + " byte limit");
        }
        if (!ALLOWED_CONTENT_TYPE.equals(file.getContentType())) {
            throw new IllegalArgumentException("only application/pdf is accepted");
        }

        TikaDocumentReader reader;
        try {
            reader = new TikaDocumentReader(file.getResource(), ExtractedTextFormatter.defaults());
        } catch (Exception e) {
            throw new IllegalArgumentException("unable to parse the uploaded file as PDF", e);
        }

        // 实测：单个 PDF 资源稳定产出且只产出 1 个 Document，仍按 List 拼接更保险——
        // 这是 Spring AI 的行为约定，不是接口契约
        String rawText = reader.get().stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        String normalized = normalize(rawText);
        if (normalized.isBlank()) {
            throw new PdfHasNoExtractableTextException(
                    "no extractable text — likely a scanned/image-only PDF, OCR is not enabled");
        }
        return normalized;
    }

    // PDFBox 按版面宽度给每行做右侧空格填充对齐，ExtractedTextFormatter.defaults() 不处理这个；
    // 不清理的话每个 chunk 里都会带着一整段看不见的空白
    private String normalize(String text) {
        return text.lines()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"))
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
