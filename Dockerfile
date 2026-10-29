# ---------------------------------------------
# Giai đoạn 1: Build file JAR bằng Maven & Java 17
# ---------------------------------------------
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy file cấu hình maven và mã nguồn
COPY pom.xml .
COPY src ./src

# Build package thành file JAR (bỏ qua tests để deploy nhanh)
RUN mvn clean package -DskipTests

# ---------------------------------------------
# Giai đoạn 2: Runtime image siêu nhẹ bằng JRE 17 Alpine
# ---------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy file jar từ builder stage
COPY --from=builder /build/target/*.jar app.jar

# Render sẽ tự gán biến môi trường PORT và Spring Boot nhận qua ${PORT:8080}
EXPOSE 8080

# Tối ưu JVM cho gói Free của Render (512MB RAM & 0.1 CPU):
# - Dùng Serial GC (-XX:+UseSerialGC) để giảm thiểu tối đa CPU overhead và native memory của G1GC
# - Giới hạn Heap tối đa 256MB (-Xms128m -Xmx256m)
# - Giới hạn Metaspace tối đa 160MB (-XX:MaxMetaspaceSize=160m)
# - Giới hạn Stack của mỗi Thread xuống 512KB (-Xss512k)
# - Tự động thoát khi dính OOM (-XX:+ExitOnOutOfMemoryError)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:+UseSerialGC -Xms128m -Xmx256m -Xss512k -XX:MaxMetaspaceSize=160m -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
