package com.example.file_management.exception;

public class UserPermissionDeniedException extends RuntimeException {
    public UserPermissionDeniedException(String message) {
        super(message);
    }
}
