package com.projectxray.core;

import com.projectxray.core.galaxy.DependencyGalaxyBuilder;
import com.projectxray.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DependencyGalaxyBuilderTest {

    @Test
    void galaxyContainsOnlyRealEntityEndpoints() {
        CodeEntity a = new CodeEntity("type:a", "class", "A", "demo.a.A", "A.java", 1, "Java", null);
        CodeEntity b = new CodeEntity("type:b", "class", "B", "demo.b.B", "B.java", 1, "Java", null);

        CodeRelation real = new CodeRelation("type:a", "type:b", "uses");
        CodeRelation unresolved = new CodeRelation("type:a", "symbol:DefinitelyMissing", "uses");

        DependencyGalaxy g = new DependencyGalaxyBuilder().build(
            "demo", List.of(a, b), List.of(real, unresolved)
        );

        assertEquals(2, g.nodes().size());
        assertEquals(1, g.edges().size());
        assertEquals("type:a", g.edges().get(0).sourceId());
        assertEquals("type:b", g.edges().get(0).targetId());
        assertTrue(g.nodes().stream().noneMatch(n -> n.id().equals("symbol:DefinitelyMissing")));
    }

    @Test
    void galaxyAggregatesRepeatedRealRelations() {
        CodeEntity a = new CodeEntity("type:a", "class", "A", "demo.a.A", "A.java", 1, "Java", null);
        CodeEntity b = new CodeEntity("type:b", "class", "B", "demo.b.B", "B.java", 1, "Java", null);

        List<CodeRelation> relations = List.of(
            new CodeRelation("type:a", "type:b", "calls"),
            new CodeRelation("type:a", "type:b", "calls"),
            new CodeRelation("type:a", "type:b", "uses-type")
        );

        DependencyGalaxy g = new DependencyGalaxyBuilder().build("demo", List.of(a,b), relations);

        assertEquals(2, g.edges().size());
        assertEquals(3, g.nodes().get(0).outboundRelations() + g.nodes().get(1).outboundRelations());
        assertTrue(g.edges().stream().anyMatch(e -> e.kind().equals("calls") && e.evidenceCount() == 2));
    }

    @Test
    void unresolvedOnlyRelationshipsDoNotCreateGalaxyEdges() {
        CodeEntity a = new CodeEntity("type:a", "class", "A", "demo.a.A", "A.java", 1, "Java", null);
        DependencyGalaxy g = new DependencyGalaxyBuilder().build(
            "demo", List.of(a),
            List.of(new CodeRelation("type:a", "unresolved-call:X.run()", "calls-unresolved"))
        );
        assertEquals(1, g.nodes().size());
        assertEquals(0, g.edges().size());
    }
}
