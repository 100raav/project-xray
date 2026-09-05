package com.projectxray.core;

import com.projectxray.core.index.SemanticFingerprint;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class SemanticFingerprintTest {
    @Test
    void commentsAndWhitespaceDoNotChangeFingerprint() throws Exception {
        var a=Files.createTempFile("xray-a",".java");
        var b=Files.createTempFile("xray-b",".java");
        Files.writeString(a,"class A {\n  int x = 1; // note\n}\n");
        Files.writeString(b,"/* header */ class A{int x=1;}\n");
        assertEquals(SemanticFingerprint.of(a), SemanticFingerprint.of(b));
    }

    @Test
    void codeChangeChangesFingerprint() throws Exception {
        var a=Files.createTempFile("xray-a",".java");
        var b=Files.createTempFile("xray-b",".java");
        Files.writeString(a,"class A { int x = 1; }\n");
        Files.writeString(b,"class A { int x = 2; }\n");
        assertNotEquals(SemanticFingerprint.of(a), SemanticFingerprint.of(b));
    }

    @Test
    void stringContentsArePreserved() throws Exception {
        var a=Files.createTempFile("xray-a",".java");
        var b=Files.createTempFile("xray-b",".java");
        Files.writeString(a,"class A { String s = \"a b\"; }\n");
        Files.writeString(b,"class A { String s = \"a  b\"; }\n");
        assertNotEquals(SemanticFingerprint.of(a), SemanticFingerprint.of(b));
    }
}
