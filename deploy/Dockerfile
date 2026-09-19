# Nebula 通用服务镜像
#
# 一份 Dockerfile 构建全部 5 个 Java 服务，通过 SERVICE_NAME 构建参数区分。
# 之所以不给每个服务写一份：它们的构建方式完全一致（同一个 Maven 反应堆、
# 同一个 JDK、同一种 Spring Boot 可执行 JAR），差异只有模块名和端口。
#
# 用法：
#   docker build -f deploy/Dockerfile --build-arg SERVICE_NAME=nebula-service-gateway -t nebula/gateway:1.0.4-SNAPSHOT .
# 注意 build context 必须是仓库根目录（因为要用到 nebula-parent / nebula-bom 等父模块）。

# ---------- 构建阶段 ----------
# Java 21：与 nebula-parent 的 <java.version>21</java.version> 一致，降版本会编译失败。
FROM maven:3.9-eclipse-temurin-21 AS builder

ARG SERVICE_NAME
ARG MAVEN_OPTS="-Xmx1g"

WORKDIR /build

# 国内节点构建必须走阿里云镜像：实测中央仓 763 KB/s、阿里云 15.4 MB/s，
# 本项目依赖树数百 MB，不配镜像首次构建会慢到不可接受。
COPY deploy/maven-settings.xml /root/.m2/settings.xml

# 先只复制 POM 并预热依赖，这样只改业务代码时能命中 Docker 层缓存，
# 不必每次重新下载整个依赖树（北京 4c4g 上这一步很值钱）。
COPY pom.xml ./
COPY nebula-parent/pom.xml nebula-parent/
COPY nebula-bom/pom.xml nebula-bom/
COPY nebula-apis/ nebula-apis/
COPY nebula-sdk/ nebula-sdk/
COPY nebula-starters/ nebula-starters/
COPY nebula-agents/ nebula-agents/
COPY nebula-services/ nebula-services/

# -pl 只构建目标服务，-am 连带构建它依赖的父模块与内部模块。
# 跳过测试：部署镜像里跑测试会显著拉长构建时间，测试应在 CI 或本地完成。
#
# --mount=type=cache 让三个服务共享同一份 ~/.m2/repository。没有它的话，
# 每个 SERVICE_NAME 都是独立的构建层，依赖树要重新下载三遍。
# 需要 BuildKit（Docker 23+ 默认启用）。
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn -B -q -pl "nebula-services/${SERVICE_NAME}" -am \
        -DskipTests -Dmaven.test.skip=true \
        clean package \
 && TARGET_DIR="nebula-services/${SERVICE_NAME}/target" \
 && APP_JAR="$(ls "${TARGET_DIR}/${SERVICE_NAME}-"*-app.jar 2>/dev/null | head -n1)" \
 && if [ -z "$APP_JAR" ]; then \
        APP_JAR="$(ls "${TARGET_DIR}/${SERVICE_NAME}-"*.jar | grep -v '\.original$' | grep -v -- '-sources\.jar$' | head -n1)"; \
    fi \
 && echo "[build] picked jar: $APP_JAR" \
 && cp "$APP_JAR" /build/app.jar

# ---------- 运行阶段 ----------
# 只要 JRE，不带 Maven 和 JDK 工具链，镜像小很多。
FROM eclipse-temurin:21-jre-jammy

ARG SERVICE_NAME
ENV SERVICE_NAME=${SERVICE_NAME}

# 时区：服务的 JDBC URL 写死了 serverTimezone=Asia/Shanghai，容器时区保持一致，
# 否则日志时间和数据库时间对不上，排查问题时很容易被误导。
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
 && apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# 非 root 运行。
RUN groupadd -r nebula && useradd -r -g nebula nebula

WORKDIR /app
COPY --from=builder /build/app.jar /app/app.jar
RUN mkdir -p /app/logs && chown -R nebula:nebula /app

USER nebula

# 堆上限交给 compose 的 JAVA_TOOL_OPTIONS 注入，这里不写死，
# 因为 5 个服务在 4c4g 上的配额并不相同。
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]
