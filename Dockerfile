FROM gcr.io/distroless/java21-debian12@sha256:903d5ad227a4afff8a207cd25c580ed059cc4006bb390eae65fb0361fc9724c3

COPY build/libs/app.jar /app/

WORKDIR /app
CMD ["app.jar"]
