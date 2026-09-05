package com.projectxray.core.project;

import com.projectxray.core.model.BuildInfo;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Detects build metadata from the real repository. It never invents dependencies. */
public final class BuildProjectAnalyzer {
    public BuildInfo analyze(Path root) {
        try {
            Path pom=root.resolve("pom.xml");
            if(Files.isRegularFile(pom)) return analyzeMaven(pom);
            Path g=root.resolve("build.gradle"), k=root.resolve("build.gradle.kts");
            if(Files.isRegularFile(g)||Files.isRegularFile(k)) return analyzeGradle(root,Files.isRegularFile(g)?g:k);
            return BuildInfo.unknown();
        } catch(Exception e){ return new BuildInfo("unknown","","","",List.of(),List.of(),List.of("Build metadata error: "+safe(e))); }
    }
    private BuildInfo analyzeMaven(Path pom)throws Exception{
        Document d=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile()); d.getDocumentElement().normalize();
        String group=first(d,"groupId"), artifact=first(d,"artifactId"), version=first(d,"version");
        List<String> modules=childrenText(d,"modules","module"), deps=new ArrayList<>();
        var nodes=d.getElementsByTagName("dependency");
        for(int i=0;i<nodes.getLength();i++){var n=nodes.item(i);String g=child(n,"groupId"),a=child(n,"artifactId"),v=child(n,"version");if(!g.isBlank()&&!a.isBlank())deps.add(g+":"+a+(v.isBlank()?"":":"+v));}
        return new BuildInfo("maven",group,artifact,version,List.copyOf(modules),List.copyOf(new LinkedHashSet<>(deps)),List.of());
    }
    private BuildInfo analyzeGradle(Path root,Path file)throws IOException{
        String text=Files.readString(file); String group=match(text,"(?m)^\\s*group\\s*=\\s*[\\\"']([^\\\"']+)"); String version=match(text,"(?m)^\\s*version\\s*=\\s*[\\\"']([^\\\"']+)");
        List<String> deps=new ArrayList<>(); Matcher m=Pattern.compile("(?:implementation|api|compileOnly|runtimeOnly|testImplementation)\\s*[\\(]?[\\\"']([^\\\"']+)[\\\"']").matcher(text); while(m.find())deps.add(m.group(1));
        List<String> modules=List.of(); Path settings=Files.exists(root.resolve("settings.gradle.kts"))?root.resolve("settings.gradle.kts"):root.resolve("settings.gradle");
        if(Files.isRegularFile(settings)){String s=Files.readString(settings);Matcher inc=Pattern.compile("include\\s*\\(?([^\\n]+)").matcher(s);if(inc.find()){List<String> ms=new ArrayList<>();for(String t:inc.group(1).split(",")){String x=t.trim().replaceAll("[\\\"']","");if(!x.isBlank())ms.add(x.replace(":","/"));}modules=ms;}}
        return new BuildInfo("gradle",group,"",version,List.copyOf(modules),List.copyOf(new LinkedHashSet<>(deps)),List.of());
    }
    private static String first(Document d,String tag){var n=d.getElementsByTagName(tag);return n.getLength()==0?"":n.item(0).getTextContent().trim();}
    private static List<String> childrenText(Document d,String parent,String child){var ps=d.getElementsByTagName(parent);if(ps.getLength()==0)return List.of();var p=ps.item(0);List<String> out=new ArrayList<>();var ns=p.getChildNodes();for(int i=0;i<ns.getLength();i++)if(ns.item(i).getNodeName().equals(child))out.add(ns.item(i).getTextContent().trim());return out;}
    private static String child(org.w3c.dom.Node n,String tag){var ns=n.getChildNodes();for(int i=0;i<ns.getLength();i++)if(ns.item(i).getNodeName().equals(tag))return ns.item(i).getTextContent().trim();return "";}
    private static String match(String s,String r){Matcher m=Pattern.compile(r).matcher(s);return m.find()?m.group(1):"";}
    private static String safe(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
}
