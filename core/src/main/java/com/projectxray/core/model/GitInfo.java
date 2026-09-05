package com.projectxray.core.model;
import java.util.List;
public record GitInfo(boolean repository, String branch, String commit, boolean dirty, List<String> changedFiles, String remote) {
    public static GitInfo none(){ return new GitInfo(false,"","",false,List.of(),""); }
}
