package com.projectxray.core;

import com.projectxray.core.health.CodeHealthRadarEngine;
import com.projectxray.core.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CodeHealthRadarEngineTest {
    private CodeEntity e(String id, String kind, String name, String file) {
        return new CodeEntity(id, kind, name, "demo."+name, file, 1, "Java", null);
    }

    private AnalysisResult analysis(List<CodeEntity> es, List<CodeRelation> rs, ArchitectureGraph ag, GitTimeMachine tm) {
        return new AnalysisResult("demo","/tmp/demo","Java",1,1,es,rs,List.of(),List.of(),
            new BuildInfo("unknown","","","",List.of(),List.of(),List.of()),
            GitInfo.none(),List.of(),ag,DependencyGalaxy.empty(),tm,CodeHealthRadar.empty());
    }

    @Test
    void reportsCycleAsHighFindingAndMetric() {
        var es=List.of(e("A","class","A","A.java"),e("B","class","B","B.java"));
        var rs=List.of(new CodeRelation("A","B","uses"),new CodeRelation("B","A","uses"));
        var ag=new ArchitectureGraph("A",List.of(),List.of(),List.of(List.of("A","B")),1);
        var r=new CodeHealthRadarEngine().analyze(analysis(es,rs,ag,GitTimeMachine.empty()));
        assertTrue(r.metrics().stream().anyMatch(m->m.id().equals("architecture-cycles") && m.value()==1));
        assertTrue(r.findings().stream().anyMatch(f->f.severity().equals("high")));
    }

    @Test
    void doesNotClaimCoverageWithoutCoverageSource() {
        var es=List.of(e("A","class","A","A.java"));
        var r=new CodeHealthRadarEngine().analyze(analysis(es,List.of(),ArchitectureGraph.empty(),GitTimeMachine.empty()));
        assertTrue(r.warnings().stream().anyMatch(w->w.contains("coverage")));
        assertTrue(r.metrics().stream().noneMatch(m->m.name().toLowerCase().contains("coverage")));
    }

    @Test
    void scoreIsBoundedAndExplainable() {
        var es=List.of(e("A","class","A","A.java"));
        var r=new CodeHealthRadarEngine().analyze(analysis(es,List.of(),ArchitectureGraph.empty(),GitTimeMachine.empty()));
        assertTrue(r.score()>=0 && r.score()<=100);
        assertTrue(r.metrics().stream().anyMatch(m->m.id().equals("overall-score")));
    }
}
