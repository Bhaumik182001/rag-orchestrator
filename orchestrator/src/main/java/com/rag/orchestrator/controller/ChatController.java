package com.rag.orchestrator.controller;

import com.rag.orchestrator.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
	private final ChatService chatService;
	
	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}
	
	@PostMapping
	public ResponseEntity<Map<String, String>> ask(@RequestBody Map<String, String> request){
		String query = request.get("query");
		if(query == null || query.isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("error", "The 'query' field is required."));
		}
		
		String answer = chatService.askCodebase(query);
		return ResponseEntity.ok(Map.of(
				"query", query,
				"answer", answer
		));
	}
}
