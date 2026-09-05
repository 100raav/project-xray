package com.projectxray.core.index;

import java.util.*;

/**
 * Exact persistent method resolver. It accepts Java-style parameter type names
 * and only returns a method when the persisted qualified signature matches.
 * Arity-only matching is retained as a deliberately ambiguous fallback.
 */
public final class PersistentMethodResolver {
    public record MethodResolution(
        String ownerQuery, String methodQuery, List<String> parameterTypes,
        PersistentSymbolStore.SymbolRecord method,
        List<PersistentSymbolStore.SymbolRecord> candidates,
        String strategy, boolean resolved, String reason
    ) {}

    private final PersistentSymbolResolver symbols = new PersistentSymbolResolver();

    public MethodResolution resolve(PersistentSymbolStore.StoreDocument doc,
                                     String ownerQuery, String methodQuery,
                                     List<String> parameterTypes) {
        List<String> normalizedParameterTypes = parameterTypes.stream()
            .map(PersistentMethodResolver::normalizeType)
            .toList();
        var owner=symbols.resolve(doc,ownerQuery);
        if(!owner.resolved())
            return new MethodResolution(ownerQuery,methodQuery,normalizedParameterTypes,null,List.of(),
                "owner-unresolved",false,"Owner type could not be resolved.");

        String ownerPrefix=owner.symbol().qualifiedName()+"#"+methodQuery+"(";
        List<PersistentSymbolStore.SymbolRecord> exactMatches=doc.symbols().stream()
            .filter(s -> "method".equals(s.kind()))
            .filter(s -> s.qualifiedName()!=null && s.qualifiedName().startsWith(ownerPrefix))
            .filter(s -> normalizedParameterTypes.equals(parseParameters(s.qualifiedName())))
            .toList();
        if(exactMatches.size()==1)
            return new MethodResolution(ownerQuery,methodQuery,normalizedParameterTypes,exactMatches.get(0),
                exactMatches,"owner+name+parameter-types",true,"Exact persisted qualified signature.");

        List<PersistentSymbolStore.SymbolRecord> candidates=doc.symbols().stream()
            .filter(s -> "method".equals(s.kind()))
            .filter(s -> s.qualifiedName()!=null && s.qualifiedName().startsWith(owner.symbol().qualifiedName()+"#"+methodQuery+"("))
            .toList();
        if(candidates.isEmpty())
            return new MethodResolution(ownerQuery,methodQuery,normalizedParameterTypes,null,List.of(),
                "not-found",false,"No persisted method matched the exact signature.");
        return new MethodResolution(ownerQuery,methodQuery,normalizedParameterTypes,null,candidates,
            "ambiguous-overload",false,"Overloaded methods exist but no exact persisted parameter signature matched.");
    }

    /** Compatibility helper for callers that only know arity; it never guesses an overload. */
    public MethodResolution resolve(PersistentSymbolStore.StoreDocument doc,
                                     String ownerQuery, String methodQuery, int arity) {
        var owner=symbols.resolve(doc,ownerQuery);
        if(!owner.resolved()) return new MethodResolution(ownerQuery,methodQuery,List.of(),null,List.of(),"owner-unresolved",false,"Owner type could not be resolved.");
        List<PersistentSymbolStore.SymbolRecord> candidates=doc.symbols().stream()
            .filter(s -> "method".equals(s.kind()))
            .filter(s -> s.qualifiedName()!=null && s.qualifiedName().startsWith(owner.symbol().qualifiedName()+"#"+methodQuery+"("))
            .filter(s -> parameterCount(s.qualifiedName())==arity).toList();
        return candidates.size()==1
            ? new MethodResolution(ownerQuery,methodQuery,List.of(),candidates.get(0),candidates,"owner+name+arity",true,"Unique persisted arity candidate.")
            : new MethodResolution(ownerQuery,methodQuery,List.of(),null,candidates,candidates.isEmpty()?"not-found":"ambiguous-overload",false,
                candidates.isEmpty()?"No persisted method matched owner/name/arity.":"Multiple overloads match the requested arity; parameter types are required.");
    }

    private static List<String> parseParameters(String signature) {
        int a=signature.indexOf('('), b=signature.lastIndexOf(')');
        if(a<0||b<a) return List.of();
        String inside=signature.substring(a+1,b).trim();
        if(inside.isEmpty()) return List.of();
        return Arrays.stream(inside.split(","))
            .map(PersistentMethodResolver::normalizeType)
            .toList();
    }

    private static String normalizeType(String value) {
        return value.trim().replace("java.lang.","").replace("java.util.","").replaceAll("\\s+","");
    }

    private static int parameterCount(String signature) {
        int a=signature.indexOf('('),b=signature.lastIndexOf(')');
        if(a<0||b<a)return -1;
        String inside=signature.substring(a+1,b).trim();
        if(inside.isEmpty())return 0;
        int depth=0,count=1;
        for(char c:inside.toCharArray()) { if(c=='<') depth++; else if(c=='>') depth=Math.max(0,depth-1); else if(c==','&&depth==0) count++; }
        return count;
    }
}
