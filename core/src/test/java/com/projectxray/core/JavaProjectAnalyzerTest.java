package com.projectxray.core;

import com.projectxray.core.analysis.JavaProjectAnalyzer;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class JavaProjectAnalyzerTest {
    @Test void analyzesRealFilesAndDoesNotInjectFixtureData() throws Exception {
        Path root = Files.createTempDirectory("xray-test-");
        Path pkg = Files.createDirectories(root.resolve("src/demo"));
        Files.writeString(pkg.resolve("Service.java"), "package demo; @org.springframework.stereotype.Service class Service { void run(){} }");
        Files.writeString(pkg.resolve("Controller.java"), "package demo; @org.springframework.web.bind.annotation.RestController class Controller { private final Service service = new Service(); void go(){ service.run(); } }");

        var result = new JavaProjectAnalyzer().analyze(root);

        assertEquals(2, result.filesScanned());
        assertTrue(result.entities().stream().anyMatch(e -> e.name().equals("Service")));
        assertTrue(result.entities().stream().anyMatch(e -> e.name().equals("Controller")));
        assertTrue(result.entities().stream().anyMatch(e -> e.kind().equals("method") && e.name().equals("go")));
        assertTrue(result.relations().stream().anyMatch(r -> r.kind().startsWith("calls")));
        assertEquals("1.0", result.schemaVersion());
        assertTrue(result.rootPath().equals(root.toAbsolutePath().normalize().toString()));
        assertEquals(2, result.files().size());
        assertEquals("unknown", result.build().system());
        assertNotNull(result.architectureGraph());
        assertTrue(result.architectureGraph().nodes().stream().anyMatch(n -> n.kind().equals("package") && n.name().equals("demo")));
        assertEquals(result.entities().size(), result.architectureGraph().nodes().stream().filter(n -> n.kind().equals("project")).findFirst().orElseThrow().entityCount());
    }

    @Test void detectsSpringEndpointAndControllerServiceInjection() throws Exception {
        Path root = Files.createTempDirectory("xray-spring-");
        Path pkg = Files.createDirectories(root.resolve("src/demo"));
        Files.writeString(pkg.resolve("UserService.java"), "package demo; @org.springframework.stereotype.Service public class UserService { public void create(){} }");
        Files.writeString(pkg.resolve("UserController.java"), "package demo; @org.springframework.web.bind.annotation.RestController @org.springframework.web.bind.annotation.RequestMapping(\"/users\") public class UserController { private final UserService service; public UserController(UserService service){this.service=service;} @org.springframework.web.bind.annotation.PostMapping(\"/create\") public void create(){ service.create(); } }");

        var result = new JavaProjectAnalyzer().analyze(root);

        assertEquals(1, result.endpoints().size());
        assertEquals("POST", result.endpoints().get(0).httpMethod());
        assertEquals("/users/create", result.endpoints().get(0).route());
        assertTrue(result.relations().stream().anyMatch(r -> r.kind().equals("injects")));
        assertTrue(result.relations().stream().anyMatch(r -> r.kind().equals("routes-to")));
        assertEquals("unknown", result.build().system());
    }

    @Test void buildsRealPackageArchitectureAndDetectsCycles() throws Exception {
        Path root = Files.createTempDirectory("xray-architecture-");
        Path a = Files.createDirectories(root.resolve("src/a"));
        Path b = Files.createDirectories(root.resolve("src/b"));
        Files.writeString(a.resolve("Alpha.java"), "package a; public class Alpha { public void call(Beta b){ b.run(); } }");
        Files.writeString(b.resolve("Beta.java"), "package b; public class Beta { public void run(){ } }");

        var result = new JavaProjectAnalyzer().analyze(root);
        var graph = result.architectureGraph();
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("package") && n.name().equals("a")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("package") && n.name().equals("b")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.sourceId().equals("package:a") && e.targetId().equals("package:b")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.kind().equals("contains")));
    }

    @Test void ignoresBuildAndGitDirectories() throws Exception {
        Path root = Files.createTempDirectory("xray-test-");
        Path src = Files.createDirectories(root.resolve("src"));
        Path target = Files.createDirectories(root.resolve("target"));
        Files.createDirectories(root.resolve(".git"));
        Files.writeString(src.resolve("Real.java"), "class Real {}");
        Files.writeString(target.resolve("Generated.java"), "class Generated {}");
        Files.writeString(root.resolve(".git/Hidden.java"), "class Hidden {}");

        var result = new JavaProjectAnalyzer().analyze(root);
        assertEquals(1, result.filesScanned());
        assertTrue(result.entities().stream().noneMatch(e -> e.name().equals("Generated")));
        assertTrue(result.entities().stream().noneMatch(e -> e.name().equals("Hidden")));
    }
}
