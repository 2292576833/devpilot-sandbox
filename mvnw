#!/bin/sh
# Maven wrapper - delegates to bundled Maven
DIR="$(cd "$(dirname "$0")" && pwd)"
MVN="$DIR/.maven/apache-maven-3.9.16/bin/mvn"
if [ -f "$MVN" ]; then
    exec "$MVN" "$@"
else
    echo "Maven not found at $MVN" >&2
    exit 1
fi
