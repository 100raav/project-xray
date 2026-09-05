package com.projectxray.core.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.projectxray.core.model.AnalysisResult;
import com.projectxray.core.model.CodeEndpoint;
import com.projectxray.core.model.CodeEntity;
import com.projectxray.core.model.CodeRelation;
import com.projectxray.core.model.BuildInfo;
import com.projectxray.core.model.GitInfo;
import com.projectxray.core.model.FileSnapshot;
import com.projectxray.core.model.GitTimeMachine;
import com.projectxray.core.model.CodeHealthRadar;
import com.projectxray.core.project.BuildProjectAnalyzer;
import com.projectxray.core.architecture.ArchitectureGraphBuilder;
import com.projectxray.core.galaxy.DependencyGalaxyBuilder;
import com.projectxray.core.git.GitProjectAnalyzer;
import com.projectxray.core.git.GitTimeMachineEngine;
import com.projectxray.core.health.CodeHealthRadarEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.security.MessageDigest;

/**
 * Repository-backed Java/Spring analyzer.
 *
 * Every emitted entity, relationship and endpoint is derived from the supplied
 * repository. No fixture/demo entities are inserted at runtime.
 */
public final class JavaProjectAnalyzer {
    public AnalysisResult analyze(Path root) throws IOException {
        return analyze(root, null);
    }

    /**
     * Analyze with optional semantic-file scope. Parsing and type registration
     * remain repository-wide so Java symbol resolution has complete context;
     * expensive relationship/endpoint extraction can be restricted to affected
     * files. A null scope means full analysis.
     */
    public AnalysisResult analyze(Path root, Set<String> semanticFiles) throws IOException {
        return analyze(root, true, semanticFiles);
    }

    /** Internal mode used by Git Time Machine so snapshot analysis does not recursively analyze history. */
    public AnalysisResult analyze(Path root, boolean includeGitHistory) throws IOException {
        return analyze(root, includeGitHistory, null);
    }

    private AnalysisResult analyze(Path root, boolean includeGitHistory, Set<String> semanticFiles) throws IOException {
        long started = System.nanoTime();
        root = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Project path is not a directory: " + root);
        }
        final Path normalizedRoot = root;

        List<CodeEntity> entities = new ArrayList<>();
        List<CodeRelation> relations = new ArrayList<>();
        List<CodeEndpoint> endpoints = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> qualifiedNameToId = new HashMap<>();
        Map<String, String> simpleNameToId = new HashMap<>();
        Map<String, String> methodSignatureToId = new HashMap<>();
        Map<Path, CompilationUnit> units = new LinkedHashMap<>();

        List<Path> files;
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !isIgnored(p, normalizedRoot))
                .sorted()
                .toList();
        }

        // Large-repository policy: no artificial default cap. A deployment may
        // set XRAY_MAX_JAVA_FILES to protect memory/CI budgets; truncation is
        // explicit in warnings and never silently represented as complete.
        String maxFilesEnv = System.getenv("XRAY_MAX_JAVA_FILES");
        if (maxFilesEnv != null && !maxFilesEnv.isBlank()) {
            try {
                int max = Integer.parseInt(maxFilesEnv);
                if (max > 0 && files.size() > max) {
                    warnings.add("Repository contains " + files.size() +
                        " Java files; XRAY_MAX_JAVA_FILES=" + max +
                        " limits this run. Results are partial.");
                    files = files.subList(0, max);
                }
            } catch (NumberFormatException ex) {
                warnings.add("Ignoring invalid XRAY_MAX_JAVA_FILES value: " + maxFilesEnv);
            }
        }

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(root.toFile()));
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration configuration = new ParserConfiguration().setSymbolResolver(symbolSolver);
        JavaParser parser = new JavaParser(configuration);

        // Pass 1: parse all source units and register types/methods before resolving relationships.
        for (Path file : files) {
            try {
                ParseResult<CompilationUnit> parsed = parser.parse(file);
                if (parsed.getResult().isEmpty()) {
                    warnings.add("Could not parse: " + root.relativize(file));
                    continue;
                }
                CompilationUnit cu = parsed.getResult().get();
                units.put(file, cu);
                String pkg = cu.getPackageDeclaration().map(x -> x.getNameAsString()).orElse("");

                for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                    String qn = qualifiedTypeName(type, pkg);
                    String id = "type:" + qn;
                    String role = frameworkRole(type);
                    int line = line(type);
                    entities.add(new CodeEntity(id, type.isInterface() ? "interface" : "class",
                        type.getNameAsString(), qn, root.relativize(file).toString(), line, "Java", role));
                    qualifiedNameToId.put(qn, id);
                    simpleNameToId.putIfAbsent(type.getNameAsString(), id);

                    for (MethodDeclaration method : type.getMethods()) {
                        String methodId = id + "#method:" + methodSignature(method);
                        String qMethod = qn + "#" + methodSignature(method);
                        entities.add(new CodeEntity(methodId, "method", method.getNameAsString(), qMethod,
                            root.relativize(file).toString(), line(method), "Java", null));
                        methodSignatureToId.put(qMethod, methodId);
                    }
                }
            } catch (Exception ex) {
                warnings.add("Analysis error in " + root.relativize(file) + ": " + safeMessage(ex));
            }
        }

        // Pass 2: semantic relationships, dependency injection and HTTP endpoints.
        // When scoped, only affected source files emit new graph evidence.
        for (Map.Entry<Path, CompilationUnit> entry : units.entrySet()) {
            Path file = entry.getKey();
            if (semanticFiles != null && !semanticFiles.contains(root.relativize(file).toString())) continue;
            CompilationUnit cu = entry.getValue();
            String pkg = cu.getPackageDeclaration().map(x -> x.getNameAsString()).orElse("");

            for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                String qn = qualifiedTypeName(type, pkg);
                String ownerId = qualifiedNameToId.get(qn);
                if (ownerId == null) continue;

                for (ClassOrInterfaceType ext : type.getExtendedTypes()) {
                    addResolvedTypeRelation(relations, ownerId, ext.getNameAsString(), qualifiedNameToId,
                        simpleNameToId, "extends");
                }
                for (ClassOrInterfaceType impl : type.getImplementedTypes()) {
                    addResolvedTypeRelation(relations, ownerId, impl.getNameAsString(), qualifiedNameToId,
                        simpleNameToId, "implements");
                }

                // Field dependencies: useful for constructor/field injection and real type relationships.
                for (var field : type.getFields()) {
                    boolean injectionAnnotation = has(field, "Autowired") || has(field, "Inject") || has(field, "Resource");
                    for (VariableDeclarator variable : field.getVariables()) {
                        resolveLocalType(variable.getType(), qualifiedNameToId, simpleNameToId).ifPresent(target ->
                            relations.add(new CodeRelation(ownerId, target, injectionAnnotation ? "injects" : "uses-type")));
                    }
                }

                // Constructor parameter dependencies are common in modern Spring applications.
                for (var ctor : type.getConstructors()) {
                    for (var parameter : ctor.getParameters()) {
                        resolveLocalType(parameter.getType(), qualifiedNameToId, simpleNameToId).ifPresent(target ->
                            relations.add(new CodeRelation(ownerId, target, "injects")));
                    }
                }

                for (MethodDeclaration method : type.getMethods()) {
                    String methodId = ownerId + "#method:" + methodSignature(method);

                    // HTTP endpoint discovery from Spring MVC/Web annotations.
                    for (var annotation : method.getAnnotations()) {
                        String ann = annotationSimpleName(annotation.getNameAsString());
                        String http = httpMethod(ann);
                        if (http != null || ann.equals("RequestMapping")) {
                            String methodPath = annotationStringValue(annotation).orElse("");
                            String classPath = classRequestMapping(type);
                            String route = joinPaths(classPath, methodPath);
                            if (route.isBlank()) route = "/";
                            String endpointId = "endpoint:" + httpOrRequestMapping(http) + ":" + route + ":" + methodId;
                            endpoints.add(new CodeEndpoint(endpointId, http == null ? "REQUEST" : http,
                                route, ownerId, methodId, root.relativize(file).toString(), line(method)));
                            relations.add(new CodeRelation(endpointId, methodId, "routes-to", root.relativize(file).toString(), line(method), route));
                        }
                    }

                    for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                        // Prefer semantic method resolution. If resolution fails, preserve an explicitly marked unresolved edge.
                        try {
                            ResolvedMethodDeclaration resolved = call.resolve();
                            String targetId = methodSignatureToId.get(resolved.getQualifiedSignature());
                            if (targetId != null) {
                                relations.add(new CodeRelation(methodId, targetId, "calls", root.relativize(file).toString(), line(call), call.toString()));
                            } else {
                                relations.add(new CodeRelation(methodId, "resolved-method:" + resolved.getQualifiedSignature(), "calls-unmapped", root.relativize(file).toString(), line(call), call.toString()));
                            }
                        } catch (Exception ignored) {
                            String scope = call.getScope().map(Object::toString).orElse("");
                            String targetName = scope.isBlank() ? call.getNameAsString() : scope + "." + call.getNameAsString();
                            relations.add(new CodeRelation(methodId, "unresolved-call:" + targetName, "calls-unresolved", root.relativize(file).toString(), line(call), call.toString()));
                        }

                        // Connect a receiver expression to a local type where it can be established conservatively.
                        call.getScope().filter(NameExpr.class::isInstance).map(NameExpr.class::cast).ifPresent(scope -> {
                            try {
                                ResolvedReferenceTypeDeclaration decl = null;
                                var resolvedType = scope.resolve().getType();
                                if (resolvedType.isReferenceType()) {
                                    decl = resolvedType.asReferenceType().getTypeDeclaration().orElse(null);
                                }
                                if (decl != null) {
                                    String target = qualifiedNameToId.get(decl.getQualifiedName());
                                    if (target != null) relations.add(new CodeRelation(methodId, target, "uses-type"));
                                }
                            } catch (Exception ignored) { }
                            method.getParameters().stream()
                                .filter(parameter -> parameter.getNameAsString().equals(scope.getNameAsString()))
                                .findFirst()
                                .flatMap(parameter -> resolveLocalType(parameter.getType(), qualifiedNameToId, simpleNameToId))
                                .ifPresent(target -> relations.add(new CodeRelation(methodId, target, "uses-type")));
                        });
                    }
                }

                for (var imp : cu.getImports()) {
                    String imported = imp.getNameAsString();
                    String targetId = qualifiedNameToId.get(imported);
                    relations.add(new CodeRelation(ownerId, targetId != null ? targetId : "import:" + imported, "imports"));
                }
            }
        }

        // 0.4 adds real project/build/Git metadata and immutable file fingerprints.
        BuildInfo build = new BuildProjectAnalyzer().analyze(root);
        GitInfo git = new GitProjectAnalyzer().analyze(root);
        List<FileSnapshot> snapshots = files.stream().map(file -> snapshot(normalizedRoot, file, warnings)).toList();
        warnings.addAll(build.warnings());

        long durationMs = (System.nanoTime() - started) / 1_000_000;
        String projectName = root.getFileName() != null ? root.getFileName().toString() : root.toString();
        var architectureGraph = new ArchitectureGraphBuilder().build(projectName, entities, relations);
        var dependencyGalaxy = new DependencyGalaxyBuilder().build(projectName, entities, relations);
        var timeMachine = includeGitHistory ? new GitTimeMachineEngine().analyze(root, 12) : GitTimeMachine.empty();
        var preliminary = new AnalysisResult(projectName, root.toString(), "Java", durationMs, files.size(),
            List.copyOf(entities), dedupe(relations), dedupeEndpoints(endpoints), List.copyOf(warnings),
            build, git, snapshots, architectureGraph, dependencyGalaxy, timeMachine, CodeHealthRadar.empty());
        var radar = new CodeHealthRadarEngine().analyze(preliminary);
        long sourceBytes = files.stream().mapToLong(f -> {
            try { return Files.size(f); } catch (IOException ex) { return 0L; }
        }).sum();
        var scanDiagnostics = new com.projectxray.core.model.ScanDiagnostics(
            !warnings.stream().anyMatch(w -> w.contains("Results are partial")),
            "repository-wide Java semantic scan",
            files.size(), units.size(), sourceBytes,
            warnings.stream().filter(w -> w.contains("Results are partial")).findFirst().orElse(""));
        return new AnalysisResult(preliminary.schemaVersion(), preliminary.analyzedAt(), preliminary.project(),
            preliminary.rootPath(), preliminary.language(), preliminary.durationMs(), preliminary.filesScanned(),
            preliminary.entities(), preliminary.relations(), preliminary.endpoints(), preliminary.warnings(),
            preliminary.build(), preliminary.git(), preliminary.files(), preliminary.architectureGraph(),
            preliminary.dependencyGalaxy(), preliminary.gitTimeMachine(), radar, scanDiagnostics);
    }

    private static FileSnapshot snapshot(Path root, Path file, List<String> warnings) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest(bytes)) hex.append(String.format("%02x", b));
            return new FileSnapshot(root.relativize(file).toString(), Files.size(file),
                Files.getLastModifiedTime(file).toMillis(), hex.toString());
        } catch (Exception e) {
            warnings.add("Could not fingerprint: " + root.relativize(file) + " (" + safeMessage(e) + ")");
            return new FileSnapshot(root.relativize(file).toString(), -1, -1, "");
        }
    }

    private static boolean isIgnored(Path path, Path root) {
        Path rel = root.relativize(path);
        for (Path part : rel) {
            String n = part.toString();
            if (Set.of(".git", "target", "build", "out", "node_modules", ".gradle", ".idea").contains(n)) return true;
        }
        return false;
    }

    private static String qualifiedTypeName(ClassOrInterfaceDeclaration type, String pkg) {
        List<String> names = new ArrayList<>();
        ClassOrInterfaceDeclaration current = type;
        while (current != null) {
            names.add(current.getNameAsString());
            current = current.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        }
        Collections.reverse(names);
        String local = String.join(".", names);
        return pkg.isBlank() ? local : pkg + "." + local;
    }

    private static String methodSignature(MethodDeclaration method) {
        return method.getNameAsString() + "(" + method.getParameters().stream()
            .map(p -> p.getType().asString()).reduce((a,b) -> a + "," + b).orElse("") + ")";
    }

    private static void addResolvedTypeRelation(List<CodeRelation> relations, String ownerId, String name,
                                                Map<String,String> qn, Map<String,String> simple, String kind) {
        String targetId = qn.get(name);
        if (targetId == null) targetId = simple.get(name);
        relations.add(new CodeRelation(ownerId, targetId != null ? targetId : "symbol:" + name, kind));
    }

    private static Optional<String> resolveLocalType(Type type, Map<String,String> qn, Map<String,String> simple) {
        String text = type.asString().replaceAll("<.*>", "").replace("[]", "").trim();
        String id = qn.get(text);
        if (id == null) id = simple.get(text);
        return Optional.ofNullable(id);
    }

    private static String frameworkRole(ClassOrInterfaceDeclaration type) {
        if (has(type, "RestController") || has(type, "Controller")) return "controller";
        if (has(type, "Service")) return "service";
        if (has(type, "Repository")) return "repository";
        if (has(type, "Entity")) return "entity";
        if (has(type, "Configuration")) return "configuration";
        if (has(type, "Component")) return "component";
        return null;
    }

    private static boolean has(NodeWithAnnotations<?> node, String name) {
        return node.getAnnotations().stream().anyMatch(a -> annotationSimpleName(a.getNameAsString()).equals(name));
    }

    private static int line(com.github.javaparser.ast.Node n) {
        return n.getBegin().map(p -> p.line).orElse(-1);
    }

    private static String httpMethod(String annotation) {
        return switch (annotation) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "DeleteMapping" -> "DELETE";
            case "PatchMapping" -> "PATCH";
            case "HeadMapping" -> "HEAD";
            case "OptionsMapping" -> "OPTIONS";
            default -> null;
        };
    }

    private static String httpOrRequestMapping(String http) { return http == null ? "REQUEST" : http; }

    private static Optional<String> annotationStringValue(com.github.javaparser.ast.expr.AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            var value = annotation.asSingleMemberAnnotationExpr().getMemberValue();
            if (value.isStringLiteralExpr()) return Optional.of(value.asStringLiteralExpr().asString());
        }
        if (annotation.isNormalAnnotationExpr()) {
            for (var pair : annotation.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path")) {
                    if (pair.getValue().isStringLiteralExpr()) return Optional.of(pair.getValue().asStringLiteralExpr().asString());
                    if (pair.getValue().isArrayInitializerExpr() && !pair.getValue().asArrayInitializerExpr().getValues().isEmpty()
                        && pair.getValue().asArrayInitializerExpr().getValues().get(0).isStringLiteralExpr()) {
                        return Optional.of(pair.getValue().asArrayInitializerExpr().getValues().get(0).asStringLiteralExpr().asString());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static String classRequestMapping(ClassOrInterfaceDeclaration type) {
        for (var a : type.getAnnotations()) {
            if (annotationSimpleName(a.getNameAsString()).equals("RequestMapping")) return annotationStringValue(a).orElse("");
        }
        return "";
    }

    private static String joinPaths(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        if (left.isBlank()) return normalizePath(right);
        if (right.isBlank()) return normalizePath(left);
        return normalizePath(left + "/" + right);
    }

    private static String normalizePath(String p) {
        if (p == null || p.isBlank()) return "/";
        String x = p.trim().replaceAll("/+", "/");
        if (!x.startsWith("/")) x = "/" + x;
        if (x.length() > 1 && x.endsWith("/")) x = x.substring(0, x.length()-1);
        return x;
    }

    private static List<CodeRelation> dedupe(List<CodeRelation> input) {
        return new ArrayList<>(new LinkedHashSet<>(input));
    }

    private static List<CodeEndpoint> dedupeEndpoints(List<CodeEndpoint> input) {
        return new ArrayList<>(new LinkedHashSet<>(input));
    }

    private static String annotationSimpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private static String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
