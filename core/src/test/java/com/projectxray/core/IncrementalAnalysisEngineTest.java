package com.projectxray.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectxray.core.incremental.IncrementalAnalysisEngine;
import com.projectxray.core.model.*;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncrementalAnalysisEngineTest {
    private static String sha(byte[] b) throws Exception {
        var d=MessageDigest.getInstance("SHA-256").digest(b);
        var s=new StringBuilder();
        for(byte x:d)s.append(String.format("%02x",x));
        return s.toString();
    }

    private static AnalysisResult previous(String file, String hash) {
        return new AnalysisResult(
            "1.1", "2026-01-01T00:00:00Z", "demo", "/tmp/demo", "Java",
            100, 1, List.of(), List.of(), List.of(), List.of(),
            new BuildInfo("unknown","","","",List.of(),List.of(),List.of()),
            GitInfo.none(),
            List.of(new FileSnapshot(file, 10, 1, hash)),
            ArchitectureGraph.empty(), DependencyGalaxy.empty(), GitTimeMachine.empty(),
            CodeHealthRadar.empty(), ScanDiagnostics.complete(1,10)
        );
    }

    @Test
    void reusesWhenFingerprintMatches() throws Exception {
        Path root=Files.createTempDirectory("xray-inc");
        Path src=root.resolve("A.java");
        byte[] bytes="class A {}".getBytes();
        Files.write(src,bytes);
        Path report=root.resolve(".xray/analysis.json");
        Files.createDirectories(report.getParent());
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule());
        mapper.writeValue(report.toFile(),previous("A.java",sha(bytes)));

        var result=new IncrementalAnalysisEngine().analyze(root,report);
        assertTrue(result.decision().reused());
        assertEquals(1,result.decision().unchangedFiles());
        assertTrue(result.analysis().warnings().stream().anyMatch(w->w.contains("Incremental cache reused")));
    }

    @Test
    void doesNotReuseWhenFingerprintChanges() throws Exception {
        Path root=Files.createTempDirectory("xray-inc-change");
        Path src=root.resolve("A.java");
        Files.writeString(src,"class A { int x; }");
        Path report=root.resolve(".xray/analysis.json");
        Files.createDirectories(report.getParent());
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule());
        mapper.writeValue(report.toFile(),previous("A.java","definitely-not-the-real-hash"));

        var result=new IncrementalAnalysisEngine().analyze(root,report);
        assertFalse(result.decision().reused());
        assertEquals(1,result.decision().changedFiles());
        assertTrue(result.decision().reason().contains("Source fingerprints changed"));
    }
}
