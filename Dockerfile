FROM eclipse-temurin:17 as builder
WORKDIR /app
COPY . .
RUN ./gradlew :server:installDist

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/server/build/install/server ./
COPY --from=builder /app/app/src/main/assets ./app/src/main/assets
EXPOSE 9018
ENTRYPOINT ["./bin/server"]
