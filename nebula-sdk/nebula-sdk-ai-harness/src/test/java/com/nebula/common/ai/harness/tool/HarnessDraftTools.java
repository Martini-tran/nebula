package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.harness.draft.DraftApplicationService;

import java.util.List;

/**
 * 测试用：一次性装配全部草稿工具。
 *
 * <p>放在 tool 包内是因为各 {@code *ToolDefinition} 与其基类均为包私有；
 * 生产环境由 {@code HarnessAutoConfiguration} 逐个注册为 Bean，此处只为端到端测试
 * 提供与之等价的工具集合，避免测试重复 new 十几个类。
 *
 * @author nebula
 */
public final class HarnessDraftTools {

    private HarnessDraftTools() {
    }

    /**
     * 构造与生产装配等价的草稿工具集合。
     *
     * @param service 草稿应用服务
     * @return 工具列表
     */
    public static List<ToolDefinition> all(DraftApplicationService service) {
        return List.of(
                new CreateDraftToolDefinition(service),
                new UpdateDraftMetadataToolDefinition(service),
                new AddNodeToolDefinition(service),
                new UpdateNodeToolDefinition(service),
                new RemoveNodeToolDefinition(service),
                new ConnectToolDefinition(service),
                new DisconnectToolDefinition(service),
                new ReadDraftToolDefinition(service),
                new InspectContextToolDefinition(service),
                new ValidateDraftToolDefinition(service),
                new SimulateDraftToolDefinition(service),
                new CommitDraftToolDefinition(service));
    }
}
