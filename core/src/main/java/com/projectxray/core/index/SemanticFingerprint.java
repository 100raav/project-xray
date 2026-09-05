package com.projectxray.core.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Conservative semantic-ish fingerprint for Java source.
 *
 * It removes comments and insignificant whitespace while preserving string and
 * character literals. This is intentionally not a Java compiler hash: it is a
 * safe optimization only for changes that normalize to the same token text.
 */
public final class SemanticFingerprint {
    private SemanticFingerprint() {}

    public static String of(Path file) throws IOException {
        return sha256(normalize(Files.readString(file)));
    }

    static String normalize(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean lineComment=false, blockComment=false, string=false, character=false, escape=false;
        boolean whitespace=false;
        for (int i=0;i<source.length();i++) {
            char c=source.charAt(i);
            char n=i+1<source.length()?source.charAt(i+1):'\0';

            if (lineComment) {
                if (c=='\n') { lineComment=false; whitespace=true; }
                continue;
            }
            if (blockComment) {
                if (c=='*' && n=='/') { blockComment=false; i++; whitespace=true; }
                continue;
            }
            if (!string && !character && c=='/' && n=='/') { lineComment=true; i++; continue; }
            if (!string && !character && c=='/' && n=='*') { blockComment=true; i++; continue; }

            if (string) {
                out.append(c);
                if (escape) escape=false;
                else if (c=='\\') escape=true;
                else if (c=='"') string=false;
                continue;
            }
            if (character) {
                out.append(c);
                if (escape) escape=false;
                else if (c=='\\') escape=true;
                else if (c=='\'') character=false;
                continue;
            }
            if (c=='"') { appendSeparatorIfNeeded(out, whitespace, c); whitespace=false; string=true; out.append(c); continue; }
            if (c=='\'') { appendSeparatorIfNeeded(out, whitespace, c); whitespace=false; character=true; out.append(c); continue; }
            if (Character.isWhitespace(c)) { whitespace=true; continue; }
            appendSeparatorIfNeeded(out, whitespace, c);
            whitespace=false;
            out.append(c);
        }
        return out.toString().trim();
    }

    private static void appendSeparatorIfNeeded(StringBuilder out, boolean whitespace, char next) {
        if (whitespace && out.length() > 0
                && Character.isJavaIdentifierPart(out.charAt(out.length() - 1))
                && Character.isJavaIdentifierPart(next)) {
            out.append(' ');
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder();
            for(byte x:digest)b.append(String.format("%02x",x));
            return b.toString();
        } catch(Exception e){ throw new IllegalStateException(e); }
    }
}
