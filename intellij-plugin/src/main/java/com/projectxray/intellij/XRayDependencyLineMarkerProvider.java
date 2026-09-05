package com.projectxray.intellij;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

public final class XRayDependencyLineMarkerProvider implements LineMarkerProvider {
    @Override public @Nullable LineMarkerInfo<?> getLineMarkerInfo(PsiElement element){
        if(!(element instanceof PsiIdentifier))return null;
        PsiMethod m=PsiTreeUtil.getParentOfType(element,PsiMethod.class,false);
        PsiClass c=PsiTreeUtil.getParentOfType(element,PsiClass.class,false);
        if(m==null&&c==null)return null;
        XRayPsiBridge.BridgeResult bridge=XRayPsiBridge.fromElement(element.getProject(),element);
        if(bridge==null)return null;
        return new LineMarkerInfo<>(element,element.getTextRange(),AllIcons.Actions.ShowAsTree,
            e->"Show X-Ray dependencies for "+bridge.qualifiedName(),
            (mouseEvent,psiElement)->{
                if(psiElement.getContainingFile()==null||psiElement.getContainingFile().getVirtualFile()==null)return;
                var doc=FileDocumentManager.getInstance().getDocument(psiElement.getContainingFile().getVirtualFile());
                if(doc==null)return;
                Editor[] editors=EditorFactory.getInstance().getEditors(doc,psiElement.getProject());
                if(editors.length>0) XRayDependenciesAction.show(psiElement.getProject(),editors[0],bridge);
            },
            com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.RIGHT,
            ()->"Project X-Ray dependencies");
    }
}
