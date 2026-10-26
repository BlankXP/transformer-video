# ---------- 1. 前端构建 ----------
FROM node:22-slim AS frontend

WORKDIR /fe

RUN npm config set registry https://registry.npmmirror.com

COPY frontend/package.json frontend/package-lock.json ./
# 严格按 lock 安装（package.json 与 lock 不一致时直接失败），构建可复现
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ---------- 2. 后端构建（前端产物打进 jar 的 static） ----------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY backend/pom.xml .

RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>' > /root/.m2/settings.xml && \
    echo '<settings>' >> /root/.m2/settings.xml && \
    echo '  <mirrors>' >> /root/.m2/settings.xml && \
    echo '    <mirror>' >> /root/.m2/settings.xml && \
    echo '      <id>aliyun</id>' >> /root/.m2/settings.xml && \
    echo '      <mirrorOf>central</mirrorOf>' >> /root/.m2/settings.xml && \
    echo '      <name>Aliyun Maven</name>' >> /root/.m2/settings.xml && \
    echo '      <url>https://maven.aliyun.com/repository/public</url>' >> /root/.m2/settings.xml && \
    echo '    </mirror>' >> /root/.m2/settings.xml && \
    echo '  </mirrors>' >> /root/.m2/settings.xml && \
    echo '</settings>' >> /root/.m2/settings.xml

RUN mvn dependency:go-offline -B

COPY backend/src ./src

# 前端产物直接落到 Spring Boot 静态资源目录，由 jar 一起托管，不再需要独立 nginx 容器
COPY --from=frontend /fe/dist ./src/main/resources/static

RUN mvn package -DskipTests -B

# ---------- 3. 运行 ----------
FROM eclipse-temurin:17-jre-jammy

RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \
    sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \
    apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    yt-dlp \
    fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/target/bili-translator-0.0.1-SNAPSHOT.jar app.jar

RUN mkdir -p /app/temp

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "app.jar"]
