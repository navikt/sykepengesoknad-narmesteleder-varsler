FROM gcr.io/distroless/java21-debian12@sha256:245a5c2bbdbd5c9f859079f885cd03054340f554c6fcf67f14fef894a926979b

COPY build/libs/app.jar /app/

WORKDIR /app
CMD ["app.jar"]
