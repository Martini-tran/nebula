package com.nebula.space.bookmark;

import com.nebula.space.entity.SpaceBookmarkFolder;
import com.nebula.space.vo.admin.FolderAiPlanVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FolderPlannerTest {

    /*
     * F1 前端(10)            id 1
     *   F2 Vue(3)            id 11
     *   F3 构建(2)           id 12
     *     F4 Webpack(1)      id 121
     * F5 后端(5)             id 2
     *   F6 构建(4)           id 21
     */
    private FolderPlanner planner;

    private static SpaceBookmarkFolder folder(long id, long parentId, String name, int sort) {
        SpaceBookmarkFolder f = new SpaceBookmarkFolder();
        f.setId(id);
        f.setParentId(parentId);
        f.setName(name);
        f.setSortOrder(sort);
        return f;
    }

    private static FolderPlanner.RawOp op(String op, String key, String folder, String parent, String into, String name) {
        return new FolderPlanner.RawOp(op, key, folder, parent, into, name, "理由");
    }

    @BeforeEach
    void setUp() {
        planner = new FolderPlanner(
                List.of(folder(2, 0, "后端", 1), folder(12, 1, "构建", 1), folder(1, 0, "前端", 0),
                        folder(11, 1, "Vue", 0), folder(121, 12, "Webpack", 0), folder(21, 2, "构建", 0)),
                Map.of(1L, 10, 11L, 3, 12L, 2, 121L, 1, 2L, 5, 21L, 4),
                Map.of(11L, List.of("Vue.js 官方文档", "Pinia")));
    }

    @Test
    void describesTreeInOrderWithKeys() {
        assertEquals(List.of(
                "F1 | 前端 | 10 条",
                "F2 | 前端 / Vue | 3 条 | Vue.js 官方文档；Pinia",
                "F3 | 前端 / 构建 | 2 条",
                "F4 | 前端 / 构建 / Webpack | 1 条",
                "F5 | 后端 | 5 条",
                "F6 | 后端 / 构建 | 4 条"), planner.describe());
    }

    @Test
    void createThenMoveIntoNewFolderUsesKeys() {
        FolderAiPlanVO.Op create = planner.apply(op("create", "n1", null, "F1", null, " 工程化 "));
        assertNotNull(create);
        assertEquals("N1", create.getKey());
        assertEquals("1", create.getParent());
        assertEquals("前端 / 工程化", create.getAfter());

        FolderAiPlanVO.Op move = planner.apply(op("move", null, "F3", "N1", null, null));
        assertNotNull(move);
        assertEquals("12", move.getFolder());
        assertEquals("N1", move.getParent());
        assertEquals("前端 / 构建", move.getBefore());
        assertEquals("前端 / 工程化 / 构建", move.getAfter());

        // 编号不能重复使用
        assertNull(planner.apply(op("create", "N1", null, "", null, "另一个")));
    }

    @Test
    void rejectsInvalidOperations() {
        // 引用不存在的目录
        assertNull(planner.apply(op("rename", null, "F99", null, null, "新名")));
        // 同级重名（不分大小写）
        assertNull(planner.apply(op("rename", null, "F2", null, null, "构建")));
        assertNull(planner.apply(op("create", "N1", null, "F1", null, "vue")));
        // 名称带斜杠、为空
        assertNull(planner.apply(op("rename", null, "F2", null, null, "Vue/React")));
        assertNull(planner.apply(op("create", "N2", null, "", null, "  ")));
        // 移到自己的子目录下、移到原处、漏写 parent
        assertNull(planner.apply(op("move", null, "F3", "F4", null, null)));
        assertNull(planner.apply(op("move", null, "F2", "F1", null, null)));
        assertNull(planner.apply(op("move", null, "F2", null, null, null)));
        // 移到的地方已有同名目录
        assertNull(planner.apply(op("move", null, "F6", "F1", null, null)));
        // 合并进自己的子目录
        assertNull(planner.apply(op("merge", null, "F3", null, "F4", null)));
        // 编号格式不对、未知操作
        assertNull(planner.apply(op("create", "X1", null, "", null, "新")));
        assertNull(planner.apply(op("delete", null, "F2", null, null, null)));
    }

    @Test
    void mergeMovesChildrenAndBookmarks() {
        // F3「前端/构建」并入 F6「后端/构建」：子目录 Webpack 跟过去
        FolderAiPlanVO.Op merge = planner.apply(op("merge", null, "F3", null, "F6", null));
        assertNotNull(merge);
        assertEquals("12", merge.getFolder());
        assertEquals("21", merge.getInto());
        assertEquals(2, merge.getBookmarkCount());
        assertEquals("后端 / 构建", merge.getAfter());

        // 被合并的目录不能再被引用
        assertNull(planner.apply(op("rename", null, "F3", null, null, "随便")));
        // Webpack 现在在「后端 / 构建」下
        FolderAiPlanVO.Op rename = planner.apply(op("rename", null, "F4", null, null, "Webpack 4"));
        assertNotNull(rename);
        assertEquals("后端 / 构建 / Webpack", rename.getBefore());
    }

    @Test
    void mergeRefusedWhenChildNamesClash() {
        assertNotNull(planner.apply(op("create", "N1", null, "F6", null, "webpack")));
        // 「后端 / 构建」下已经有 webpack，F3 的子目录 Webpack 并不过去
        assertNull(planner.apply(op("merge", null, "F3", null, "F6", null)));
    }

    @Test
    void moveToTopLevelWithEmptyParent() {
        FolderAiPlanVO.Op move = planner.apply(op("move", null, "F2", "", null, null));
        assertNotNull(move);
        assertEquals("0", move.getParent());
        assertEquals("Vue", move.getAfter());
    }
}
