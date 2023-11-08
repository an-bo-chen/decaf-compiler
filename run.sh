#!/usr/bin/env bash

cd "$(dirname "$0")"
JARS=$(find target lib -name '*.jar' | tr '\n' ':')

scala -classpath "$JARS" decaf.compile.Compiler "$@"