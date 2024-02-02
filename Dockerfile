FROM gcr.io/distroless/java21-debian12@sha256:b7c03dfcbaf93a7408c8b9fa817d2c973287cfdd1807f6c1724302763887b647

COPY build/libs/app.jar /app/

WORKDIR /app
CMD ["app.jar"]
