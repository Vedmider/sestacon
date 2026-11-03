# syntax=docker/dockerfile:1.7
# Base images are fully configurable. Either override just the registry prefix:
#   docker build --build-arg REGISTRY=my-mirror.example.com/library .
# or override a whole image ref (any registry/name/tag):
#   docker build --build-arg BUILD_IMAGE=my/maven:3.9-jdk21 --build-arg RUNTIME_IMAGE=my/jre:21 .
ARG REGISTRY=docker.io/library
ARG BUILD_IMAGE=${REGISTRY}/maven:3.9-eclipse-temurin-21
ARG RUNTIME_IMAGE=${REGISTRY}/eclipse-temurin:21-jre

FROM ${BUILD_IMAGE} AS build
WORKDIR /build
COPY pom.xml .
# A Maven settings.xml is OPTIONAL and only needed behind a private mirror. Provide it as
# a BuildKit secret and it is used automatically; otherwise the build uses Maven Central:
#   docker build --secret id=m2settings,src=$HOME/.m2/settings.xml .
RUN --mount=type=secret,id=m2settings,target=/root/.m2/settings.xml,required=false \
    mvn -B $([ -s /root/.m2/settings.xml ] && echo -s /root/.m2/settings.xml) dependency:go-offline
COPY src ./src
RUN --mount=type=secret,id=m2settings,target=/root/.m2/settings.xml,required=false \
    mvn -B $([ -s /root/.m2/settings.xml ] && echo -s /root/.m2/settings.xml) -DskipTests clean package

FROM ${RUNTIME_IMAGE} AS runtime
WORKDIR /app
RUN groupadd -r app -g 10001 && useradd -r -g app -u 10001 -s /usr/sbin/nologin app
# Bake the DJL native cache (pre-cxx11 libtorch + tokenizers) so embeddings work
# offline — no runtime download from publish.djl.ai in the cluster.
COPY docker/djl-cache /opt/djl-cache
ENV PYTORCH_PRECXX11=true \
    DJL_CACHE_DIR=/opt/djl-cache \
    JAVA_TOOL_OPTIONS=-Dai.djl.offline=true
COPY --from=build /build/target/rag-app-*.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
