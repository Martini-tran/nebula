package com.nebula.uid.worker;

import com.nebula.uid.utils.DockerUtils;
import com.nebula.uid.utils.NetUtils;
import com.nebula.uid.worker.entity.WorkerNodeEntity;
import com.nebula.uid.worker.mapper.WorkerNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一次性 WorkerId 分配器：每次启动插入一条新记录，自增 ID 即 workerId，
 * 进程结束后该 workerId 不会被复用（Disposable）。
 *
 * 
 *
 * @author nebula
 */
@Slf4j
@RequiredArgsConstructor
public class DisposableWorkerIdAssigner implements WorkerIdAssigner {

    private final WorkerNodeMapper workerNodeMapper;

    /**
     * 基于数据库分配 workerId。
     * 若环境变量包含容器主机名与端口，则视为容器节点，否则视为物理节点。
     *
     * @return 分配到的 workerId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long assignWorkerId() {
        WorkerNodeEntity entity = buildWorkerNode();
        workerNodeMapper.insert(entity);
        log.info("Add worker node: {}", entity);
        return entity.getId();
    }

    private WorkerNodeEntity buildWorkerNode() {
        WorkerNodeEntity entity = new WorkerNodeEntity();
        if (DockerUtils.isDocker()) {
            entity.setType(WorkerNodeType.CONTAINER.value());
            entity.setHostName(DockerUtils.getDockerHost());
            entity.setPort(DockerUtils.getDockerPort());
        } else {
            entity.setType(WorkerNodeType.ACTUAL.value());
            entity.setHostName(NetUtils.getLocalAddress());
            entity.setPort(System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(100000));
        }
        entity.setLaunchDate(LocalDate.now());
        LocalDateTime now = LocalDateTime.now();
        entity.setCreated(now);
        entity.setModified(now);
        return entity;
    }
}
