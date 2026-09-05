package com.projectxray.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

public final class XRayToolWindowFactory implements ToolWindowFactory {
    @Override public void createToolWindowContent(Project project,ToolWindow toolWindow){
        JPanel root=new JPanel(new BorderLayout(8,8));root.setBorder(JBUI.Borders.empty(8));
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
        JButton analyze=new JButton("Analyze");JButton arch=new JButton("Architecture");JButton impact=new JButton("Impact");JButton health=new JButton("Health");JButton context=new JButton("Context");JButton live=new JButton("Live Impact");
        for(JButton b:new JButton[]{analyze,arch,impact,health,context,live})toolbar.add(b);root.add(toolbar,BorderLayout.NORTH);
        JLabel status=new JLabel("Project X-Ray • local-first • ready");status.setBorder(JBUI.Borders.emptyBottom(4));
        JPanel top=new JPanel(new BorderLayout());top.add(toolbar,BorderLayout.CENTER);top.add(status,BorderLayout.SOUTH);root.remove(toolbar);root.add(top,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();JBTextArea data=new JBTextArea();data.setEditable(false);data.setLineWrap(false);XRayGraphPanel graph=new XRayGraphPanel(project);
        tabs.addTab("Data",new JBScrollPane(data));tabs.addTab("Architecture",new JBScrollPane(graph));root.add(tabs,BorderLayout.CENTER);
        XRayProjectService service=project.getService(XRayProjectService.class);
        analyze.addActionListener(e->{status.setText("Project X-Ray • analyzing in background…");data.setText("Running repository analysis…\n");service.analyze().thenAccept(r->ApplicationManager.getApplication().invokeLater(()->{data.setText(r.output());status.setText(r.exitCode()==0?"Project X-Ray • analysis complete":"Project X-Ray • analysis failed");if(r.exitCode()!=0)Messages.showErrorDialog(project,r.output(),"Project X-Ray");else graph.load(service.artifact("analysis.json"));}));});
        arch.addActionListener(e->{graph.load(service.artifact("analysis.json"));tabs.setSelectedIndex(1);status.setText("Project X-Ray • architecture view • real repository data");});
        impact.addActionListener(e->show(service,data,"invalidation-plan.json"));health.addActionListener(e->show(service,data,"analysis.json"));context.addActionListener(e->show(service,data,"compiler-context.json"));
        live.addActionListener(e->{XRayLiveChangeServiceHolder holder=project.getService(XRayLiveChangeServiceHolder.class);data.setText(holder.service().output());status.setText(holder.service().status());});
        toolWindow.getContentManager().addContent(ContentFactory.getInstance().createContent(root,"X-Ray",false));
    }
    private static void show(XRayProjectService service,JBTextArea out,String file){Path p=service.artifact(file);try{out.setText(p!=null&&Files.isRegularFile(p)?Files.readString(p):"Artifact not available. Run Analyze Project first.");out.setCaretPosition(0);}catch(Exception e){out.setText("Unable to read "+file+": "+e.getMessage());}}
}
