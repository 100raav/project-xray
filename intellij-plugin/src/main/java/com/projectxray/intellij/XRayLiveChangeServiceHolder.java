package com.projectxray.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.PROJECT)
public final class XRayLiveChangeServiceHolder implements Disposable {
    private final XRayLiveChangeService service;
    public XRayLiveChangeServiceHolder(Project project){service=new XRayLiveChangeService(project);}
    public XRayLiveChangeService service(){return service;}
    @Override public void dispose(){service.dispose();}
}
