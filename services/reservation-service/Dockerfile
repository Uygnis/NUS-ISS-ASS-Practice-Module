# syntax=docker/dockerfile:1.7
#
# RentEZ — production image. Built by CI on every merge to main and by
# `make aws-images`; deployed to EKS by `make aws-up`.
#
# Dockerfile.dev is the sibling of this file and stays as it is: Docker Compose
# builds it locally, where a plain layer cache is fine and BuildKit cache mounts
# would not be shared with the host's ~/.m2 anyway.
#
# THREE THINGS DIFFER FROM Dockerfile.dev, ALL OF THEM MATTERING ONLY ON AWS.

# 1. A BUILDKIT CACHE MOUNT instead of a dependency-resolution layer.
#    `dependency:go-offline` caches into an image layer, which a CI runner
#    throws away every build - five services x a full Maven tree, every merge.
#    A cache mount persists across builds and is shared between the five
#    services, because they resolve almost the same dependency set.
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B dependency:go-offline -DskipTests

COPY src ./src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# 2. A NON-ROOT USER. The dev image runs as root, which is harmless on a laptop
#    and is not on a node with a public IP. Nothing here needs to write outside
#    /tmp, so an unprivileged user costs nothing.
RUN groupadd --system --gid 1001 rentez \
 && useradd --system --uid 1001 --gid rentez --no-create-home rentez

COPY --from=builder --chown=rentez:rentez /app/target/*.jar app.jar
USER rentez

EXPOSE 8080

# 3. NO JVM FLAGS BAKED IN. JAVA_TOOL_OPTIONS comes from the Deployment
#    (-XX:MaxRAMPercentage=70), so the heap follows the container limit set in
#    Helm. Hard-coding -Xmx here would silently override it and re-create the
#    "five JVMs each claim the node's memory" problem the compose file warns
#    about.
ENTRYPOINT ["java", "-jar", "app.jar"]
