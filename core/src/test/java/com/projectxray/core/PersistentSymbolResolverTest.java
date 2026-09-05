package com.projectxray.core;

import com.projectxray.core.index.PersistentSymbolResolver;
import com.projectxray.core.index.PersistentSymbolStore;
import com.projectxray.core.model.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistentSymbolResolverTest {
    private PersistentSymbolStore.StoreDocument doc() {
        var a=new CodeEntity("type:a","class","A","p.A","A.java",1,"Java","service");
        var b=new CodeEntity("type:b","class","B","p.B","B.java",1,"Java","service");
        var c=new CodeEntity("type:c","class","C","p.C","C.java",1,"Java","service");
        var r1=new CodeRelation("type:a","type:b","uses","A.java",2,"b");
        var r2=new CodeRelation("type:b","type:c","uses","B.java",2,"c");
        var result=new AnalysisResult("demo","/repo","Java",1,3,List.of(a,b,c),List.of(r1,r2),List.of(),List.of(),
            new BuildInfo("unknown","","","",List.of(),List.of(),List.of()),GitInfo.none(),
            List.of(new FileSnapshot("A.java",1,1,"a"),new FileSnapshot("B.java",1,1,"b"),new FileSnapshot("C.java",1,1,"c")));
        return PersistentSymbolStore.build(Path.of("/repo"),result);
    }

    @Test void resolvesQualifiedName() {
        var r=new PersistentSymbolResolver().resolve(doc(),"p.B");
        assertTrue(r.resolved());
        assertEquals("type:b",r.symbol().id());
        assertEquals("qualified-name",r.strategy());
    }

    @Test void resolvesUnambiguousSimpleName() {
        var r=new PersistentSymbolResolver().resolve(doc(),"C");
        assertTrue(r.resolved());
        assertEquals("type:c",r.symbol().id());
    }

    @Test void rejectsUnknown() {
        assertFalse(new PersistentSymbolResolver().resolve(doc(),"Missing").resolved());
    }

    @Test void followsDependenciesAndDependents() {
        var d=doc();
        var api=new PersistentSymbolResolver();
        assertEquals(2,api.dependencies(d,"type:a",5).size());
        assertEquals(2,api.dependents(d,"type:c",5).size());
    }
}
