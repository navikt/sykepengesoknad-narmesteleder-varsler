FROM gcr.io/distroless/java17-debian11@sha256:02da3336c22a538c37084e293d13b69bf1bee1f6058404cef28192aa667d19d2

COPY build/libs/app.jar /app/

WORKDIR /app
CMD ["app.jar"]
