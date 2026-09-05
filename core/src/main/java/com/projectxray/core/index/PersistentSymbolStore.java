package com.projectxray.core.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectxray.core.model.*;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Persistent compiler-context store built from X-Ray's real CodeEntity model.
 *
 * It provides stable symbol lookup by id, qualified name, simple name and
 * source file. It also stores symbol -> relation adjacency so later versions
 * can resolve affected regions without reparsing unaffected source files.
 */
public final class PersistentSymbolStore {
    public record SymbolRecord(
        String id, String kind, String name, String qualifiedName,
        String file, int line, String language, String role
    ) {
        static SymbolRecord from(CodeEntity e) {
            return new SymbolRecord(e.id(), e.kind(), e.name(), e.qualifiedName(),
                e.file(), e.line(), e.language(), e.frameworkRole());
        }
        CodeEntity toEntity() {
            return new CodeEntity(id, kind, name, qualifiedName, file, line, language, role);
        }
    }

    public record RelationRecord(
        String sourceId, String targetId, String type,
        String evidenceFile, int evidenceLine, String evidence
    ) {
        static RelationRecord from(CodeRelation r) {
            return new RelationRecord(r.sourceId(), r.targetId(), r.kind(),
                r.evidenceFile(), r.evidenceLine(), r.evidence());
        }
        CodeRelation toRelation() {
            return new CodeRelation(sourceId, targetId, type, evidenceFile, evidenceLine, evidence);
        }
    }

    public record StoreDocument(
        String schemaVersion, String createdAt, String rootPath,
        String language, List<SymbolRecord> symbols, List<RelationRecord> relations
    ) {
        static StoreDocument empty(Path root) {
            return new StoreDocument("1.0", Instant.now().toString(), root.toString(),
                "Java", List.of(), List.of());
        }
    }

    private final ObjectMapper mapper =
        new ObjectMapper().registerModule(new JavaTimeModule());

    public StoreDocument load(Path path, Path root) {
        try {
            if (!Files.isRegularFile(path)) return StoreDocument.empty(root);
            StoreDocument d = mapper.readValue(Files.readString(path), StoreDocument.class);
            if (!"1.0".equals(d.schemaVersion()) || !Objects.equals(root.toString(), d.rootPath()))
                return StoreDocument.empty(root);
            return d;
        } catch (Exception ignored) {
            return StoreDocument.empty(root);
        }
    }

    public void save(Path path, StoreDocument document) throws IOException {
        Files.createDirectories(path.toAbsolutePath().normalize().getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, mapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(document), StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static StoreDocument build(Path root, AnalysisResult analysis) {
        List<SymbolRecord> symbols = analysis.entities().stream()
            .map(SymbolRecord::from).toList();
        List<RelationRecord> relations = analysis.relations().stream()
            .map(RelationRecord::from).toList();
        return new StoreDocument("1.0", Instant.now().toString(),
            root.toAbsolutePath().normalize().toString(), analysis.language(), symbols, relations);
    }

    public Map<String, SymbolRecord> byId(StoreDocument d) {
        Map<String, SymbolRecord> out = new LinkedHashMap<>();
        for (SymbolRecord s : d.symbols()) out.put(s.id(), s);
        return out;
    }

    public Map<String, SymbolRecord> byQualifiedName(StoreDocument d) {
        Map<String, SymbolRecord> out = new LinkedHashMap<>();
        for (SymbolRecord s : d.symbols()) {
            if (s.qualifiedName() != null && !s.qualifiedName().isBlank())
                out.putIfAbsent(s.qualifiedName(), s);
        }
        return out;
    }

    public Map<String, List<SymbolRecord>> bySimpleName(StoreDocument d) {
        Map<String, List<SymbolRecord>> out = new LinkedHashMap<>();
        for (SymbolRecord s : d.symbols())
            out.computeIfAbsent(s.name(), k -> new ArrayList<>()).add(s);
        return out;
    }

    public Map<String, List<SymbolRecord>> byFile(StoreDocument d) {
        Map<String, List<SymbolRecord>> out = new LinkedHashMap<>();
        for (SymbolRecord s : d.symbols())
            out.computeIfAbsent(s.file(), k -> new ArrayList<>()).add(s);
        return out;
    }

    public Map<String, List<RelationRecord>> outgoing(StoreDocument d) {
        Map<String, List<RelationRecord>> out = new LinkedHashMap<>();
        for (RelationRecord r : d.relations())
            out.computeIfAbsent(r.sourceId(), k -> new ArrayList<>()).add(r);
        return out;
    }

    public Map<String, List<RelationRecord>> incoming(StoreDocument d) {
        Map<String, List<RelationRecord>> out = new LinkedHashMap<>();
        for (RelationRecord r : d.relations())
            out.computeIfAbsent(r.targetId(), k -> new ArrayList<>()).add(r);
        return out;
    }
}
