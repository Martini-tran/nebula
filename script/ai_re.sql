CREATE TABLE ai_relay_provider (
                                   id BIGINT NOT NULL AUTO_INCREMENT COMMENT '服务商ID',
                                   name VARCHAR(100) NOT NULL COMMENT '服务商名称（如 OpenRouter）',
                                   website_url VARCHAR(255) DEFAULT NULL COMMENT '官网地址',
                                   logo_file_id BIGINT DEFAULT NULL COMMENT 'Logo文件ID（sys_file）',
                                   description VARCHAR(1000) DEFAULT NULL COMMENT '服务商简介',
                                   recommend_score DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '综合推荐分（核心排序依据）',
                                   sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
                                   status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1正常 0下线）',
                                   create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   PRIMARY KEY (id),
                                   UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI中转服务商';

CREATE TABLE ai_relay_package_type (
                                       id BIGINT NOT NULL AUTO_INCREMENT COMMENT '套餐类型ID',
                                       code VARCHAR(50) NOT NULL COMMENT '套餐类型编码（day/week/month/usage等）',
                                       name VARCHAR(50) NOT NULL COMMENT '套餐类型名称（天卡/周卡/月卡/按量）',
                                       billing_mode TINYINT NOT NULL COMMENT '计费模式（1固定周期 2按量计费）',
                                       duration_value INT DEFAULT NULL COMMENT '套餐周期数值，如1、7、30，按量计费可为空',
                                       duration_unit TINYINT DEFAULT NULL COMMENT '周期单位（1天 2周 3月 4年），按量计费可为空',
                                       description VARCHAR(500) DEFAULT NULL COMMENT '类型说明',
                                       sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
                                       status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
                                       create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                       PRIMARY KEY (id),
                                       UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI中转套餐类型配置';

CREATE TABLE ai_relay_provider_package (
                                           id BIGINT NOT NULL AUTO_INCREMENT COMMENT '套餐ID',
                                           provider_id BIGINT NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
                                           package_type_id BIGINT NOT NULL COMMENT '套餐类型ID（ai_relay_package_type.id）',
                                           name VARCHAR(100) NOT NULL COMMENT '套餐名称',
                                           price DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '套餐价格',
                                           original_price DECIMAL(10,2) DEFAULT NULL COMMENT '原价/划线价',
                                           currency VARCHAR(20) NOT NULL DEFAULT 'CNY' COMMENT '币种（CNY/USD等）',
                                           is_recommended TINYINT NOT NULL DEFAULT 0 COMMENT '是否推荐（1是 0否）',
                                           recommend_score DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '套餐推荐分',
                                           description VARCHAR(1000) DEFAULT NULL COMMENT '套餐说明',
                                           sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
                                           status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1正常 0下线）',
                                           create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                           update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                           PRIMARY KEY (id),
                                           KEY idx_provider_id (provider_id),
                                           KEY idx_package_type_id (package_type_id),
                                           KEY idx_provider_status_sort (provider_id, status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI中转服务商套餐';

CREATE TABLE ai_relay_provider_package_limit (
                                                 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '套餐限制ID',
                                                 package_id BIGINT NOT NULL COMMENT '套餐ID（ai_relay_provider_package.id）',
                                                 limit_type TINYINT NOT NULL COMMENT '限制类型（1总额度 2每日额度 3每周额度 4每月额度 5单次额度）',
                                                 quota_amount DECIMAL(18,6) NOT NULL COMMENT '额度数量',
                                                 quota_unit VARCHAR(50) NOT NULL COMMENT '额度单位（token/request/credit等）',
                                                 reset_cycle TINYINT NOT NULL DEFAULT 0 COMMENT '重置周期（0不重置 1每日 2每周 3每月 4套餐周期）',
                                                 over_limit_strategy TINYINT NOT NULL DEFAULT 1 COMMENT '超限策略（1禁止使用 2按量计费 3限速）',
                                                 description VARCHAR(500) DEFAULT NULL COMMENT '限制说明',
                                                 status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
                                                 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                 PRIMARY KEY (id),
                                                 KEY idx_package_id (package_id),
                                                 KEY idx_package_limit_type (package_id, limit_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI中转服务商套餐额度限制';

CREATE TABLE ai_relay_model (
                                id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模型ID',
                                code VARCHAR(100) NOT NULL COMMENT '模型编码（如 gpt-4o-mini / claude-3-5-sonnet）',
                                name VARCHAR(100) NOT NULL COMMENT '模型名称',
                                model_vendor VARCHAR(50) DEFAULT NULL COMMENT '模型厂商（OpenAI/Anthropic/Google等）',
                                model_type TINYINT NOT NULL DEFAULT 1 COMMENT '模型类型（1文本 2图像 3音频 4多模态 5Embedding）',
                                description VARCHAR(1000) DEFAULT NULL COMMENT '模型说明',
                                sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
                                status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
                                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (id),
                                UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置';

CREATE TABLE ai_relay_package_model (
                                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '套餐模型ID',
                                        package_id BIGINT NOT NULL COMMENT '套餐ID（ai_relay_provider_package.id）',
                                        model_id BIGINT NOT NULL COMMENT '模型ID（ai_relay_model.id）',
                                        provider_model_code VARCHAR(150) DEFAULT NULL COMMENT '服务商侧模型编码，不填则默认使用模型编码',
                                        consume_multiplier DECIMAL(8,4) NOT NULL DEFAULT 1.0000 COMMENT '消耗倍率，如1.5表示消耗额度*1.5',
                                        min_charge_amount DECIMAL(18,6) DEFAULT NULL COMMENT '最低扣费额度',
                                        max_context_tokens INT DEFAULT NULL COMMENT '最大上下文Token数',
                                        is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认模型（1是 0否）',
                                        sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
                                        status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
                                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                        PRIMARY KEY (id),
                                        UNIQUE KEY uk_package_model (package_id, model_id),
                                        KEY idx_package_id (package_id),
                                        KEY idx_model_id (model_id),
                                        KEY idx_package_status_sort (package_id, status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI中转套餐支持模型及消耗倍率';

CREATE TABLE ai_relay_payment_method (
                                         id BIGINT NOT NULL AUTO_INCREMENT COMMENT '支付方式ID',
                                         code VARCHAR(50) NOT NULL COMMENT '支付方式编码（alipay/wechat/paypal/usdt等）',
                                         name VARCHAR(50) NOT NULL COMMENT '支付方式名称（支付宝/微信/PayPal/USDT等）',
                                         icon_file_id BIGINT DEFAULT NULL COMMENT '支付方式图标文件ID（sys_file）',
                                         description VARCHAR(500) DEFAULT NULL COMMENT '支付方式说明',
                                         sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
                                         status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
                                         create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                         update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                         PRIMARY KEY (id),
                                         UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI中转支付方式配置';

CREATE TABLE ai_relay_provider_payment_method (
                                                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                                  provider_id BIGINT NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
                                                  payment_method_id BIGINT NOT NULL COMMENT '支付方式ID（ai_relay_payment_method.id）',
                                                  remark VARCHAR(500) DEFAULT NULL COMMENT '备注说明',
                                                  sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
                                                  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
                                                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                  PRIMARY KEY (id),
                                                  UNIQUE KEY uk_provider_payment (provider_id, payment_method_id),
                                                  KEY idx_provider_id (provider_id),
                                                  KEY idx_payment_method_id (payment_method_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI中转服务商支持支付方式';

CREATE TABLE ai_relay_provider_advantage (
                                             id BIGINT NOT NULL AUTO_INCREMENT COMMENT '优势ID',
                                             provider_id BIGINT NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
                                             title VARCHAR(100) NOT NULL COMMENT '优势标题',
                                             content VARCHAR(500) DEFAULT NULL COMMENT '优势说明',
                                             advantage_type TINYINT NOT NULL DEFAULT 1 COMMENT '优势类型（1普通优势 2核心优势 3风险提示）',
                                             icon_file_id BIGINT DEFAULT NULL COMMENT '优势图标文件ID（sys_file）',
                                             sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
                                             status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
                                             create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                             update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                             PRIMARY KEY (id),
                                             KEY idx_provider_id (provider_id),
                                             KEY idx_provider_status_sort (provider_id, status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI中转服务商优势';

INSERT INTO ai_relay_package_type
(code, name, billing_mode, duration_value, duration_unit, description, sort_order)
VALUES
    ('day', '天卡', 1, 1, 1, '按天购买的固定周期套餐', 10),
    ('week', '周卡', 1, 1, 2, '按周购买的固定周期套餐', 20),
    ('month', '月卡', 1, 1, 3, '按月购买的固定周期套餐', 30),
    ('usage', '按量', 2, NULL, NULL, '按照实际用量计费', 40);

INSERT INTO ai_relay_payment_method
(code, name, sort_order)
VALUES
    ('alipay', '支付宝', 10),
    ('wechat', '微信支付', 20),
    ('bank_card', '银行卡', 30),
    ('paypal', 'PayPal', 40),
    ('usdt', 'USDT', 50);