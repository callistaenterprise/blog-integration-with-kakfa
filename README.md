**EndlessLoop Demo**

This demo is intended as a simulation for integrating applications using Apache Kafka.

---

## Prerequisites

To run this application locally you will need:

* A Java runtime environment (JRE) version 11 or higher.
* Docker and Docker Compose

## Usage

This repository contains a simulation consisting of a Micronaut application that will be deployed with a Kafka cluster.
The application will produce and consume messages to illustrate different situations.

This application uses Gradle to build. To build the application and publish as a Docker image
run  `/gradlew dockerBuild`.

The simulation can be started using Docker Compose. To start the simulation use `docker-compose up -d`. To stop the
simulation use `docker-compose down -v` - note that the `-v` is optional but is used to remove any Kafka topics to
between runs.