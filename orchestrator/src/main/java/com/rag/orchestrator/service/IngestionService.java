package com.rag.orchestrator.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

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
        this.tokenTextSplitter = TokenTextSplitter.builder()
				.withChunkSize(500)
				.withMinChunkSizeChars(200)
				.build();
    }

    /**
     * Clones a remote Git repository (shallow --depth 1) into a temporary directory,
     * indexes its supported source files, and wipes the directory afterwards.
     */
    public int ingestGitRepository(String gitUrl, String projectName) throws IOException, GitAPIException {
        Path tempDir = Files.createTempDirectory("rag-repo-");
        log.info("Cloning Git repository: {} into ephemeral directory: {}", gitUrl, tempDir);

        try {
            // Shallow clone (depth 1) to fetch only latest snapshot without commit history
            try (Git git = Git.cloneRepository()
                    .setURI(gitUrl)
                    .setDirectory(tempDir.toFile())
                    .setDepth(1)
                    .call()) {
                log.info("Repository cloned successfully. Starting vector ingestion...");
            }

            return ingestDirectory(tempDir.toString(), projectName);

        } finally {
            // Ephemeral cleanup: Delete temporary repo files immediately
            log.info("Cleaning up ephemeral directory: {}", tempDir);
            FileSystemUtils.deleteRecursively(tempDir);
        }
    }

    public int ingestDirectory(String dirPath, String projectName) throws IOException {
        Path rootPath = Paths.get(dirPath);
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("Invalid directory path: " + dirPath);
        }

        List<Document> documentsToIngest = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(this::isSupportedCodeFile)
                 .forEach(path -> {
                     try {
                         String content = Files.readString(path);
                         if (content.isBlank()) return;

                         Map<String, Object> metadata = new HashMap<>();
                         metadata.put("fileName", path.getFileName().toString());
                         metadata.put("filePath", path.toAbsolutePath().toString());
                         metadata.put("projectName", projectName);

                         Document rawDoc = new Document(content, metadata);
                         List<Document> splitDocs = tokenTextSplitter.apply(List.of(rawDoc));
                         documentsToIngest.addAll(splitDocs);

                     } catch (IOException e) {
                         log.error("Failed to read file: {}", path, e);
                     }
                 });
        }

        log.info("Parsed {} code chunks for project [{}]. Storing in vector database...", documentsToIngest.size(), projectName);
        vectorStore.accept(documentsToIngest);
        log.info("Ingestion completed for project [{}]", projectName);
        return documentsToIngest.size();
    }

    private boolean isSupportedCodeFile(Path path) {
        String normalizedPath = path.toString().replace("\\", "/").toLowerCase();

        if (normalizedPath.contains("/.") || 
            normalizedPath.contains("/target/") || 
            normalizedPath.contains("/bin/") || 
            normalizedPath.contains("/node_modules/")) {
            return false;
        }

        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".java") || 
               name.endsWith(".ts") || 
               name.endsWith(".tsx") || 
               name.endsWith(".js") || 
               name.endsWith(".sql") || 
               name.endsWith(".properties") ||
               name.endsWith(".yml") ||
               name.endsWith(".yaml") ||
               name.endsWith(".md");
    }
}