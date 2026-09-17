package com.example.file_management.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class MarkdownExtractionService {

    private static final String[] ALLOWED_EXTENSIONS = {".md", ".markdown"};

    private final long maxBytes;

    public MarkdownExtractionService(
            @Value("${rag.markdown.max-size-bytes:5242880}") long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("rag.markdown.max-size-bytes must be greater than 0");
        }
        this.maxBytes = maxBytes;
    }

    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("upload is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "Markdown file exceeds the " + maxBytes + " byte limit");
        }
        //浏览器/系统对.md文件上报的content-type五花八门(text/markdown、
        //text/plain、application/octet-stream甚至空)，不可靠，改按扩展名判断
        String filename = file.getOriginalFilename();
        boolean hasAllowedExtension = filename != null
                && java.util.Arrays.stream(ALLOWED_EXTENSIONS)
                        .anyMatch(ext -> filename.toLowerCase(Locale.ROOT).endsWith(ext));
        if (!hasAllowedExtension) {
            throw new IllegalArgumentException("only .md/.markdown files are accepted");
        }

        String rawText;
        try {
            rawText = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("unable to read the uploaded file", e);
        }

        String normalized = rawText.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("the uploaded markdown file is empty");
        }
        return normalized;
    }
}
