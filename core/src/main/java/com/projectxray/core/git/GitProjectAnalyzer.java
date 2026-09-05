package com.projectxray.core.git;

import com.projectxray.core.model.GitInfo;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Reads only real Git metadata from the supplied repository. */
public final class GitProjectAnalyzer {
    public GitInfo analyze(Path root){
        if(!Files.isDirectory(root.resolve(".git")))return GitInfo.none();
        try{
            String branch=run(root,"git","branch","--show-current").trim();
            String commit=run(root,"git","rev-parse","HEAD").trim();
            String status=run(root,"git","status","--porcelain");
            List<String> changed=status.lines().filter(s->!s.isBlank()).map(s->s.length()>3?s.substring(3):s).toList();
            String remote=run(root,"git","config","--get","remote.origin.url").trim();
            return new GitInfo(true,branch,commit,!status.isBlank(),changed,remote);
        }catch(Exception e){return new GitInfo(true,"","",false,List.of(),"");}
    }
    private static String run(Path cwd,String... cmd)throws Exception{Process p=new ProcessBuilder(cmd).directory(cwd.toFile()).redirectErrorStream(true).start();String out=new String(p.getInputStream().readAllBytes());int code=p.waitFor();if(code!=0)throw new IOException(out);return out;}
}
