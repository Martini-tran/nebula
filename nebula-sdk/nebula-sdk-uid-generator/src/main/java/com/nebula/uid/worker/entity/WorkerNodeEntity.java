package com.nebula.uid.worker.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * worker_node 表实体：记录 UID 生成器的节点信息，每次进程启动会插入一条新记录，
 * 自增主键即作为该实例的 workerId。
 *
 * <p>派生自 baidu/uid-generator (Apache License 2.0)</p>
 *
 * @author nebula
 */
@Data
@TableName("worker_node")
public class WorkerNodeEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，作为 workerId 使用 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** ACTUAL 模式为 IP，CONTAINER 模式为 hostname */
    private String hostName;

    /** ACTUAL 模式为时间戳+随机数，CONTAINER 模式为容器 port */
    private String port;

    /** 节点类型，参见 {@link com.nebula.uid.worker.WorkerNodeType} */
    private Integer type;

    /** 启动日期 */
    private LocalDate launchDate;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime created;

    /** 最后修改时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime modified;
}
