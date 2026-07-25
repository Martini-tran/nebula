package com.nebula.common.ai.rag.toolcatalog;

import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具目录索引器（{@link ApplicationRunner}，场景④）。
 *
 * <p>启动时把 {@link ToolRegistry} 中代码定义的全部工具向量化进 {@code nebula_tool_catalog}，使 {@code search_tools}
 * 可用。<b>索引源是内存注册表而非 {@code ai_tool} 表</b>：工具全集本就在 {@link ToolRegistry}（代码是唯一真相源），
 * 遍历它零跨模块依赖、零 DB 读，且与真相源天然一致；{@code ai_tool} 表只是给前端看的镜像，不作索引源。
 *
 * <p>排在 {@code ToolRegistrySynchronizer}（{@code @Order(0)}）之后（{@code @Order(1)}）——虽不强依赖其顺序（本索引器
 * 读注册表内存不读 {@code ai_tool} 表），但排后更符合「注册表已就绪 → 镜像 DB → 索引向量」的直觉。
 *
 * <p><b>可用性铁律</b>：索引失败仅 {@code log.warn} 不阻断启动（{@link ToolCatalogService#index} 内部已降级，向量库
 * 故障时 {@code search_tools} 检索返回空，Copilot 回退全量 {@code list_tools}）。
 *
 * <p><b>增量</b>：工具集在启动期由 Bean 扫描确定、运行期不变，故启动全量索引即可；无「运行期新增工具」增量诉求。
 *
 * @author nebula
 */
@Slf4j
@Order(1)
public class ToolCatalogIndexer implements ApplicationRunner {

    private final ToolCatalogService toolCatalogService;
    private final ToolRegistry toolRegistry;

    public ToolCatalogIndexer(ToolCatalogService toolCatalogService, ToolRegistry toolRegistry) {
        this.toolCatalogService = toolCatalogService;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<ToolDefinition> tools = new ArrayList<>(toolRegistry.all());
            int indexed = toolCatalogService.index(tools);
            log.info("工具目录向量索引完成：注册表 {} 个工具，索引 {} 个", tools.size(), indexed);
        } catch (Exception e) {
            log.warn("工具目录向量索引失败（不阻断启动，search_tools 将回退全量 list_tools）：{}", e.getMessage());
        }
    }
}
