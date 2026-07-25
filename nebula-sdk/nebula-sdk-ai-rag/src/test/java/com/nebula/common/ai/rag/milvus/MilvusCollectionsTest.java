package com.nebula.common.ai.rag.milvus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MilvusCollections} 物理名生成测试。
 *
 * @author nebula
 */
class MilvusCollectionsTest {

    @Test
    void 物理名嵌入维度与版本() {
        String physical = MilvusCollections.physicalName(MilvusCollections.KB, "nebula_", 1536);
        assertEquals("nebula_kb__d1536__v1", physical);
    }

    @Test
    void 默认前缀不重复拼接() {
        // 逻辑名已含 nebula_ 前缀，默认前缀不应再拼一次
        String physical = MilvusCollections.physicalName(MilvusCollections.MEMORY, "nebula_", 768);
        assertEquals("nebula_memory__d768__v1", physical);
    }

    @Test
    void 非默认前缀参与拼接() {
        String physical = MilvusCollections.physicalName(MilvusCollections.TOOL_CATALOG, "acme_", 1024);
        assertTrue(physical.startsWith("acme_nebula_tool_catalog"));
        assertTrue(physical.endsWith("__d1024__v1"));
    }

    @Test
    void 四场景逻辑名齐备() {
        assertEquals(4, MilvusCollections.ALL.size());
        assertTrue(MilvusCollections.ALL.contains(MilvusCollections.KB));
        assertTrue(MilvusCollections.ALL.contains(MilvusCollections.FLOW_EXAMPLE));
        assertTrue(MilvusCollections.ALL.contains(MilvusCollections.MEMORY));
        assertTrue(MilvusCollections.ALL.contains(MilvusCollections.TOOL_CATALOG));
    }
}
