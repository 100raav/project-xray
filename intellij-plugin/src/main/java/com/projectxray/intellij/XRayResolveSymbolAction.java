package com.projectxray.intellij;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public final class XRayResolveSymbolAction extends AnAction {
    @Override public ActionUpdateThread getActionUpdateThread(){return ActionUpdateThread.BGT;}
    @Override public void update(AnActionEvent e){e.getPresentation().setEnabled(e.getProject()!=null&&e.getData(CommonDataKeys.EDITOR)!=null);}
    @Override public void actionPerformed(AnActionEvent e){
        Project project=e.getProject();Editor editor=e.getData(CommonDataKeys.EDITOR);if(project==null||editor==null)return;
        XRayPsiBridge.BridgeResult r=XRayPsiBridge.atCaret(project,editor);
        if(r==null){Messages.showInfoMessage(project,"No Java symbol at the caret matched the persisted X-Ray model. Run Analyze Project first.","Project X-Ray");return;}
        Messages.showInfoMessage(project,"X-Ray ID: "+r.xrayId()+"\nQualified: "+r.qualifiedName()+"\nFile: "+r.file()+":"+(r.line()+1)+"\nBridge: "+r.strategy(),"Project X-Ray Symbol Bridge");
    }
}
