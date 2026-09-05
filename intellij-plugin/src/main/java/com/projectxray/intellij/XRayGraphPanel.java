package com.projectxray.intellij;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class XRayGraphPanel extends JPanel {
    private final ObjectMapper mapper=new ObjectMapper();
    private final Project project;
    private JsonNode graph;
    private final Map<String,Point> points=new LinkedHashMap<>();
    private final Map<String,JsonNode> nodeById=new LinkedHashMap<>();

    XRayGraphPanel(Project project){
        this.project=project;setBackground(new Color(12,15,22));setPreferredSize(new Dimension(900,650));
        addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(e.getClickCount()!=2) return; openNearest(e.getPoint());}});
        setToolTipText("");
    }
    void load(Path analysis){
        try{JsonNode root=mapper.readTree(Files.readString(analysis));graph=root.path("architectureGraph");repaint();}
        catch(Exception e){graph=null;repaint();}
    }
    @Override public String getToolTipText(MouseEvent e){String id=nearest(e.getPoint());if(id==null)return null;JsonNode n=nodeById.get(id);return n==null?null:n.path("name").asText()+" • double-click to open source";}
    private void openNearest(Point p){
        String id=nearest(p);
        if(id==null)return;

        JsonNode n=nodeById.get(id);
        if(n==null)return;

        String path=n.path("path").asText();
        if(path.isBlank())return;

        Path root=project.getService(XRayProjectService.class).root();
        if(root==null)return;

        Path target=Path.of(path);
        if(!target.isAbsolute())target=root.resolve(path);

        target=target.normalize();

        VirtualFile vf=LocalFileSystem.getInstance().findFileByNioFile(target);
        if(vf==null)vf=LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target);

        if(vf==null)return;

        int line=Math.max(0,n.path("line").asInt()-1);
        new OpenFileDescriptor(project,vf,line,0).navigate(true);
    }
    private String nearest(Point p){String best=null;double d=30*30;for(var e:points.entrySet()){double dx=e.getValue().x-p.x,dy=e.getValue().y-p.y,dd=dx*dx+dy*dy;if(dd<d){d=dd;best=e.getKey();}}return best;}
    @Override protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D x=(Graphics2D)g.create();x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);points.clear();nodeById.clear();
        if(graph==null||!graph.has("nodes")){x.setColor(Color.LIGHT_GRAY);x.drawString("Run Analyze to generate the repository-derived architecture graph.",24,30);x.dispose();return;}
        JsonNode nodes=graph.path("nodes"),edges=graph.path("edges");int limit=XRaySettings.get().maxGraphNodes();int n=Math.min(nodes.size(),limit);int cx=getWidth()/2,cy=getHeight()/2,radius=Math.max(120,Math.min(getWidth(),getHeight())/2-80);
        for(int i=0;i<n;i++){JsonNode node=nodes.get(i);String id=node.path("id").asText();double a=(Math.PI*2*i)/Math.max(1,n);points.put(id,new Point(cx+(int)(radius*Math.cos(a)),cy+(int)(radius*Math.sin(a))));nodeById.put(id,node);}
        for(JsonNode edge:edges){Point a=points.get(edge.path("sourceId").asText()),b=points.get(edge.path("targetId").asText());if(a==null||b==null)continue;x.setColor(new Color(95,105,125,120));x.drawLine(a.x,a.y,b.x,b.y);}
        for(JsonNode node:nodes){Point p=points.get(node.path("id").asText());if(p==null)continue;int size=16+Math.min(30,node.path("entityCount").asInt()/2);x.setColor(new Color(82,170,255,210));x.fill(new Ellipse2D.Double(p.x-size/2.0,p.y-size/2.0,size,size));x.setColor(Color.WHITE);String name=node.path("name").asText(node.path("id").asText());x.drawString(name,p.x+size/2+5,p.y+4);}
        x.setColor(new Color(190,200,220));String suffix=nodes.size()>limit?" • rendering cap "+limit+" of "+nodes.size():"";x.drawString("Repository architecture • double-click a node to open source"+suffix,18,22);x.dispose();}
}
