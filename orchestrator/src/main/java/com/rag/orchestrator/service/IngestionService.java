package com.rag.orchestrator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Service
public class IngestionService {
	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
	
	private final VectorStore vectorStore;
	private final TokenTextSplitter tokenTextSplitter;
	
	public IngestionService(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
		this.tokenTextSplitter =  TokenTextSplitter.builder()
				.withChunkSize(500)
				.withMinChunkSizeChars(200)
				.build();
	}
	
	public int ingestDirectory(String dirPath, String projectName) throws IOException {
		Path rootPath = Paths.get(dirPath);
		
		if(!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
			throw new IllegalArgumentException("Invalid directory path: " + dirPath);
		}
		
		List<Document> documentsToIngest = new ArrayList<>();
		
		try(Stream<Path> paths = Files.walk(rootPath)) {
			paths.filter(Files::isRegularFile)
			.filter(this::isSupportedCodeFile)
			.forEach(path -> {
				try {
					String content = Files.readString(path);
					if(content.isBlank()) return;
					
					Map<String, Object> metadata = new HashMap<>();
					metadata.put("filename", path.getFileName().toString());
					metadata.put("filepath", path.toAbsolutePath().toString());
					metadata.put("projectName", projectName);
					
					Document rawDoc = new Document(content, metadata);
					List<Document> splitDocs = tokenTextSplitter.apply(List.of(rawDoc));
					documentsToIngest.addAll(splitDocs);
				} catch (IOException e) {
					log.error("Faild to read file: {}", path, e);
				}
			});
		}
		
		log.info("Parsed {} code chunks. Sending to Ollama for embedding and PostgreSQL for storage...", documentsToIngest.size());
		vectorStore.accept(documentsToIngest);
		log.info("IngestionComplete");
		return documentsToIngest.size();
	}
	
	private boolean isSupportedCodeFile(Path path) {
        String fullPath = path.toString().replace("\\", "/").toLowerCase();
        String fileName = path.getFileName().toString().toLowerCase();

        // 1. Exclude all hidden directories, build artifacts, and IDE folders
        if (fullPath.contains("/target/") || 
            fullPath.contains("/bin/") || 
            fullPath.contains("/node_modules/") || 
            fullPath.contains("/.git/") || 
            fullPath.contains("/.metadata/") || 
            fullPath.contains("/.settings/") || 
            fullPath.contains("/.vscode/") ||
            fileName.startsWith(".")) {
            return false;
        }

        // 2. Only accept source code extensions we actually care about
        return fileName.endsWith(".java") || 
               fileName.endsWith(".sql") || 
               fileName.endsWith(".properties");
    }
}


