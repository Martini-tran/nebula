/*
 UID Generator - worker_node 表
 用于 nebula-sdk-uid-generator 模块的 DisposableWorkerIdAssigner，
 每次实例启动会插入一条记录，自增 ID 即作为该实例的 workerId。
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for worker_node
-- ----------------------------
DROP TABLE IF EXISTS `worker_node`;
CREATE TABLE `worker_node`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键，作为 workerId',
  `host_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'host：ACTUAL=IP，CONTAINER=hostname',
  `port` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'port：ACTUAL=时间戳-随机数，CONTAINER=容器端口',
  `type` int NOT NULL COMMENT '节点类型：1=CONTAINER 2=ACTUAL',
  `launch_date` date NOT NULL COMMENT '启动日期',
  `modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'UID Generator workerId 分配表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
