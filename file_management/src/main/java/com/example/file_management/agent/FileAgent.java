package com.example.file_management.agent;

@FunctionalInterface
public interface FileAgent {

    String chat(String sessionId, String userInput, int userId);
}
