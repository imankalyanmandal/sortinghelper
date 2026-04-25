FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first — download dependencies (cached layer)
COPY pom.xml .

# Fix packaging: change WAR to JAR before building
# WAR needs an external servlet container — JAR has embedded Tomcat and runs standalone
RUN sed -i 's|<packaging>war</packaging>|<packaging>jar</packaging>|g' pom.xml

RUN mvn dependency:go-offline -q

# Copy source and build fat executable JAR
COPY src ./src
RUN mvn package -DskipTests -q

# ── Runtime image (smaller) ───────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN mkdir -p /data

# Match any version of the JAR name
COPY --from=builder /app/target/java-service-*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m"

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]