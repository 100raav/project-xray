package com.projectxray.core;

import com.projectxray.core.index.PersistentMethodResolver;
import com.projectxray.core.index.PersistentSymbolStore;
import com.projectxray.core.model.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PersistentMethodResolverTest {
    @Test void resolvesExactOverloadByParameterTypes() {
        var type=new CodeEntity("type:A","class","A","p.A","A.java",1,"Java",null);
        var one=new CodeEntity("type:A#method:run(java.lang.String)","method","run","p.A#run(java.lang.String)","A.java",2,"Java",null);
        var two=new CodeEntity("type:A#method:run(int)","method","run","p.A#run(int)","A.java",3,"Java",null);
        var result=new AnalysisResult("demo","/repo","Java",1,1,List.of(type,one,two),List.of(),List.of(),List.of(),
            new BuildInfo("unknown","","","",List.of(),List.of(),List.of()),GitInfo.none(),List.of(new FileSnapshot("A.java",3,1,"x")));
        var doc=PersistentSymbolStore.build(Path.of("/repo"),result);
        var resolver=new PersistentMethodResolver();
        var r=resolver.resolve(doc,"p.A","run",List.of("int"));
        assertTrue(r.resolved()); assertEquals(two.id(),r.method().id());
    }

    @Test void refusesAmbiguousArityOnlyResolution() {
        var type=new CodeEntity("type:A","class","A","p.A","A.java",1,"Java",null);
        var one=new CodeEntity("type:A#method:run(java.lang.String)","method","run","p.A#run(java.lang.String)","A.java",2,"Java",null);
        var two=new CodeEntity("type:A#method:run(java.lang.Integer)","method","run","p.A#run(java.lang.Integer)","A.java",3,"Java",null);
        var result=new AnalysisResult("demo","/repo","Java",1,1,List.of(type,one,two),List.of(),List.of(),List.of(),
            new BuildInfo("unknown","","","",List.of(),List.of(),List.of()),GitInfo.none(),List.of(new FileSnapshot("A.java",3,1,"x")));
        var doc=PersistentSymbolStore.build(Path.of("/repo"),result);
        var r=new PersistentMethodResolver().resolve(doc,"p.A","run",1);
        assertFalse(r.resolved()); assertEquals("ambiguous-overload",r.strategy());
    }
}
