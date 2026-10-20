package com.rag.orchestrator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {
	private static final Logger log = LoggerFactory.getLogger(ChatService.class);
	
	private final ChatClient chatClient;
	private final VectorStore vectorStore;
	
	public ChatService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
		this.chatClient = chatClientBuilder.build();
		this.vectorStore = vectorStore;
	}
	
	public String askCodebase(String query) {
		log.info("Searching vector store for query: {}", query);
		
		SearchRequest searchRequest = SearchRequest.builder()
				.query(query)
				.topK(8)
				.similarityThreshold(0.38)
				.build();
		
		List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);
		
		if(similarDocuments.isEmpty()) {
			return "No relevant code snippets found in the database matching your query";
		}
		
		// --- DIAGNOSTIC LOGGING ---
        log.info("=== RETRIEVED {} CHUNKS ===", similarDocuments.size());
        for (int i = 0; i < similarDocuments.size(); i++) {
            Document doc = similarDocuments.get(i);
            String fileName = (String) doc.getMetadata().getOrDefault("fileName", "Unknown");
            String preview = doc.getText().replace("\n", " ");
            log.info("[Chunk {}] From: {} | Preview: {}", 
                i + 1, fileName, preview.substring(0, Math.min(preview.length(), 100)));
        }
        // --------------------------
		
		String context = similarDocuments.stream().map(doc -> {
			String filename = (String) doc.getMetadata().getOrDefault("fileName", "Unknown File");
			return "### Source File: " + filename + "\n" + doc.getText();
		}).collect(Collectors.joining("\n\n---\n\n"));
		
		log.info("Retrieved {} relevant snippet(s). Sending prompt to Llama 3.2 ....");
		
		String systemPrompt = """
                You are a senior software engineer acting as a codebase assistant.
                Answer the user's question accurately using ONLY the provided code snippets in the context below.
                If the context does not contain enough information to answer, state clearly that the information is not present.
                Do not invent or assume code that is not shown.

                Context:
                {context}
                """;
		
		return chatClient.prompt()
				.system(sp -> sp.text(systemPrompt).param("context", context))
				.user(query)
				.call()
				.content();
		
	}
}
