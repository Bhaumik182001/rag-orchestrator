# Local Enterprise RAG Orchestrator

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%20%2F%204.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-Ollama%20%26%20PGvector-blue.svg)](https://spring.io/projects/spring-ai)
[![Docker](https://img.shields.io/badge/Docker-Compose%20VPC%20Bridge-2496ED.svg)](https://www.docker.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20pgvector-336791.svg)](https://github.com/pgvector/pgvector)
[![Ollama](https://img.shields.io/badge/Ollama-Local%20Llama%203.2-black.svg)](https://ollama.com/)

A containerized, privacy-first **Retrieval-Augmented Generation (RAG)** microservice designed to index, semantically vectorize, and query private software codebases without transmitting sensitive code or intellectual property outside the local perimeter.

This project implements an enterprise-grade VPC deployment pattern, bridging containerized backend orchestration with local GPU-accelerated inference.

---

## 🏛️ Architecture Overview

The system mirrors an Enterprise Virtual Private Cloud (VPC) deployment. The application layer and database layer are isolated within a private Docker bridge network, while AI embedding and generation tasks are routed through a zero-retention local engine.

```
                              ┌───────────────────────────────────────────────┐
                              │            Windows Host / GPU Layer           │
                              │                                               │
                              │  ┌─────────────────────────────────────────┐  │
                              │  │            Ollama Engine                │  │
                              │  │  - nomic-embed-text (Vector Embeddings) │  │
                              │  │  - llama3.2 (Grounded Generation)       │  │
                              │  └────────────────────▲────────────────────┘  │
                              └───────────────────────┼───────────────────────┘
                                                      │ host.docker.internal:11434
                                                      │ (GPU-Accelerated)
┌─────────────────────────────────────────────────────┼──────────────────────────────────┐
│ Docker Private Network: private-vpc-net             │                                  │
│                                                     ▼                                  │
│  ┌───────────────────────────────┐       ┌──────────────────────────────────────────┐  │
│  │     Client / Developer        │       │   Spring Boot 3 Orchestrator             │  │
│  │     (REST API / Postman)      │──────▶│   (enterprise-orchestrator-app)          │  │
│  └───────────────────────────────┘ :8080 │   - IngestionService (ETL & Chunking)    │  │
│                                          │   - ChatService (Cosine Vector Retrieval)│  │
│                                          └────────────────────▲─────────────────────┘  │
│                                                               │                        │
│                                                               │ Internal JDBC :5432    │
│                                                               ▼                        │
│                                          ┌──────────────────────────────────────────┐  │
│                                          │   PostgreSQL 16 + pgvector               │  │
│                                          │   (enterprise-vector-db)                 │  │
│                                          │   - Cosine Distance Vector Index         │  │
│                                          │   - Code Metadata & Vector Store         │  │
│                                          └──────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## ⚙️ Core Engineering Pipelines

### 1. The Ingestion Pipeline (ETL & Vectorization)

- **File System Traversal:** Recursively scans repositories while applying two-stage exclusion filters (`.git`, `.metadata`, `node_modules`, `target`, `bin`, and hidden dot-directories).
- **Token Slicing:** Uses Spring AI's `TokenTextSplitter` to segment source code (`.java`, `.ts`, `.tsx`, `.sql`, `.md`, `.properties`) into 500-token chunks with 100-token overlap to maintain structural context.
- **Vector Transformation:** Dispatches batches to local `nomic-embed-text` to generate high-dimensional floating-point embeddings.
- **Persistence:** Commits raw chunks, file paths, and project metadata directly into a PostgreSQL `vector_store` table utilizing the `pgvector` extension.

### 2. The Retrieval & Augmentation Pipeline

- **Semantic Query Matching:** Incoming user queries are embedded into vectors and evaluated against the database using Cosine Similarity:
  $$S_C(A, B) = \frac{A \cdot B}{\|A\| \|B\|}$$
- **Threshold Bouncing:** Enforces a configurable similarity threshold (default `0.38`) to reject out-of-domain queries immediately, avoiding unnecessary GPU execution.
- **Context Assembly & Anti-Hallucination Guardrails:** Retrieves top-$K$ candidates ($K=6$), formats them into an explicit context payload, and passes them to `llama3.2` with strict system constraints requiring zero-assumption responses.

---

## 🚀 Tech Stack

- **Language:** Java 21
- **Framework:** Spring Boot 3.x / 4.x, Spring AI
- **Database:** PostgreSQL 16 with `pgvector`
- **Local LLM Host:** Ollama
  - **Embedding Model:** `nomic-embed-text` (~250 MB)
  - **Generation Model:** `llama3.2` (3B parameters, 4-bit quantized)
- **Containerization:** Docker, Docker Compose (Multi-stage build)
- **Target Hardware Profile:** Optimized for 16GB System RAM & 6GB VRAM (e.g., NVIDIA RTX 2060).

---

## 📦 Prerequisites

1. **Docker Desktop** installed and running on your system.
2. **Ollama** installed natively on the host machine (ensures direct GPU access).
   - Pull the required models:
     ```bash
     ollama pull nomic-embed-text
     ollama pull llama3.2
     ```

---

## 🛠️ Quick Start Deployment

### 1. Clone & Configure

```bash
git clone https://github.com/<your-username>/rag-orchestrator.git
cd rag-orchestrator
```

### 2. Launch the Stack via Docker Compose

Run the single-command deployment:

```bash
docker compose up --build -d
```

### 3. Verify Container Health

```bash
docker compose ps
```

You should see:

- `enterprise-vector-db` (healthy on port `5433:5432`)
- `enterprise-orchestrator-app` (running on port `8080:8080`)

---

## 🔌 API Reference

### 1. Ingest a Codebase

Initiates directory extraction, tokenization, embedding, and vector storage.

- **Endpoint:** `POST /api/ingest`
- **Query Parameters:**
  - `path` _(string)_: Absolute path to the directory on the machine.
  - `projectName` _(string)_: Metadata tag associated with the ingested chunks.

**Example Request:**

```http
POST /api/ingest?path=D:/Projects/my-app/src&projectName=MyApp HTTP/1.1
Host: localhost:8080
```

**Example Response:**

```json
{
  "status": "SUCCESS",
  "project": "MyApp",
  "chunksIngested": 18
}
```

---

### 2. Query the Codebase

Searches the vector store using cosine similarity and returns a grounded answer.

- **Endpoint:** `POST /api/chat`
- **Headers:** `Content-Type: application/json`

**Request Body:**

```json
{
  "query": "Which directories and file extensions are filtered out during ingestion?"
}
```

**Example Response:**

```json
{
  "query": "Which directories and file extensions are filtered out during ingestion?",
  "answer": "According to IngestionService.java, directories containing '/.', '/target/', '/bin/', and '/node_modules/' are ignored. Supported file extensions include .java, .properties, .yml, .yaml, .sql, and .md."
}
```

---

## 🔒 Security & Enterprise Design Highlights

- **Decoupled Architecture:** The orchestrator operates as an external observer. Target applications (React, Next.js, Spring Boot) do not require custom dependencies or modification to be indexed.
- **Zero Data Retention:** Code, queries, and vectors never cross the local machine boundary, satisfying strict enterprise IP and compliance requirements.
- **Pluggable Model Provider:** Built on Spring AI abstraction layers. Transitioning from local Ollama to enterprise cloud endpoints (e.g., AWS Bedrock Claude 3.5 Sonnet / Amazon Titan Embeddings inside a corporate VPC) requires modifying only configuration parameters in `application.properties`, without code alterations.

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE).
