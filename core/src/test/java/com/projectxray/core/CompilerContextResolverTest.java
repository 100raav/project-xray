package com.projectxray.core;

import com.projectxray.core.index.CompilerContextResolver;
import com.projectxray.core.index.PersistentSymbolStore;
import com.projectxray.core.model.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompilerContextResolverTest {
    private PersistentSymbolStore.StoreDocument doc() {
        var a=new CodeEntity("type:a","class","PaymentService","com.demo.PaymentService",
            "PaymentService.java",1,"Java","service");
        var result=new AnalysisResult("demo","/repo","Java",1,1,List.of(a),List.of(),List.of(),List.of(),
            new BuildInfo("unknown","","","",List.of(),List.of(),List.of()),GitInfo.none(),
            List.of(new FileSnapshot("PaymentService.java",1,1,"x")));
        return PersistentSymbolStore.build(Path.of("/repo"),result);
    }

    @Test void resolvesSamePackageType() {
        var r=new CompilerContextResolver().resolveType(doc(),"PaymentService","com.demo",List.of());
        assertTrue(r.resolved());
        assertEquals("same-package symbol",r.reason());
    }

    @Test void classifiesJdkTypeAsExternal() {
        var r=new CompilerContextResolver().resolveType(doc(),"String","com.demo",List.of());
        assertFalse(r.resolved());
        assertEquals("external",r.category());
    }

    @Test void reportsUnknownProjectType() {
        var r=new CompilerContextResolver().resolveType(doc(),"MissingType","com.demo",List.of());
        assertFalse(r.resolved());
        assertEquals("unresolved",r.category());
    }
}
