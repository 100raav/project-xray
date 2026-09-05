package com.projectxray.core.health;

import com.projectxray.core.model.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Conservative, explainable static health analysis.
 *
 * Every metric is computed from repository analysis evidence already present
 * in Project X-Ray. No guessed coverage, runtime telemetry, or synthetic
 * health values are introduced.
 */
public final class CodeHealthRadarEngine {

    public CodeHealthRadar analyze(AnalysisResult a) {
        List<HealthMetric> metrics = new ArrayList<>();
        List<HealthFinding> findings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        int entityCount = a.entities().size();
        int relationCount = a.relations().size();
        int classCount = (int) a.entities().stream().filter(e -> isType(e)).count();

        // Architecture cycles
        int cycles = a.architectureGraph().cycles().size();
        double cycleRate = classCount == 0 ? 0 : (cycles * 100.0 / classCount);
        metrics.add(new HealthMetric("architecture-cycles","Architecture cycles","Architecture",
            cycles,"cycles",cycles==0?"healthy":"risk",
            cycles==0?"No cycles were detected in the architecture graph."
                :"Cycles were detected among analyzed architecture nodes.",
            "architectureGraph.cycles"));

        if (cycles > 0) {
            for (List<String> cycle : a.architectureGraph().cycles().stream().limit(20).toList()) {
                findings.add(new HealthFinding("high","Architecture","Circular dependency detected",
                    cycle.isEmpty()?"":cycle.get(0),"",-1,
                    "A cycle was detected in the repository-derived architecture graph.",
                    String.join(" -> ", cycle)));
            }
        }

        // Coupling: mean unique outgoing dependency targets per type.
        Map<String, Set<String>> outgoing = new HashMap<>();
        Map<String, Set<String>> incoming = new HashMap<>();
        Set<String> realIds = a.entities().stream().map(CodeEntity::id).collect(Collectors.toSet());
        for (CodeRelation r : a.relations()) {
            if (!realIds.contains(r.sourceId()) || !realIds.contains(r.targetId())) continue;
            outgoing.computeIfAbsent(r.sourceId(), k->new HashSet<>()).add(r.targetId());
            incoming.computeIfAbsent(r.targetId(), k->new HashSet<>()).add(r.sourceId());
        }
        List<CodeEntity> types = a.entities().stream().filter(this::isType).toList();
        double avgFanOut = types.stream().mapToInt(e->outgoing.getOrDefault(e.id(),Set.of()).size()).average().orElse(0);
        double avgFanIn = types.stream().mapToInt(e->incoming.getOrDefault(e.id(),Set.of()).size()).average().orElse(0);

        metrics.add(new HealthMetric("avg-fan-out","Average fan-out","Coupling",round(avgFanOut),
            "targets/type",status(avgFanOut,8,14),
            "Average number of distinct real relationship targets per analyzed type.",
            "resolved CodeRelation source/target pairs"));

        metrics.add(new HealthMetric("avg-fan-in","Average fan-in","Coupling",round(avgFanIn),
            "dependents/type",status(avgFanIn,15,30),
            "Average number of distinct real dependents per analyzed type.",
            "resolved CodeRelation source/target pairs"));

        // Relationship density
        double density = classCount < 2 ? 0 : relationCount * 100.0 / (classCount * (double)(classCount-1));
        metrics.add(new HealthMetric("relationship-density","Relationship density","Architecture",round(density),
            "%",status(density,20,50),
            "Resolved relationship count normalized by possible directed type pairs.",
            "entities + relations"));

        // File size / entity concentration
        Map<String,Integer> entityPerFile = a.entities().stream()
            .collect(Collectors.groupingBy(CodeEntity::file, Collectors.summingInt(e->1)));
        entityPerFile.entrySet().stream().filter(e->e.getValue()>=30).sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
            .limit(20).forEach(e -> findings.add(new HealthFinding(
                e.getValue()>=60?"high":"medium","Structure","High symbol concentration",
                "",""+e.getKey(),-1,
                "This source file contains many analyzed symbols; it may deserve decomposition review.",
                e.getValue()+" analyzed entities in one file."
            )));
        double maxEntitiesPerFile=entityPerFile.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        metrics.add(new HealthMetric("max-entities-per-file","Maximum entities in one file","Structure",maxEntitiesPerFile,
            "entities/file",status(maxEntitiesPerFile,30,60),
            "Maximum number of analyzed entities found in one source file.",
            "CodeEntity.file grouping"));

        // Methods per type and method size proxy (source line span is unavailable in current model).
        Map<String,Long> methodsByOwner = a.entities().stream()
            .filter(e->"method".equals(e.kind()))
            .collect(Collectors.groupingBy(e->ownerId(e.id()),Collectors.counting()));
        double avgMethods = types.stream().mapToLong(e->methodsByOwner.getOrDefault(e.id(),0L)).average().orElse(0);
        metrics.add(new HealthMetric("avg-methods-per-type","Average methods per type","Complexity",round(avgMethods),
            "methods/type",status(avgMethods,20,40),
            "Average number of analyzed methods owned by each analyzed type.",
            "CodeEntity kind=method"));

        // Git change concentration: available from Time Machine diff only; don't invent frequency.
        if (a.gitTimeMachine().repository()) {
            int changed=a.gitTimeMachine().currentDiff().changedFiles().size();
            metrics.add(new HealthMetric("head-change-surface","HEAD change surface","Change risk",changed,
                "files",status(changed,20,50),
                "Number of files changed between HEAD and its immediate parent.",
                "gitTimeMachine.currentDiff.changedFiles"));
        } else {
            warnings.add("Git history metrics unavailable because the analyzed project is not a Git repository.");
        }

        // Explicitly do not report test coverage: current model has no trustworthy coverage source.
        warnings.add("Test coverage is not scored: no coverage report is consumed by the current analyzer.");
        warnings.add("Runtime performance, production errors, and dynamic reflection are not scored by this static radar.");

        // Score: deductions are transparent and bounded.
        double score=100;
        score -= Math.min(35, cycles*8);
        score -= Math.min(25, Math.max(0, avgFanOut-8)*1.5);
        score -= Math.min(15, Math.max(0, avgFanIn-15)*0.5);
        score -= Math.min(15, Math.max(0, maxEntitiesPerFile-30)*0.25);
        score -= Math.min(10, Math.max(0, avgMethods-20)*0.4);
        score=Math.max(0,Math.min(100,score));

        metrics.add(new HealthMetric("overall-score","Overall health score","Summary",round(score),
            "/100",score>=80?"healthy":score>=60?"watch":"risk",
            "Explainable static score from the metrics above; it is not a security or production-readiness guarantee.",
            "weighted deductions from X-Ray static metrics"));

        return new CodeHealthRadar(round(score),List.copyOf(metrics),List.copyOf(findings),List.copyOf(warnings));
    }

    private boolean isType(CodeEntity e) {
        return "class".equals(e.kind()) || "interface".equals(e.kind());
    }
    private static String ownerId(String methodId) {
        int i=methodId.indexOf("#method:");
        return i>0?methodId.substring(0,i):"";
    }
    private static String status(double value,double watch,double risk){
        return value<watch?"healthy":value<risk?"watch":"risk";
    }
    private static double round(double v){return Math.round(v*100.0)/100.0;}
}
