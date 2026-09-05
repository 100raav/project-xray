package com.projectxray.intellij;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public final class XRayAnalyzeAction extends AnAction {
    @Override public void actionPerformed(AnActionEvent event) {
        Project project=event.getProject();
        if(project==null)return;
        project.getService(XRayProjectService.class).analyze().thenAccept(result ->
            Messages.showInfoMessage(project,
                "Exit code: "+result.exitCode()+"\n"+result.output(),
                "Project X-Ray Analysis"));
    }
}
