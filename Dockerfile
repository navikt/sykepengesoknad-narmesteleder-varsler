FROM gcr.io/distroless/java17@sha256:78d2c280d0914978844d2a2dd2b5315acd437e33c6905b6c562dca97ae34d9b3

COPY build/libs/app.jar /app/

WORKDIR /app
CMD ["app.jar"]
