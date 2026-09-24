FROM eclipse-temurin:21-jre-alpine
ARG H2_VERSION=2.2.224
ADD https://repo1.maven.org/maven2/com/h2database/h2/${H2_VERSION}/h2-${H2_VERSION}.jar /opt/h2.jar
WORKDIR /data
EXPOSE 9092
CMD ["java", "-cp", "/opt/h2.jar", "org.h2.tools.Server", "-tcp", "-tcpAllowOthers", "-tcpPort", "9092", "-ifNotExists", "-baseDir", "/data"]
