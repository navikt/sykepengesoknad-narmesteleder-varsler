#!/bin/bash
echo "Bygger sykepengesoknad-narmesteleder-varsler for bruk i flex-docker-compose"
./gradlew ktlintFormat
./gradlew shadowJar
docker build -t sykepengesoknad-narmesteleder-varsler:latest .
