package com.projectxray.core.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectxray.core.model.AnalysisResult;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class CompilerContextReport {
    public record Entry(String symbolId, String file, int line, String kind,
                        String qualifiedName, String status, String details) {}
    public record Document(String schemaVersion, String generatedAt, String rootPath,
                           int projectSymbols, int projectRelations, List<Entry> entries) {}

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public Document build(Path root, AnalysisResult analysis) {
        List<Entry> entries=new ArrayList<>();
        for (var e:analysis.entities()) {
            entries.add(new Entry(e.id(),e.file(),e.line(),e.kind(),e.qualifiedName(),
                "indexed","persisted project symbol"));
        }
        for (var r:analysis.relations()) {
            if (r.targetId()!=null && r.targetId().startsWith("unresolved-call:")) {
                entries.add(new Entry(r.sourceId(),r.evidenceFile(),r.evidenceLine(),
                    "unresolved-call",r.targetId(),"unresolved",r.evidence()));
            }
        }
        return new Document("1.0",Instant.now().toString(),
            root.toAbsolutePath().normalize().toString(),analysis.entities().size(),
            analysis.relations().size(),List.copyOf(entries));
    }

    public void save(Path path, Document d) throws IOException {
        Files.createDirectories(path.toAbsolutePath().normalize().getParent());
        Path tmp=path.resolveSibling(path.getFileName()+".tmp");
        Files.writeString(tmp,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(d));
        try{Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
}
