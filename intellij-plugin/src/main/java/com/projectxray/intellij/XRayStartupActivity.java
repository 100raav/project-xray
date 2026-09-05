package com.projectxray.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;

public final class XRayStartupActivity implements StartupActivity {
    @Override public void runActivity(Project project){ project.getService(XRayLiveChangeServiceHolder.class); }
}
