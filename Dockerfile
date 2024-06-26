FROM gcr.io/distroless/java21-debian12@sha256:e4cb46a49683df2fd5a93bc669f0c56942d75ea6d08b08f506cc70ca686c5e57

COPY build/libs/app.jar /app/

WORKDIR /app
CMD ["app.jar"]
