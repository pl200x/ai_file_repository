package com.example.file_management.exception;

public class VersionChainConflictException extends RuntimeException {
    public VersionChainConflictException(String message) {
        super(message);
    }
}
