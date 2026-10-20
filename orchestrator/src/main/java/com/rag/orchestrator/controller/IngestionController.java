package com.rag.orchestrator.controller;

import com.rag.orchestrator.service.IngestionService;
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
	public ResponseEntity<?> ingestionProject(
			@RequestParam String path,
			@RequestParam(defaultValue = "GeneralProject") String projectName) {
		try {
			int chunkCount = ingestionService.ingestDirectory(path, projectName);
			return ResponseEntity.ok(Map.of(
					"status", "SUCCESS",
					"chunkIngested", chunkCount,
					"project", projectName
			));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}
}
