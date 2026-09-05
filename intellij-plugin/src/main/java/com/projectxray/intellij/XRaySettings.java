package com.projectxray.intellij;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

@Service(Service.Level.APP)
@State(name = "ProjectXRaySettings", storages = @Storage("project-xray.xml"))
public final class XRaySettings implements PersistentStateComponent<XRaySettings.State> {
    public static final class State {
        public String cliJar = "";
        public int debounceMs = 900;
        public int maxGraphNodes = 5000;
    }
    private State state = new State();
    public static XRaySettings get() { return com.intellij.openapi.application.ApplicationManager.getApplication().getService(XRaySettings.class); }
    @Override public State getState() { return state; }
    @Override public void loadState(State state) { this.state = state == null ? new State() : state; }
    public String cliJar() { return state.cliJar == null ? "" : state.cliJar.trim(); }
    public int debounceMs() { return Math.max(250, Math.min(5000, state.debounceMs)); }
    public int maxGraphNodes() { return Math.max(100, Math.min(100000, state.maxGraphNodes)); }
}
