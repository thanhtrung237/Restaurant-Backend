FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
# The repository is also used on Windows; normalize the wrapper before running it.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    set -eu; \
    for attempt in 1 2 3; do \
        if ./gradlew --no-daemon --console=plain bootJar -x test > /tmp/gradle-build.log 2>&1; then \
            cat /tmp/gradle-build.log; \
            break; \
        fi; \
        cat /tmp/gradle-build.log; \
        if [ "$attempt" -eq 3 ] || ! grep -Fq 'Remote host terminated the handshake' /tmp/gradle-build.log; then \
            exit 1; \
        fi; \
        echo "Gradle TLS connection interrupted. Retrying with cached dependencies."; \
        sleep 3; \
    done; \
    find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' \
       -exec cp {} /tmp/app.jar \;

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 restaurant \
    && useradd --system --uid 10001 --gid restaurant --home-dir /app restaurant
WORKDIR /app
COPY --from=build --chown=restaurant:restaurant /tmp/app.jar ./app.jar
ENV SPRING_PROFILES_ACTIVE=docker \
    SERVER_PORT=8080 \
    TZ=Asia/Ho_Chi_Minh
USER restaurant
EXPOSE 8080
# Initial Hibernate schema creation on Docker Desktop can take several minutes.
HEALTHCHECK --interval=15s --timeout=5s --start-period=240s --retries=5 \
    CMD curl --fail --silent --output /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
