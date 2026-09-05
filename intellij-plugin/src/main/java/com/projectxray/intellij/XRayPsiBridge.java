package com.projectxray.intellij;

import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.nio.file.Path;
import java.util.*;

/** Native PSI ↔ X-Ray bridge. PSI supplies authoritative IDE symbol identity; X-Ray supplies cross-IDE persisted relationships. */
public final class XRayPsiBridge {
    public record BridgeResult(String xrayId, String qualifiedName, String file, int line, PsiElement psi, String strategy) {}

    public static BridgeResult atCaret(Project project, com.intellij.openapi.editor.Editor editor) {
        if(editor==null)return null;
        PsiFile file=PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if(file==null)return null;
        PsiElement element=file.findElementAt(editor.getCaretModel().getOffset());
        return fromElement(project,element);
    }

    public static BridgeResult fromElement(Project project,PsiElement element){
        if(element==null)return null;
        PsiMethod method=PsiTreeUtil.getParentOfType(element,PsiMethod.class,false);
        if(method!=null && method.getContainingClass()!=null){
            String owner=method.getContainingClass().getQualifiedName();
            String q=owner==null?null:owner+"#"+methodSignature(method);
            BridgeResult exact=lookup(project,q,method.getContainingFile(),method,"psi-exact-method");
            if(exact!=null)return exact;
        }
        PsiClass clazz=PsiTreeUtil.getParentOfType(element,PsiClass.class,false);
        if(clazz!=null){
            String q=clazz.getQualifiedName();
            BridgeResult r=lookup(project,q,clazz.getContainingFile(),clazz,"psi-exact-type");
            if(r!=null)return r;
        }
        PsiNamedElement named=PsiTreeUtil.getParentOfType(element,PsiNamedElement.class,false);
        if(named!=null&&named.getName()!=null)return lookup(project,named.getName(),named.getContainingFile(),named,"psi-name");
        return null;
    }

    private static BridgeResult lookup(Project project,String query,PsiFile file,PsiElement psi,String strategy){
        JsonNode store=readStore(project);if(store==null)return null;
        for(JsonNode s:store.path("symbols")){
            if(query.equals(s.path("qualifiedName").asText()) || query.equals(s.path("id").asText()) ||
                (strategy.equals("psi-name")&&query.equals(s.path("name").asText()))){
                int line=Math.max(0,s.path("line").asInt()-1);
                return new BridgeResult(s.path("id").asText(),s.path("qualifiedName").asText(),s.path("file").asText(),line,psi,strategy);
            }
        }
        return null;
    }

    private static JsonNode readStore(Project project){
        Path p=project.getService(XRayProjectService.class).artifact("symbol-store.json");
        if(p==null)return null;try{return new com.fasterxml.jackson.databind.ObjectMapper().readTree(java.nio.file.Files.readString(p));}catch(Exception e){return null;}
    }

    public static String methodSignature(PsiMethod method){
        StringBuilder b=new StringBuilder(method.getName()).append('(');
        PsiParameter[] ps=method.getParameterList().getParameters();
        for(int i=0;i<ps.length;i++){
            if(i>0)b.append(',');
            PsiType type=ps[i].getType();
            b.append(type.getCanonicalText());
        }
        return b.append(')').toString();
    }
}
