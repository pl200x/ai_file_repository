package com.example.file_management.exception;

public class InvalidFileVersionException extends RuntimeException {
    public InvalidFileVersionException(String message) {
        super(message);
    }
}
