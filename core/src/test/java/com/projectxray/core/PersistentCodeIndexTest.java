package com.projectxray.core;

import com.projectxray.core.index.PersistentCodeIndex;
import com.projectxray.core.model.*;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistentCodeIndexTest {
    @Test
    void rebuildCreatesPerFileEvidenceBuckets() {
        var entity = new CodeEntity("type:A","class","A","A","A.java",1,"Java",null);
        var relation = new CodeRelation("type:A","type:B","uses","A.java",4,"field b");
        var endpoint = new CodeEndpoint("endpoint:GET:/a","GET","/a","type:A","type:A#method:a","A.java",7);
        var analysis = new AnalysisResult("demo","/tmp/demo","Java",10,1,
            List.of(entity),List.of(relation),List.of(endpoint),List.of(),
            new BuildInfo("unknown","","","",List.of(),List.of(),List.of()),
            GitInfo.none(),List.of(new FileSnapshot("A.java",10,1,"abc")),
            ArchitectureGraph.empty(),DependencyGalaxy.empty(),GitTimeMachine.empty(),
            CodeHealthRadar.empty(),ScanDiagnostics.complete(1,10));

        var doc=PersistentCodeIndex.rebuild(Path.of("/tmp/demo"),analysis);
        assertEquals("1.5",doc.schemaVersion());
        assertEquals(1,doc.files().size());
        assertEquals(1,doc.files().get(0).entities().size());
        assertEquals(1,doc.files().get(0).relations().size());
        assertEquals(1,doc.files().get(0).endpoints().size());
    }
}
