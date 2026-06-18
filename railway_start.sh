#!/usr/bin/env bash
# Railway build entry point – guarantees a valid JAVA_HOME
# -------------------------------------------------

# Railway’s Linux images provide OpenJDK under /usr/lib/jvm
# Adjust the version if you prefer a different JDK.
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="${JAVA_HOME}/bin:${PATH}"

# Show the Java version for debugging (optional)
java -version

# Run the Maven wrapper with the flags you already use
./mvnw -DoutputFile=target/mvn-dependency-list.log -B -DskipTests clean dependency:list install -Pproduction
