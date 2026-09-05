package com.projectxray.intellij;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.EditorFactory;


import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Debounced, single-flight live impact service. It never runs heavyweight analysis on the EDT. */
public final class XRayLiveChangeService implements Disposable {
    private final Project project;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r -> { Thread t=new Thread(r,"Project-X-Ray-Live-Impact"); t.setDaemon(true); return t; });
    private final AtomicReference<ScheduledFuture<?>> pending=new AtomicReference<>();
    private volatile String lastStatus="Live impact: idle";
    private volatile String lastOutput="";

    public XRayLiveChangeService(Project project){
        this.project=project;
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener(){
            @Override public void documentChanged(DocumentEvent event){schedule(event.getDocument());}
        },this);
    }
    public String status(){return lastStatus;}
    public String output(){return lastOutput;}

    private void schedule(Document document){
        VirtualFile file=FileDocumentManager.getInstance().getFile(document);if(file==null)return;
        Path root=project.getService(XRayProjectService.class).root();if(root==null)return;
        if(!"java".equalsIgnoreCase(file.getExtension()))return;
        Path p=Path.of(file.getPath()).toAbsolutePath().normalize();if(!p.startsWith(root))return;
        ScheduledFuture<?> old=pending.getAndSet(scheduler.schedule(()->run(file),XRaySettings.get().debounceMs(),TimeUnit.MILLISECONDS));
        if(old!=null)old.cancel(false);
        lastStatus="Live impact: change detected • waiting for quiet period";
    }
    private void run(VirtualFile file){
        Path root=project.getService(XRayProjectService.class).root();
        if(root==null)return;
        String rel=root.relativize(Path.of(file.getPath()).toAbsolutePath().normalize()).toString();
        boolean unsaved=FileDocumentManager.getInstance().isFileModified(file);
        lastStatus="Live impact: analyzing "+rel+(unsaved?" • current editor is unsaved; using last saved X-Ray graph":"");
        project.getService(XRayProjectService.class).impactForFile(rel).thenAccept(result->{
            lastOutput=result.output();lastStatus=result.exitCode()==0?"Live impact: updated for "+rel:"Live impact: failed — "+result.output();
        });
    }
    @Override public void dispose(){ScheduledFuture<?> f=pending.getAndSet(null);if(f!=null)f.cancel(false);scheduler.shutdownNow();}
}
