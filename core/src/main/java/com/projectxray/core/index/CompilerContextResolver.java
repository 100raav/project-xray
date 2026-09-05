package com.projectxray.core.index;

import java.util.*;

/**
 * Persistent compiler-context facade.
 *
 * Resolves a source-level type against the persisted project symbols first,
 * then reports whether the name is an external/JDK type or an unresolved
 * project symbol. It never invents a project symbol for an ambiguous name.
 */
public final class CompilerContextResolver {
    public record TypeResolution(
        String query,
        String packageName,
        String resolvedId,
        String qualifiedName,
        String category,
        List<PersistentSymbolStore.SymbolRecord> candidates,
        String reason
    ) {
        public boolean resolved() { return resolvedId != null; }
    }

    private final PersistentSymbolResolver resolver = new PersistentSymbolResolver();

    public TypeResolution resolveType(PersistentSymbolStore.StoreDocument doc,
                                      String typeText, String packageName,
                                      List<String> imports) {
        String q = normalize(typeText);
        if (q.isBlank()) return new TypeResolution(typeText, packageName, null, null,
            "invalid", List.of(), "empty type");

        // Exact/qualified/project symbol resolution.
        if (q.indexOf('.') >= 0 || q.startsWith("type:")) {
            var exact = resolver.resolve(doc, q);
            if (exact.resolved()) return resolved(q, packageName, exact.symbol(), "qualified/project symbol");
        }

        if (q.indexOf('.') < 0 && packageName != null && !packageName.isBlank()) {
            var pkg = resolver.resolve(doc, packageName + "." + q);
            if (pkg.resolved()) return resolved(q, packageName, pkg.symbol(), "same-package symbol");
        }

        if (imports != null) {
            for (String imp : imports) {
                String candidate = imp.endsWith(".*") ? imp.substring(0, imp.length()-2)+"."+q : imp;
                if (!imp.endsWith(".*") && !imp.endsWith("."+q)) continue;
                if (imp.endsWith(".*") || imp.endsWith("."+q)) {
                    var imported = resolver.resolve(doc, candidate);
                    if (imported.resolved()) return resolved(q, packageName, imported.symbol(), "imported symbol");
                }
            }
        }

        var simple = resolver.resolve(doc, q);
        if (!simple.resolved() && simple.candidates().size() > 1)
            return new TypeResolution(q, packageName, null, null, "ambiguous",
                simple.candidates(), "multiple project symbols share this simple name");

        if (looksLikeJavaPlatformType(q))
            return new TypeResolution(q, packageName, null, q, "external",
                List.of(), "not present in project store; likely JDK/platform type");

        return new TypeResolution(q, packageName, null, null, "unresolved",
            List.of(), "no project symbol or known platform type matched");
    }

    private static TypeResolution resolved(String q, String pkg,
                                           PersistentSymbolStore.SymbolRecord s, String why) {
        return new TypeResolution(q, pkg, s.id(), s.qualifiedName(), "project",
            List.of(s), why);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String q=s.trim();
        while (q.endsWith("[]")) q=q.substring(0,q.length()-2);
        q=q.replace("? extends ","").replace("? super ","").replace("?","");
        int generic=q.indexOf('<');
        if(generic>=0) q=q.substring(0,generic);
        return q.trim();
    }

    private static boolean looksLikeJavaPlatformType(String q) {
        return Set.of("String","Object","Integer","Long","Double","Float","Boolean",
            "Byte","Short","Character","Number","Void","Exception","RuntimeException",
            "Throwable","List","Set","Map","Collection","Iterable","Optional",
            "Stream","StringBuilder","StringBuffer","Override","Deprecated").contains(q)
            || q.startsWith("java.") || q.startsWith("javax.");
    }
}
