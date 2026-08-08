package com.nebula.common.ai.harness.draft;

import java.util.List;

/**
 * 双引擎建图约束的权威文本。
 *
 * @author nebula
 */
public final class EngineTypeCatalog {

    public static final String DAG = "DAG";
    public static final String STATE_MACHINE = "STATE_MACHINE";

    private EngineTypeCatalog() {
    }

    public static boolean supported(String engineType) {
        return DAG.equals(engineType) || STATE_MACHINE.equals(engineType);
    }

    public static List<String> constraints(String engineType) {
        if (STATE_MACHINE.equals(engineType)) {
            return List.of(
                    "本图用 stateType 标注节点角色，不使用 START/END 节点",
                    "必须恰好一个 ENTRY 态作为起步",
                    "必须至少一个 TERMINAL 态且无出边",
                    "允许成环（回跳/重试），但须保证 TERMINAL 可达",
                    "分支靠出边 conditionExpr(guard) + sortNo 裁决顺序，建议留一条无条件 default 边");
        }
        return List.of(
                "本图使用 START/END 结构节点，不设置 stateType",
                "START 和 END 必须各恰好一个",
                "图必须无环，所有可执行节点应从 START 可达并最终到达 END",
                "条件分支使用 IF 节点及出边 conditionExpr，建议保留一条无条件 default 边",
                "并行分支在 JOIN 节点汇聚，循环使用 LOOP 容器节点");
    }
}
