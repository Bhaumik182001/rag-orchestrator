package com.rag.orchestrator.controller;

import com.rag.orchestrator.service.IngestionService;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<?> ingestLocalPath(
            @RequestParam String path,
            @RequestParam(defaultValue = "GeneralProject") String projectName) {
        try {
            int chunkCount = ingestionService.ingestDirectory(path, projectName);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "chunksIngested", chunkCount,
                    "project", projectName
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/git")
    public ResponseEntity<?> ingestGitRepository(
            @RequestParam String gitUrl,
            @RequestParam(required = false) String projectName) {
        try {
            // Fallback: derive project name from Git URL (e.g. repo.git -> repo)
            String resolvedProjectName = (projectName != null && !projectName.isBlank())
                    ? projectName
                    : extractRepoName(gitUrl);

            int chunkCount = ingestionService.ingestGitRepository(gitUrl, resolvedProjectName);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "gitUrl", gitUrl,
                    "project", resolvedProjectName,
                    "chunksIngested", chunkCount
            ));
        } catch (GitAPIException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to clone repository: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private String extractRepoName(String gitUrl) {
        String cleanUrl = gitUrl.replaceAll("\\.git$", "");
        int lastSlash = cleanUrl.lastIndexOf('/');
        return (lastSlash != -1) ? cleanUrl.substring(lastSlash + 1) : "GitProject";
    }
}
