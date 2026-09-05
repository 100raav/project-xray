package com.projectxray.core;

import com.projectxray.core.index.PartialAnalysisMerger;
import com.projectxray.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PartialAnalysisMergerTest {
    private AnalysisResult result(String root, String file, String id, String hash) {
        var e=new CodeEntity(id,"class",id,id,file,1,"Java",null);
        return new AnalysisResult("demo",root,"Java",1,1,List.of(e),List.of(),List.of(),List.of(),
            new BuildInfo("unknown","","","",List.of(),List.of(),List.of()),GitInfo.none(),
            List.of(new FileSnapshot(file,10,1,hash)),ArchitectureGraph.empty(),
            DependencyGalaxy.empty(),GitTimeMachine.empty(),CodeHealthRadar.empty(),
            ScanDiagnostics.complete(1,10));
    }

    @Test
    void rejectsChangedFileSet() {
        var old=result("/repo","A.java","A","a");
        var partial=result("/repo","B.java","B","b");
        var merged=new PartialAnalysisMerger().merge(old,partial,Set.of("B.java"),true);
        assertFalse(merged.safe());
    }

    @Test
    void rejectsIncompleteClosure() {
        var old=result("/repo","A.java","A","a");
        var partial=result("/repo","A.java","A","a");
        var merged=new PartialAnalysisMerger().merge(old,partial,Set.of("A.java"),false);
        assertFalse(merged.safe());
    }
}
