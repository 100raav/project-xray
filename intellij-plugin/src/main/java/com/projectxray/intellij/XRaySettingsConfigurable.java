package com.projectxray.intellij;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class XRaySettingsConfigurable implements Configurable {
    private JPanel panel;
    private JBTextField cliJar;
    private JBTextField debounce;
    private JBTextField maxNodes;

    @Override public String getDisplayName() { return "Project X-Ray"; }

    @Override public @Nullable JComponent createComponent() {
        cliJar = new JBTextField();
        debounce = new JBTextField();
        maxNodes = new JBTextField();
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(new JBLabel("X-Ray core JAR:"), cliJar)
            .addLabeledComponent(new JBLabel("Live analysis debounce (ms):"), debounce)
            .addLabeledComponent(new JBLabel("Maximum graph nodes rendered:"), maxNodes)
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();
        reset();
        return panel;
    }

    @Override public boolean isModified() {
        XRaySettings.State s = XRaySettings.get().getState();
        return !safe(cliJar.getText()).equals(safe(s.cliJar)) || parse(debounce, s.debounceMs) != s.debounceMs ||
            parse(maxNodes, s.maxGraphNodes) != s.maxGraphNodes;
    }

    @Override public void apply() throws com.intellij.openapi.options.ConfigurationException {
        XRaySettings.State s = XRaySettings.get().getState();
        s.cliJar = safe(cliJar.getText());
        s.debounceMs = clamp(parse(debounce, 900), 250, 5000);
        s.maxGraphNodes = clamp(parse(maxNodes, 5000), 100, 100000);
    }

    @Override public void reset() {
        XRaySettings.State s = XRaySettings.get().getState();
        if (cliJar != null) cliJar.setText(safe(s.cliJar));
        if (debounce != null) debounce.setText(Integer.toString(s.debounceMs));
        if (maxNodes != null) maxNodes.setText(Integer.toString(s.maxGraphNodes));
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
    private static int parse(JTextField f, int fallback) { try { return Integer.parseInt(f.getText().trim()); } catch (Exception e) { return fallback; } }
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
}
