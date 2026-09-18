# ============================================================
# CodeAtlas 后端镜像
#
# 采用多阶段构建：
#   阶段 1（build）  用 Maven + JDK 17 编译打包
#   阶段 2（runtime）只保留 JRE 与可执行 jar
# 这样最终镜像不含源码、Maven 仓库与 JDK，体积与攻击面都更小。
# ============================================================

# ---------------- 阶段 1：构建 ----------------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# 先只复制 pom.xml 并预拉依赖。
# 只要 pom.xml 未变动，这层就能命中缓存，后续改源码无需重新下载依赖。
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

# 再复制源码并打包（跳过测试：测试在 CI / 本地执行，不进入镜像构建）
COPY src ./src
RUN mvn -B -DskipTests clean package

# ---------------- 阶段 2：运行 ----------------
FROM eclipse-temurin:17-jre-alpine

# curl 仅用于容器健康检查
RUN apk add --no-cache curl \
    && addgroup -S codeatlas \
    && adduser -S codeatlas -G codeatlas

WORKDIR /app

# 上传文件的落盘目录，由 compose 挂载为数据卷
RUN mkdir -p /app/uploads && chown -R codeatlas:codeatlas /app

COPY --from=build --chown=codeatlas:codeatlas /build/target/*.jar /app/app.jar

# 不以 root 运行
USER codeatlas

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    STORAGE_ROOT=/app/uploads \
    TZ=Asia/Shanghai

EXPOSE 8080

# 应用内含 /health 端点（无需认证）。注意它只要进程存活就返回 200，
# 因此这里只作为「容器已就绪」的判断依据，不作为业务可用性探针。
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD curl -fsS http://localhost:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
