package com.example.file_management.exception;

public class CantFindTargetFileException extends RuntimeException {
    public CantFindTargetFileException(String message) {
        super(message);
    }
}
