package com.projectxray.core;

import com.projectxray.core.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisResultSchemaTest {
    @Test
    void schemaIsOneAndGalaxyIsPresent() {
        AnalysisResult r = new AnalysisResult(
            "repo", "/tmp/repo", "Java", 1, 0,
            List.of(), List.of(), List.of(), List.of(),
            new BuildInfo("unknown","","","",List.of(),List.of(),List.of()),
            new GitInfo(false,"","",false,List.of(),""),
            List.of(), ArchitectureGraph.empty(), DependencyGalaxy.empty()
        );
        assertEquals("1.0", r.schemaVersion());
        assertNotNull(r.dependencyGalaxy());
    }
}
