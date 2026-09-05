package com.projectxray.core;

import com.projectxray.core.impact.ChangeImpactEngine;
import com.projectxray.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChangeImpactEngineTest {

    private CodeEntity e(String id, String name) {
        return new CodeEntity(id, "class", name, "demo." + name, name + ".java", 1, "Java", null);
    }

    @Test
    void findsDirectAndTransitiveCallers() {
        // C -> B -> A. Changing A potentially affects B and C.
        var entities = List.of(e("A","A"), e("B","B"), e("C","C"));
        var relations = List.of(
            new CodeRelation("B","A","calls","B.java",10,"B calls A"),
            new CodeRelation("C","B","calls","C.java",20,"C calls B")
        );

        ChangeImpact impact = new ChangeImpactEngine().analyze("A", entities, relations, List.of());

        assertEquals(1, impact.directDependents());
        assertEquals(2, impact.transitiveDependents());
        assertEquals(0, impact.directDependencies());
        assertEquals(0, impact.transitiveDependencies());
        assertEquals(2, impact.affectedCallers().size());
        assertEquals("B", impact.affectedCallers().get(0).targetId());
        assertEquals("C", impact.affectedCallers().get(1).targetId());
        assertTrue(impact.warnings().get(0).contains("static-graph"));
    }

    @Test
    void findsDirectAndTransitiveDependencies() {
        // A -> B -> C. Changing A's implementation can affect its reachable dependencies.
        var entities = List.of(e("A","A"), e("B","B"), e("C","C"));
        var relations = List.of(
            new CodeRelation("A","B","uses-type","A.java",10,"A uses B"),
            new CodeRelation("B","C","calls","B.java",20,"B calls C")
        );

        ChangeImpact impact = new ChangeImpactEngine().analyze("A", entities, relations, List.of());

        assertEquals(1, impact.directDependencies());
        assertEquals(2, impact.transitiveDependencies());
        assertEquals(2, impact.affectedDependencies().size());
        assertEquals("dependency", impact.affectedDependencies().get(0).direction());
    }

    @Test
    void unresolvedTargetDoesNotBecomeImpact() {
        var entities = List.of(e("A","A"));
        var relations = List.of(
            new CodeRelation("A","unresolved-call:X.run()","calls-unresolved")
        );

        ChangeImpact impact = new ChangeImpactEngine().analyze("A", entities, relations, List.of());

        assertEquals(0, impact.directDependencies());
        assertEquals(0, impact.transitiveDependencies());
        assertTrue(impact.warnings().stream().anyMatch(w -> w.contains("unresolved")));
    }

    @Test
    void missingEntityIsExplicitErrorNotFakeImpact() {
        ChangeImpact impact = new ChangeImpactEngine().analyze("missing", List.of(), List.of(), List.of());
        assertEquals("missing", impact.sourceName());
        assertTrue(impact.warnings().stream().anyMatch(w -> w.contains("not found")));
    }
}
