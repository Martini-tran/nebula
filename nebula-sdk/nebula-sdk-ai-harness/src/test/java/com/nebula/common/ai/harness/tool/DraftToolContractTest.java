package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftToolContractTest {

    @Test
    void exposesSeparateMutationToolsWithoutIdentityParameters() {
        List<ToolDefinition> tools = List.of(
                new CreateDraftToolDefinition(null),
                new UpdateDraftMetadataToolDefinition(null),
                new AddNodeToolDefinition(null),
                new UpdateNodeToolDefinition(null),
                new RemoveNodeToolDefinition(null),
                new ConnectToolDefinition(null),
                new DisconnectToolDefinition(null),
                new ReadDraftToolDefinition(null),
                new ValidateDraftToolDefinition(null),
                new CommitDraftToolDefinition(null));

        assertEquals(Set.of("create_draft", "update_draft_metadata", "add_node", "update_node",
                        "remove_node", "connect", "disconnect", "read_draft", "validate_draft", "commit_draft"),
                tools.stream().map(ToolDefinition::code).collect(Collectors.toSet()));
        for (ToolDefinition tool : tools) {
            Map<?, ?> properties = (Map<?, ?>) tool.paramsSchema().get("properties");
            assertFalse(properties.containsKey("userId"), tool.code());
            assertFalse(properties.containsKey("sessionId"), tool.code());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsDagAndStateMachineAsSeparateAuthorities() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        ListNodeTypesToolDefinition tool = new ListNodeTypesToolDefinition(
                beans.getBeanProvider(FlowNodeExecutor.class));

        Map<String, Object> result = (Map<String, Object>) tool.invoke(Map.of(), null);
        Map<String, Object> engines = (Map<String, Object>) result.get("engines");
        Map<String, Object> stateMachine = (Map<String, Object>) engines.get("STATE_MACHINE");
        List<Map<String, Object>> roles = (List<Map<String, Object>>) stateMachine.get("graphRoles");

        assertTrue((Boolean) result.get("ok"));
        assertEquals(Set.of("DAG", "STATE_MACHINE"), engines.keySet());
        assertEquals(Set.of("ENTRY", "NORMAL", "TERMINAL"),
                roles.stream().map(role -> String.valueOf(role.get("type"))).collect(Collectors.toSet()));
    }
}
