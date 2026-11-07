#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/classes
find src -name '*.java' -print > build/sources.txt
javac -encoding ISO-8859-1 -source 8 -target 8 \
  -cp 'jars/*:jars/commons-math3-3.5/*' \
  -d build/classes @build/sources.txt
java -cp 'build/classes:jars/*:jars/commons-math3-3.5/*' \
  org.fog.test.perfeval.GpcFuzzyQosExperiment
