package com.projectxray.core.incremental;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectxray.core.analysis.JavaProjectAnalyzer;
import com.projectxray.core.index.PersistentCodeIndex;
import com.projectxray.core.index.DependencyAwareInvalidationEngine;
import com.projectxray.core.index.PartialAnalysisMerger;
import com.projectxray.core.index.PersistentSymbolStore;
import com.projectxray.core.index.CompilerContextReport;
import com.projectxray.core.model.AnalysisResult;
import com.projectxray.core.model.FileSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * Repository-backed incremental analysis coordinator.
 *
 * Fast path: when the persisted X-Ray result proves that every analyzed Java
 * source fingerprint is unchanged, reuse the semantic model instead of parsing
 * the repository again. When anything relevant changes, fall back to the full
 * semantic analyzer. This is intentionally conservative: 1.1 never pretends
 * that a partial dependency graph is complete.
 */
public final class IncrementalAnalysisEngine {
    public record Decision(boolean reused, int unchangedFiles, int changedFiles,
                           int addedFiles, int removedFiles, int semanticallyUnchangedChangedFiles,
                           String reason) {}

    public record Result(AnalysisResult analysis, Decision decision, DependencyAwareInvalidationEngine.Plan invalidationPlan) {}

    public Result analyze(Path root, Path persistedReport) throws IOException {
        root = root.toAbsolutePath().normalize();
        Path indexPath = root.resolve(".xray").resolve("code-index.json");
        var index = new PersistentCodeIndex();
        Optional<AnalysisResult> previous = load(persistedReport);

        if (previous.isPresent()) {
            AnalysisResult old = previous.get();
            Map<String,String> oldHashes = javaHashes(old.files());
            Map<String,String> currentHashes = currentJavaHashes(root);

            int unchanged = 0, changed = 0, added = 0, removed = 0;
            for (var e : currentHashes.entrySet()) {
                if (!oldHashes.containsKey(e.getKey())) added++;
                else if (Objects.equals(oldHashes.get(e.getKey()), e.getValue())) unchanged++;
                else changed++;
            }
            for (String oldPath : oldHashes.keySet()) {
                if (!currentHashes.containsKey(oldPath)) removed++;
            }

            if (changed == 0 && added == 0 && removed == 0 && !currentHashes.isEmpty()) {
                // Preserve the authoritative repository-derived model, but make
                // cache reuse visible so consumers never mistake it for a new parse.
                List<String> warnings = new ArrayList<>(old.warnings());
                warnings.add("Incremental cache reused: all analyzed Java source fingerprints are unchanged.");
                AnalysisResult reused = new AnalysisResult(
                    old.schemaVersion(), old.analyzedAt(), old.project(), old.rootPath(),
                    old.language(), 0L, old.filesScanned(), old.entities(), old.relations(),
                    old.endpoints(), List.copyOf(warnings), old.build(), old.git(), old.files(),
                    old.architectureGraph(), old.dependencyGalaxy(), old.gitTimeMachine(),
                    old.codeHealthRadar(), old.scanDiagnostics()
                );
                return new Result(reused, new Decision(true, unchanged, 0, 0, 0, 0,
                    "All analyzed Java source fingerprints match the persisted X-Ray model."),
                    emptyPlan(Set.of()));
            }

            var changedFiles = new LinkedHashSet<String>();
            var addedFiles = new LinkedHashSet<String>();
            for (var e : currentHashes.entrySet()) {
                if (!oldHashes.containsKey(e.getKey())) addedFiles.add(e.getKey());
                else if (!Objects.equals(oldHashes.get(e.getKey()), e.getValue())) changedFiles.add(e.getKey());
            }
            var removedFiles = new LinkedHashSet<>(oldHashes.keySet());
            removedFiles.removeAll(currentHashes.keySet());

            var persistedIndex = index.load(indexPath, root);
            var indexedByPath = index.byPath(persistedIndex);
            int semanticUnchanged = 0;
            for (String changedPath : changedFiles) {
                var oldFile = indexedByPath.get(changedPath);
                if (oldFile == null || oldFile.semanticSha256() == null || oldFile.semanticSha256().isBlank()) continue;
                Path currentFile = root.resolve(changedPath);
                try {
                    if (Objects.equals(oldFile.semanticSha256(), com.projectxray.core.index.SemanticFingerprint.of(currentFile))) {
                        semanticUnchanged++;
                    }
                } catch (IOException ignored) {}
            }

            var plan = new DependencyAwareInvalidationEngine().plan(
                persistedIndex, changedFiles, addedFiles, removedFiles);

            if (!changedFiles.isEmpty() && semanticUnchanged == changedFiles.size()
                    && addedFiles.isEmpty() && removedFiles.isEmpty()) {
                List<String> warnings = new ArrayList<>(old.warnings());
                warnings.add("Incremental semantic no-op: changed Java files differ only in comments/whitespace/literal-preserving formatting; semantic model reused.");
                AnalysisResult reused = new AnalysisResult(
                    old.schemaVersion(), old.analyzedAt(), old.project(), old.rootPath(),
                    old.language(), 0L, old.filesScanned(), old.entities(), old.relations(),
                    old.endpoints(), List.copyOf(warnings), old.build(), old.git(), old.files(),
                    old.architectureGraph(), old.dependencyGalaxy(), old.gitTimeMachine(),
                    old.codeHealthRadar(), old.scanDiagnostics()
                );
                return new Result(reused, new Decision(true, unchanged, changed, added, removed, semanticUnchanged,
                    "All changed Java files have identical normalized semantic fingerprints; reused the semantic model."),
                    plan);
            }

            AnalysisResult candidate = new JavaProjectAnalyzer().analyze(root, plan.filesToReanalyze());
            var merged = new PartialAnalysisMerger().merge(old, candidate, plan.filesToReanalyze(), true);
            AnalysisResult fresh;
            String reason;
            if (merged.safe()) {
                fresh = merged.result();
                index.save(indexPath, PersistentCodeIndex.rebuild(root, fresh));
                saveSymbolStore(root, fresh);
                reason = "Dependency-aware partial semantic re-analysis completed for " +
                    plan.filesToReanalyze().size() + " affected files; persistent index refreshed.";
            } else {
                fresh = new JavaProjectAnalyzer().analyze(root);
                index.save(indexPath, PersistentCodeIndex.rebuild(root, fresh));
                saveSymbolStore(root, fresh);
                reason = "Partial merge safety check failed; fell back to complete semantic analysis. " + merged.reason();
            }
            return new Result(fresh, new Decision(false, unchanged, changed, added, removed, semanticUnchanged,
                "Source fingerprints changed; " + reason), plan);
        }

        AnalysisResult fresh = new JavaProjectAnalyzer().analyze(root);
        index.save(indexPath, PersistentCodeIndex.rebuild(root, fresh));
                saveSymbolStore(root, fresh);
        return new Result(fresh, new Decision(false, 0, currentJavaHashes(root).size(), 0, 0, 0,
            "No persisted X-Ray model was available; performed a complete semantic analysis and created the persistent code index."),
            emptyPlan(Set.of()));
    }

    private static void saveSymbolStore(Path root, AnalysisResult analysis) throws IOException {
        Path storePath = root.resolve(".xray").resolve("symbol-store.json");
        new PersistentSymbolStore().save(storePath,
            PersistentSymbolStore.build(root, analysis));
        Path contextPath = root.resolve(".xray").resolve("compiler-context.json");
        new CompilerContextReport().save(contextPath,
            new CompilerContextReport().build(root, analysis));
    }

    private static DependencyAwareInvalidationEngine.Plan emptyPlan(Set<String> files) {
        return new DependencyAwareInvalidationEngine.Plan(
            Set.copyOf(files), Set.of(), Set.of(), Set.of(), 0,
            "no invalidation required");
    }

    private static Optional<AnalysisResult> load(Path report) {
        try {
            if (!Files.isRegularFile(report)) return Optional.empty();
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            return Optional.of(mapper.readValue(Files.readString(report), AnalysisResult.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Map<String,String> javaHashes(List<FileSnapshot> files) {
        Map<String,String> out = new HashMap<>();
        for (FileSnapshot f : files) out.put(f.path(), f.sha256());
        return out;
    }

    private static Map<String,String> currentJavaHashes(Path root) throws IOException {
        Map<String,String> out = new HashMap<>();
        try (var stream = Files.walk(root)) {
            for (Path p : stream.filter(Files::isRegularFile)
                .filter(x -> x.toString().endsWith(".java"))
                .filter(x -> !isIgnored(x, root)).toList()) {
                out.put(root.relativize(p).toString(), sha256(p));
            }
        }
        return out;
    }

    private static boolean isIgnored(Path p, Path root) {
        Path rel = root.relativize(p);
        for (Path part : rel) {
            String s = part.toString();
            if (s.equals(".git") || s.equals(".xray") || s.equals("target") ||
                s.equals("build") || s.equals("out") || s.equals(".gradle") ||
                s.equals("node_modules") || s.equals(".idea")) return true;
        }
        return false;
    }

    private static String sha256(Path file) throws IOException {
        try {
            byte[] bytes = Files.readAllBytes(file);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder b = new StringBuilder();
            for (byte x : digest) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            throw new IOException("Unable to fingerprint " + file, e);
        }
    }
}
