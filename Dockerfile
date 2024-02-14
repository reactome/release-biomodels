ARG REPO_DIR=/opt/release-biomodels


# ===== stage 1 =====
FROM eclipse-temurin:11-jdk-focal AS setup-env

ARG REPO_DIR

WORKDIR ${REPO_DIR}

COPY . .

SHELL ["/bin/bash", "-c"]

# install maven
RUN apt-get update && \
    apt-get install -y maven

# run lint if container started
ENTRYPOINT []

CMD mvn -B -q checkstyle:check | grep -i --color=never '\.java\|failed to execute goal' > lint.log


# ===== stage 2 =====
FROM setup-env AS build-jar

RUN mvn clean compile assembly:single


# ===== stage 3 =====
FROM eclipse-temurin:11-jre-focal

ARG REPO_DIR

ARG JAR_FILE=target/biomodels-*-jar-with-dependencies.jar

WORKDIR ${REPO_DIR}

COPY --from=build-jar ${REPO_DIR}/${JAR_FILE} ./target/
