package com.example.file_management.exception;

public class PdfHasNoExtractableTextException extends RuntimeException {
    public PdfHasNoExtractableTextException(String message) {
        super(message);
    }
}
