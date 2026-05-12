FROM eclipse-temurin:23-jdk-alpine AS builder

# Install Gradle in the lightweight JDK-based Alpine image
RUN apk add --no-cache curl unzip bash && \
    curl -fsSL https://services.gradle.org/distributions/gradle-8.10-bin.zip -o gradle.zip && \
    unzip gradle.zip && \
    mv gradle-* /opt/gradle && \
    ln -s /opt/gradle/bin/gradle /usr/bin/gradle && \
    rm gradle.zip

WORKDIR /app

COPY gradle              ./gradle
COPY build.gradle        ./
COPY settings.gradle     ./
COPY src                 ./src

RUN gradle clean build --no-daemon -x test

FROM eclipse-temurin:23-jre-alpine AS runner

WORKDIR /app

COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]