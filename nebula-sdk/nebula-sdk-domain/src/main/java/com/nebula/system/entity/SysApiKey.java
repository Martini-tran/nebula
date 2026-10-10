package com.nebula.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * API Key（SK）表
 * 库里是 key 的真相源；运行时校验读的是 Sa-Token 写在 Redis 里的那份，由管理端在增删改时同步。
 *
 * @author nebula
 */
@Data
@TableName("sys_api_key")
public class SysApiKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花算法）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属用户ID：凭此 key 发起的请求以该用户身份执行
     */
    private Long userId;

    /**
     * 名称（展示用，说明这把 key 给谁/做什么用）
     */
    private String name;

    /**
     * key 密文（AES 加密）。需要可逆：Redis 丢数据后要靠它把 key 重新灌回去
     */
    private String apiKey;

    /**
     * key 掩码（只露首尾），列表展示用
     */
    private String keyMask;

    /**
     * 授权范围，英文逗号分隔
     */
    private String scopes;

    /**
     * 过期时间，为空表示永不过期
     */
    private LocalDateTime expireTime;

    /**
     * 状态：1启用 0禁用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除：0否 1是
     */
    @TableLogic
    private Integer deleted;
}
