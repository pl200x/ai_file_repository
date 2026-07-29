package com.example.file_management.exception;

public class FileTitleConflictException extends RuntimeException {
    public FileTitleConflictException(String message) {
        super(message);
    }
}
