package com.projectxray.intellij;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service(Service.Level.PROJECT)
public final class XRayProjectService {
    private final Project project;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean analysisRunning = new AtomicBoolean(false);
    private volatile JsonNode analysisCache;
    private volatile long analysisMtime = -1;

    public XRayProjectService(Project project) { this.project = project; }
    public Path root() { String base=project.getBasePath(); return base==null?null:Paths.get(base).toAbsolutePath().normalize(); }
    public Path xrayDir(){Path r=root();return r==null?null:r.resolve(".xray");}
    public Path artifact(String name){Path d=xrayDir();return d==null?null:d.resolve(name);}
    public boolean isRunning(){return analysisRunning.get();}

    public CompletableFuture<ProcessResult> analyze(){
        if(!analysisRunning.compareAndSet(false,true)) return CompletableFuture.completedFuture(new ProcessResult(10,"Project X-Ray analysis is already running."));
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path r=root(); if(r==null)return new ProcessResult(2,"No project root is open.");
                String cli=cliJar(); if(cli.isBlank())return new ProcessResult(3,"Configure the Project X-Ray core JAR in Settings → Tools → Project X-Ray.");
                Process p=new ProcessBuilder("java","-jar",cli,r.toString(),artifact("analysis.json").toString()).directory(r.toFile()).redirectErrorStream(true).start();
                String output=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8); int code=p.waitFor();
                refresh();invalidateCache();return new ProcessResult(code,output);
            } catch(Exception e){return new ProcessResult(4,message(e));}
            finally{analysisRunning.set(false);}
        },AppExecutorUtil.getAppExecutorService());
    }

    public CompletableFuture<ProcessResult> runCore(String... args){
        return CompletableFuture.supplyAsync(() -> {
            try{
                Path r=root();if(r==null)return new ProcessResult(2,"No project root is open.");
                String cli=cliJar();if(cli.isBlank())return new ProcessResult(3,"Configure the Project X-Ray core JAR in Settings → Tools → Project X-Ray.");
                List<String> cmd=new ArrayList<>(List.of("java","-jar",cli,r.toString()));Collections.addAll(cmd,args);
                Process p=new ProcessBuilder(cmd).directory(r.toFile()).redirectErrorStream(true).start();
                String out=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);int code=p.waitFor();return new ProcessResult(code,out);
            }catch(Exception e){return new ProcessResult(4,message(e));}
        },AppExecutorUtil.getAppExecutorService());
    }

    public CompletableFuture<ProcessResult> impactForFile(String relativePath){return runCore("--file-impact",relativePath==null?"":relativePath);}

    public JsonNode analysisJson(){
        Path p=artifact("analysis.json");if(p==null||!Files.isRegularFile(p))return null;
        try{long m=Files.getLastModifiedTime(p).toMillis();if(analysisCache==null||m!=analysisMtime){analysisCache=mapper.readTree(Files.readString(p));analysisMtime=m;}return analysisCache;}catch(Exception e){return null;}
    }
    public String artifactText(String name){Path p=artifact(name);try{return p!=null&&Files.isRegularFile(p)?Files.readString(p):"";}catch(IOException e){return "";}}
    public void invalidateCache(){analysisCache=null;analysisMtime=-1;}
    public void refresh(){Path r=root();if(r==null)return;VirtualFile f=LocalFileSystem.getInstance().refreshAndFindFileByNioFile(r);if(f!=null)f.refresh(false,true);}
    private static String cliJar(){String configured=XRaySettings.get().cliJar();if(!configured.isBlank())return configured;String env=System.getenv("PROJECT_XRAY_CLI");return env==null?"":env.trim();}
    private static String message(Exception e){return e.getMessage()==null?e.toString():e.getMessage();}
    public record ProcessResult(int exitCode,String output){}
}
