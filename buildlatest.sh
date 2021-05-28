echo "Bygger sykepengesoknad-narmesteleder-varsler latest"

./gradlew bootJar

docker build . -t sykepengesoknad-narmesteleder-varsler:latest
