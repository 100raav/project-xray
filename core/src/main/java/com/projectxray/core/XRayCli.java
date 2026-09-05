package com.projectxray.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectxray.core.analysis.JavaProjectAnalyzer;
import com.projectxray.core.incremental.IncrementalAnalysisEngine;
import com.projectxray.core.index.PersistentCodeIndex;
import com.projectxray.core.index.PersistentSymbolStore;
import com.projectxray.core.index.PersistentSymbolResolver;
import com.projectxray.core.index.CompilerContextReport;
import com.projectxray.core.index.PersistentMethodResolver;
import com.projectxray.core.impact.ChangeImpactEngine;
import com.projectxray.core.model.AnalysisResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public final class XRayCli {
    private XRayCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage();
            System.exit(2);
        }

        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        if (args.length >= 2 && "--index".equals(args[1])) {
            Path indexPath = root.resolve(".xray").resolve("code-index.json");
            var doc = new PersistentCodeIndex().load(indexPath, root);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc));
            return;
        }
        if (args.length >= 2 && "--symbols".equals(args[1])) {
            Path storePath = root.resolve(".xray").resolve("symbol-store.json");
            var doc = new PersistentSymbolStore().load(storePath, root);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc));
            return;
        }
        if (args.length >= 4 && "--method".equals(args[1])) {
            Path storePath = root.resolve(".xray").resolve("symbol-store.json");
            var doc = new PersistentSymbolStore().load(storePath, root);
            var parameterTypes = java.util.Arrays.asList(args).subList(4, args.length);
            var result = new PersistentMethodResolver().resolve(doc, args[2], args[3], parameterTypes);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            return;
        }
                if (args.length >= 2 && "--context".equals(args[1])) {
            Path contextPath = root.resolve(".xray").resolve("compiler-context.json");
            if (Files.isRegularFile(contextPath))
                System.out.println(Files.readString(contextPath));
            else
                System.out.println("{\"status\":\"missing\",\"message\":\"Run Analyze Project first.\"}");
            return;
        }
                if (args.length >= 3 && "--resolve".equals(args[1])) {
            Path storePath = root.resolve(".xray").resolve("symbol-store.json");
            var doc = new PersistentSymbolStore().load(storePath, root);
            var resolution = new PersistentSymbolResolver().resolve(doc, args[2]);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resolution));
            return;
        }
        if (args.length >= 3 && "--file-impact".equals(args[1])) {
            Path reportPath = root.resolve(".xray").resolve("analysis.json");
            if (!Files.isRegularFile(reportPath)) {
                System.out.println("{\"status\":\"missing\",\"message\":\"Run Analyze Project first.\"}");
                return;
            }
            AnalysisResult current = mapper.readValue(Files.readString(reportPath), AnalysisResult.class);
            String relativeFile = Path.of(args[2]).normalize().toString().replace('\\','/');
            var entities = current.entities().stream().filter(e -> relativeFile.equals(e.file().replace('\\','/'))).toList();
            var impacts = new java.util.ArrayList<Object>();
            for (var entity : entities) {
                impacts.add(new ChangeImpactEngine().analyze(entity.id(), current.entities(), current.relations(), current.endpoints()));
            }
            var out = new java.util.LinkedHashMap<String,Object>();
            out.put("status", "ok"); out.put("file", relativeFile); out.put("entityCount", entities.size()); out.put("impacts", impacts);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out));
            return;
        }
        if (args.length >= 4 && "--dependents".equals(args[1])) {
            Path storePath = root.resolve(".xray").resolve("symbol-store.json");
            var doc = new PersistentSymbolStore().load(storePath, root);
            var resolution = new PersistentSymbolResolver().resolve(doc, args[2]);
            var symbolId = resolution.resolved() ? resolution.symbol().id() : null;
            int depth = Integer.parseInt(args[3]);
            var out = new LinkedHashMap<String,Object>();
            out.put("resolution", resolution);
            out.put("dependents", new PersistentSymbolResolver().dependents(doc, symbolId, depth));
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out));
            return;
        }
        if (args.length >= 4 && "--dependencies".equals(args[1])) {
            Path storePath = root.resolve(".xray").resolve("symbol-store.json");
            var doc = new PersistentSymbolStore().load(storePath, root);
            var resolution = new PersistentSymbolResolver().resolve(doc, args[2]);
            var symbolId = resolution.resolved() ? resolution.symbol().id() : null;
            int depth = Integer.parseInt(args[3]);
            var out = new LinkedHashMap<String,Object>();
            out.put("resolution", resolution);
            out.put("dependencies", new PersistentSymbolResolver().dependencies(doc, symbolId, depth));
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out));
            return;
        }
        Path persisted = root.resolve(".xray").resolve("analysis.json");
        var incremental = new IncrementalAnalysisEngine();
        IncrementalAnalysisEngine.Result run = incremental.analyze(root, persisted);
        AnalysisResult result = run.analysis();
        Path invalidationPath = root.resolve(".xray").resolve("invalidation-plan.json");
        Files.createDirectories(invalidationPath.getParent());
        Files.writeString(invalidationPath,
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(run.invalidationPlan()));
        System.err.println("X-Ray invalidation: " + run.invalidationPlan().filesToReanalyze().size() + " files planned" +
            " | relationships traversed=" + run.invalidationPlan().traversedRelationships());
        System.err.println("X-Ray scan: " + (run.decision().reused() ? "incremental cache reused" : "full semantic analysis")
            + " | unchanged=" + run.decision().unchangedFiles()
            + " changed=" + run.decision().changedFiles()
            + " added=" + run.decision().addedFiles()
             + " removed=" + run.decision().removedFiles()
            + " semanticNoOp=" + run.decision().semanticallyUnchangedChangedFiles());

        // On-demand impact mode: the impact is calculated from this exact fresh
        // repository analysis. It is never loaded from fixture/demo data.
        if (args.length >= 2 && "--impact".equals(args[1])) {
            if (args.length < 3) {
                System.err.println("Missing entity ID after --impact.");
                System.exit(2);
            }
            var impact = new ChangeImpactEngine().analyze(
                args[2], result.entities(), result.relations(), result.endpoints());
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(impact));
            return;
        }

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        if (args.length >= 2) {
            Path output = Path.of(args[1]).toAbsolutePath().normalize();
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            Files.writeString(output, json);
        } else {
            System.out.println(json);
        }
    }

    private static void usage() {
        System.err.println("Usage:");
        System.err.println("  java -jar xray-core-*.jar <project-path> [output.json]");
        System.err.println("  java -jar xray-core-*.jar <project-path> --impact <entity-id>");
        System.err.println("  java -jar xray-core-*.jar <project-path> --index");
        System.err.println("  java -jar xray-core-*.jar <project-path> --symbols");
        System.err.println("  java -jar xray-core-*.jar <project-path> --context");
        System.err.println("  java -jar xray-core-*.jar <project-path> --method <owner> <method> [parameterType ...]");
        System.err.println("  java -jar xray-core-*.jar <project-path> --resolve <symbol>");
        System.err.println("  java -jar xray-core-*.jar <project-path> --file-impact <relative-java-file>");
        System.err.println("  java -jar xray-core-*.jar <project-path> --dependents <symbol> <depth>");
        System.err.println("  java -jar xray-core-*.jar <project-path> --dependencies <symbol> <depth>");
    }
}
