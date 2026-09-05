package com.projectxray.core;

import com.projectxray.core.index.DependencyAwareInvalidationEngine;
import com.projectxray.core.index.PersistentCodeIndex;
import com.projectxray.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DependencyAwareInvalidationEngineTest {
    private CodeEntity e(String id,String file) {
        return new CodeEntity(id,"class",id,file,file,1,"Java",null);
    }

    @Test
    void reverseClosureFindsTransitiveDependents() {
        var a=e("A","A.java"); var b=e("B","B.java"); var c=e("C","C.java");
        var files=List.of(
            new PersistentCodeIndex.IndexedFile("A.java","a","a",1,1,List.of(a),
                List.of(new CodeRelation("A","B","uses","A.java",1,"A -> B")),List.of()),
            new PersistentCodeIndex.IndexedFile("B.java","b","b",1,1,List.of(b),
                List.of(new CodeRelation("B","C","uses","B.java",1,"B -> C")),List.of()),
            new PersistentCodeIndex.IndexedFile("C.java","c","c",1,1,List.of(c),List.of(),List.of())
        );
        var doc=new PersistentCodeIndex.IndexDocument("1.2","now","/tmp","Java",files);

        // C changed: B depends on C, A depends on B => C, B, A.
        var plan=new DependencyAwareInvalidationEngine().plan(doc,Set.of("C.java"),Set.of(),Set.of());
        assertTrue(plan.filesToReanalyze().containsAll(Set.of("A.java","B.java","C.java")));
        assertEquals(2,plan.traversedRelationships());
    }
}
