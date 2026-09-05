package com.projectxray.core.git;

import com.projectxray.core.analysis.JavaProjectAnalyzer;
import com.projectxray.core.model.*;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class GitTimeMachineEngine {

    public GitTimeMachine analyze(Path root, int maxCommits) {
        root = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(root.resolve(".git"))) return GitTimeMachine.empty();

        List<String> warnings = new ArrayList<>();
        try {
            int limit = Math.max(1, Math.min(maxCommits, 100));
            List<GitCommit> commits = commits(root, limit);
            List<HistoricalSnapshot> snapshots = new ArrayList<>();

            // Analyze the selected historical commits, not fabricated snapshots.
            // Keep the default bounded for performance on developer laptops.
            for (GitCommit commit : commits) {
                Path temp = Files.createTempDirectory("xray-git-" + commit.shortHash());
                try {
                    extractArchive(root, commit.hash(), temp);
                    AnalysisResult r = new JavaProjectAnalyzer().analyze(temp, false);
                    int packages = (int) r.entities().stream()
                        .filter(e -> "class".equals(e.kind()) || "interface".equals(e.kind()))
                        .map(e -> packageOf(e.qualifiedName())).filter(s -> !s.isBlank()).distinct().count();
                    snapshots.add(new HistoricalSnapshot(
                        commit.hash(), commit.subject(), r.analyzedAt(), r.filesScanned(),
                        r.entities().size(), r.relations().size(), r.endpoints().size(),
                        packages, r.architectureGraph().cycles().size(), r.warnings()
                    ));
                } catch (Exception ex) {
                    warnings.add("Could not analyze commit " + commit.shortHash() + ": " + safe(ex));
                } finally {
                    deleteTree(temp);
                }
            }

            TimeMachineDiff diff = buildCurrentDiff(root, commits);
            return new GitTimeMachine(true, commits, List.copyOf(snapshots), diff, List.copyOf(warnings));
        } catch (Exception ex) {
            warnings.add("Git history analysis failed: " + safe(ex));
            return new GitTimeMachine(true, List.of(), List.of(), TimeMachineDiff.empty("", ""), List.copyOf(warnings));
        }
    }

    private static List<GitCommit> commits(Path root, int limit) throws Exception {
        String log = run(root, "git", "log", "-n", String.valueOf(limit),
            "--date=iso-strict", "--pretty=format:%H%x1f%h%x1f%an%x1f%aI%x1f%s%x1e");
        List<GitCommit> result = new ArrayList<>();
        for (String record : log.split("\\u001e")) {
            if (record.isBlank()) continue;
            String[] f = record.split("\\u001f", -1);
            if (f.length < 5) continue;
            int changed = changedFilesAt(root, f[0]);
            result.add(new GitCommit(f[0], f[1], f[2], f[3], f[4], changed));
        }
        return List.copyOf(result);
    }

    private static int changedFilesAt(Path root, String hash) throws Exception {
        String s = run(root, "git", "show", "--format=", "--name-only", "--no-renames", hash);
        return (int) s.lines().filter(x -> !x.isBlank()).distinct().count();
    }

    private static TimeMachineDiff buildCurrentDiff(Path root, List<GitCommit> commits) {
        if (commits.size() < 2) return TimeMachineDiff.empty(
            commits.isEmpty() ? "" : commits.get(0).hash(),
            commits.isEmpty() ? "" : commits.get(0).hash());

        GitCommit newest = commits.get(0), previous = commits.get(1);
        try {
            List<String> changed = run(root, "git", "diff", "--name-only", previous.hash(), newest.hash())
                .lines().filter(x -> !x.isBlank()).toList();

            AnalysisResult now = new JavaProjectAnalyzer().analyze(root, false);
            Path temp = Files.createTempDirectory("xray-diff-" + previous.shortHash());
            try {
                extractArchive(root, previous.hash(), temp);
                AnalysisResult before = new JavaProjectAnalyzer().analyze(temp, false);

                Set<String> beforeEntities = before.entities().stream().map(CodeEntity::qualifiedName)
                    .collect(Collectors.toSet());
                Set<String> afterEntities = now.entities().stream().map(CodeEntity::qualifiedName)
                    .collect(Collectors.toSet());

                Set<String> beforePkgs = packages(before);
                Set<String> afterPkgs = packages(now);

                return new TimeMachineDiff(previous.hash(), newest.hash(),
                    afterEntities.stream().filter(x -> !beforeEntities.contains(x)).sorted().toList(),
                    beforeEntities.stream().filter(x -> !afterEntities.contains(x)).sorted().toList(),
                    changed,
                    afterPkgs.stream().filter(x -> !beforePkgs.contains(x)).sorted().toList(),
                    beforePkgs.stream().filter(x -> !afterPkgs.contains(x)).sorted().toList(),
                    now.relations().size() - before.relations().size(),
                    now.endpoints().size() - before.endpoints().size(),
                    now.architectureGraph().cycles().size() - before.architectureGraph().cycles().size());
            } finally {
                deleteTree(temp);
            }
        } catch (Exception e) {
            return TimeMachineDiff.empty(previous.hash(), newest.hash());
        }
    }

    private static Set<String> packages(AnalysisResult r) {
        return r.entities().stream()
            .filter(e -> "class".equals(e.kind()) || "interface".equals(e.kind()))
            .map(e -> packageOf(e.qualifiedName()))
            .filter(x -> !x.isBlank()).collect(Collectors.toSet());
    }

    private static String packageOf(String qn) {
        if (qn == null || qn.isBlank()) return "";
        int dot = qn.lastIndexOf('.');
        return dot < 0 ? "" : qn.substring(0, dot);
    }

    private static void extractArchive(Path root, String hash, Path temp) throws Exception {
        Process p = new ProcessBuilder("git", "archive", "--format=tar", hash)
            .directory(root.toFile()).redirectErrorStream(true).start();
        try (InputStream in = p.getInputStream()) {
            // Use system tar only for extraction of Git's own archive stream.
            Process tar = new ProcessBuilder("tar", "-xf", "-", "-C", temp.toString())
                .redirectErrorStream(true).start();
            in.transferTo(tar.getOutputStream());
            tar.getOutputStream().close();
            int tarCode = tar.waitFor();
            int gitCode = p.waitFor();
            if (gitCode != 0 || tarCode != 0) throw new IOException("git archive extraction failed");
        }
    }

    private static String run(Path cwd, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(cwd.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) throw new IOException(out);
        return out;
    }

    private static String safe(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m.replace('\n',' ');
    }

    private static void deleteTree(Path root) {
        if (root == null) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
