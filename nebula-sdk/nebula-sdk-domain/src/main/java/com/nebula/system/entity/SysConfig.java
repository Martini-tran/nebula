package com.nebula.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统配置表
 *
 * @author nebula
 */
@Data
@TableName("sys_config")
public class SysConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置ID */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 配置键（唯一，如 site.title） */
    private String configKey;

    /** 配置值（支持长文本、JSON等） */
    private String configValue;

    /** 类型：1=文本 2=数字 3=布尔 4=JSON */
    private Integer configType;

    /** 配置名称（如 网站标题） */
    private String configName;

    /** 配置分组（如 system, email, security） */
    private String groupName;

    /** 是否前端可见：1=是（如主题色） 0=否（如数据库密码） */
    private Integer isFrontend;

    /** 备注 */
    private String remark;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
