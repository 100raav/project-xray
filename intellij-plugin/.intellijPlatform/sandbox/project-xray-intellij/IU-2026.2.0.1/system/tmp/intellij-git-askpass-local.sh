#!/bin/sh
"/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home/bin/java" -cp "/Applications/IntelliJ IDEA.app/Contents/plugins/vcs-git/lib/git4idea-rt.jar:/Applications/IntelliJ IDEA.app/Contents/lib/externalProcess-rt.jar" git4idea.http.GitAskPassApp "$@"
