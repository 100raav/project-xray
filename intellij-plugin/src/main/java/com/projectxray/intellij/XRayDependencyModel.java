package com.projectxray.intellij;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.project.Project;
import java.nio.file.*;
import java.util.*;

public final class XRayDependencyModel {
    private final Project project; private final ObjectMapper mapper=new ObjectMapper();
    public XRayDependencyModel(Project project){this.project=project;}
    public List<String> linesFor(String id){
        JsonNode doc=read();if(doc==null)return List.of("No persistent X-Ray model. Run Analyze Project first.");
        Map<String,String> names=new HashMap<>();for(JsonNode s:doc.path("symbols"))names.put(s.path("id").asText(),s.path("qualifiedName").asText(s.path("name").asText()));
        List<String> out=new ArrayList<>();out.add("DIRECT DEPENDENCIES");
        for(JsonNode r:doc.path("relations"))if(id.equals(r.path("sourceId").asText()))out.add("→ "+names.getOrDefault(r.path("targetId").asText(),r.path("targetId").asText())+"  ["+r.path("type").asText()+"]");
        out.add("DIRECT DEPENDENTS");
        for(JsonNode r:doc.path("relations"))if(id.equals(r.path("targetId").asText()))out.add("← "+names.getOrDefault(r.path("sourceId").asText(),r.path("sourceId").asText())+"  ["+r.path("type").asText()+"]");
        if(out.size()==2)return List.of("No persisted relationships found for this symbol.");return out;
    }
    private JsonNode read(){Path p=project.getService(XRayProjectService.class).artifact("symbol-store.json");try{return p!=null?mapper.readTree(Files.readString(p)):null;}catch(Exception e){return null;}}
}
