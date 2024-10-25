# This dockerfile for testing build in isolated environment !!!

FROM adoptopenjdk/openjdk11:jdk-11.0.8_10-ubuntu

WORKDIR /app

COPY mvnw .
COPY .mvn ./.mvn
COPY pom.xml .
COPY lib ./lib
COPY src ./src

RUN ls -lah
RUN ls .mvn -lah
RUN ls .mvn/wrapper -lah

RUN ./mvnw clean package

