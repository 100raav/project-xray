package com.projectxray.core;

import com.projectxray.core.index.PersistentSymbolStore;
import com.projectxray.core.model.CodeEntity;
import com.projectxray.core.model.CodeRelation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistentSymbolStoreTest {
    @Test
    void buildsQualifiedSimpleAndAdjacencyIndexes() {
        var a=new CodeEntity("type:a","class","PaymentService",
            "com.demo.PaymentService","src/PaymentService.java",1,"Java","service");
        var b=new CodeEntity("type:b","class","OrderService",
            "com.demo.OrderService","src/OrderService.java",1,"Java","service");
        var r=new CodeRelation(a.id(),b.id(),"uses",
            "src/PaymentService.java",4,"orderService");
        var result=new com.projectxray.core.model.AnalysisResult(
            "demo","/repo","Java",1,2,List.of(a,b),List.of(r),List.of(),List.of(),
            new com.projectxray.core.model.BuildInfo("unknown","","","",List.of(),List.of(),List.of()),
            com.projectxray.core.model.GitInfo.none(),List.of());

        var store=PersistentSymbolStore.build(Path.of("/repo"),result);
        var api=new PersistentSymbolStore();

        assertEquals("type:a",api.byQualifiedName(store).get("com.demo.PaymentService").id());
        assertEquals(1,api.bySimpleName(store).get("PaymentService").size());
        assertEquals(1,api.outgoing(store).get("type:a").size());
        assertEquals(1,api.incoming(store).get("type:b").size());
    }
}
