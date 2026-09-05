package com.projectxray.core;

import com.projectxray.core.analysis.JavaProjectAnalyzer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScopedAnalyzerTest {
    @Test
    void exposesRepositoryContextAwareScopedAnalysis() throws Exception {
        Method m=JavaProjectAnalyzer.class.getMethod("analyze", Path.class, Set.class);
        assertNotNull(m);
    }
}
