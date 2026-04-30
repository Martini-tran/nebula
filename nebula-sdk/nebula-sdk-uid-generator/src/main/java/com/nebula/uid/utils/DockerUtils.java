package com.nebula.uid.utils;

import lombok.extern.slf4j.Slf4j;

/**
 * Docker 环境探测工具，通过环境变量判断是否运行在容器内。
 *
 * <p>派生自 baidu/uid-generator (Apache License 2.0)</p>
 *
 * @author nebula
 */
@Slf4j
public abstract class DockerUtils {

    /** 环境变量 key */
    private static final String ENV_KEY_HOST = "JPAAS_HOST";
    private static final String ENV_KEY_PORT = "JPAAS_HTTP_PORT";
    private static final String ENV_KEY_PORT_ORIGINAL = "JPAAS_HOST_PORT_8080";

    private static String DOCKER_HOST = "";
    private static String DOCKER_PORT = "";
    private static boolean IS_DOCKER;

    static {
        retrieveFromEnv();
    }

    /** 容器 host，非 docker 环境返回空串 */
    public static String getDockerHost() {
        return DOCKER_HOST;
    }

    /** 容器 port，非 docker 环境返回空串 */
    public static String getDockerPort() {
        return DOCKER_PORT;
    }

    /** 是否运行在 Docker 容器内 */
    public static boolean isDocker() {
        return IS_DOCKER;
    }

    private static void retrieveFromEnv() {
        DOCKER_HOST = System.getenv(ENV_KEY_HOST);
        DOCKER_PORT = System.getenv(ENV_KEY_PORT);

        if (isBlank(DOCKER_PORT)) {
            DOCKER_PORT = System.getenv(ENV_KEY_PORT_ORIGINAL);
        }

        boolean hasEnvHost = !isBlank(DOCKER_HOST);
        boolean hasEnvPort = !isBlank(DOCKER_PORT);

        if (hasEnvHost && hasEnvPort) {
            IS_DOCKER = true;
        } else if (!hasEnvHost && !hasEnvPort) {
            IS_DOCKER = false;
        } else {
            log.error("Missing host or port from env for Docker. host:{}, port:{}", DOCKER_HOST, DOCKER_PORT);
            throw new RuntimeException(
                    "Missing host or port from env for Docker. host:" + DOCKER_HOST + ", port:" + DOCKER_PORT);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
