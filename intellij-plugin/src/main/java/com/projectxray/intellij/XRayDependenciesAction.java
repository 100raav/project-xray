package com.projectxray.intellij;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.components.JBList;
import javax.swing.*;
import java.util.*;

public final class XRayDependenciesAction extends AnAction {
    @Override public ActionUpdateThread getActionUpdateThread(){return ActionUpdateThread.BGT;}
    @Override public void update(AnActionEvent e){e.getPresentation().setEnabled(e.getProject()!=null&&e.getData(CommonDataKeys.EDITOR)!=null);}
    @Override public void actionPerformed(AnActionEvent e){
        Project project=e.getProject();Editor editor=e.getData(CommonDataKeys.EDITOR);if(project==null||editor==null)return;
        XRayPsiBridge.BridgeResult bridge=XRayPsiBridge.atCaret(project,editor);if(bridge!=null)show(project,editor,bridge);
    }
    static void show(Project project,Editor editor,XRayPsiBridge.BridgeResult bridge){
        java.util.List<String> lines=new XRayDependencyModel(project).linesFor(bridge.xrayId());
        JBPopupFactory.getInstance()
                .createPopupChooserBuilder(lines)
                .setTitle("X-Ray Dependencies • " + bridge.qualifiedName())
                .setResizable(true)
                .setMovable(true)
                .createPopup()
                .showInBestPositionFor(editor);
    }
}
