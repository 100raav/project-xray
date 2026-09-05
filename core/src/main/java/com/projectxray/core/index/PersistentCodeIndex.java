package com.projectxray.core.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectxray.core.model.*;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Persistent repository-backed symbol/relationship index.
 *
 * The index is intentionally a materialized cache of X-Ray's real analysis
 * evidence. It never manufactures symbols or relationships. Each record keeps
 * the source file and line where possible, and every indexed file is keyed by
 * its SHA-256 fingerprint.
 */
public final class PersistentCodeIndex {
    public record IndexedFile(
        String path,
        String sha256,
        String semanticSha256,
        long size,
        long modifiedEpochMs,
        List<CodeEntity> entities,
        List<CodeRelation> relations,
        List<CodeEndpoint> endpoints
    ) {}

    public record IndexDocument(
        String schemaVersion,
        String createdAt,
        String rootPath,
        String language,
        List<IndexedFile> files
    ) {
        public static IndexDocument empty(Path root) {
            return new IndexDocument("1.5", Instant.now().toString(), root.toString(), "Java", List.of());
        }
    }

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public IndexDocument load(Path indexPath, Path root) {
        try {
            if (!Files.isRegularFile(indexPath)) return IndexDocument.empty(root);
            IndexDocument d = mapper.readValue(Files.readString(indexPath), IndexDocument.class);
            if (!"1.5".equals(d.schemaVersion())) return IndexDocument.empty(root);
            return d;
        } catch (Exception e) {
            return IndexDocument.empty(root);
        }
    }

    public void save(Path indexPath, IndexDocument document) throws IOException {
        if (indexPath.getParent() != null) Files.createDirectories(indexPath.getParent());
        Path tmp = indexPath.resolveSibling(indexPath.getFileName() + ".tmp");
        Files.writeString(tmp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(tmp, indexPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, indexPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Map<String, IndexedFile> byPath(IndexDocument document) {
        Map<String, IndexedFile> result = new LinkedHashMap<>();
        for (IndexedFile f : document.files()) result.put(f.path(), f);
        return result;
    }

    public static boolean sameFingerprint(IndexedFile oldFile, FileSnapshot current) {
        return oldFile != null && Objects.equals(oldFile.sha256(), current.sha256());
    }

    public static IndexDocument rebuild(Path root, AnalysisResult analysis) {
        Map<String, List<CodeEntity>> entities = new HashMap<>();
        for (CodeEntity e : analysis.entities()) {
            entities.computeIfAbsent(e.file(), k -> new ArrayList<>()).add(e);
        }
        Map<String, List<CodeRelation>> relations = new HashMap<>();
        for (CodeRelation r : analysis.relations()) {
            if (r.evidenceFile() != null) relations.computeIfAbsent(r.evidenceFile(), k -> new ArrayList<>()).add(r);
        }
        Map<String, List<CodeEndpoint>> endpoints = new HashMap<>();
        for (CodeEndpoint e : analysis.endpoints()) {
            endpoints.computeIfAbsent(e.file(), k -> new ArrayList<>()).add(e);
        }

        List<IndexedFile> files = new ArrayList<>();
        for (FileSnapshot f : analysis.files()) {
            String semanticHash = "";
            try {
                semanticHash = SemanticFingerprint.of(root.resolve(f.path()));
            } catch (IOException ignored) {
                // The authoritative source hash remains available; semantic
                // fast-path is simply disabled for this file.
            }
            files.add(new IndexedFile(f.path(), f.sha256(), semanticHash, f.size(), f.modifiedEpochMs(),
                List.copyOf(entities.getOrDefault(f.path(), List.of())),
                List.copyOf(relations.getOrDefault(f.path(), List.of())),
                List.copyOf(endpoints.getOrDefault(f.path(), List.of()))));
        }
        return new IndexDocument("1.5", Instant.now().toString(), root.toString(), analysis.language(), files);
    }
}
