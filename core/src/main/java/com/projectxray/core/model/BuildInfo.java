package com.projectxray.core.model;
import java.util.List;
public record BuildInfo(String system, String group, String artifact, String version, List<String> modules, List<String> dependencies, List<String> warnings) {
    public static BuildInfo unknown(){ return new BuildInfo("unknown","","","",List.of(),List.of(),List.of()); }
}
