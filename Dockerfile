FROM navikt/java:16
COPY build/libs/*.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback.xml'
x
