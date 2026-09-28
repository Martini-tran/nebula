package com.nebula.space.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 目录结构 AI 重排方案：按顺序执行的操作列表，已按当前目录树逐条校验过
 *
 * <p>操作里引用目录的字段（folder / parent / into）取值：已有目录为其 ID，本方案新建的目录为 N1、N2… 编号，
 * 顶层为 "0"。前端按顺序执行，新建目录拿到 ID 后替换后续操作里的编号。</p>
 */
@Data
public class FolderAiPlanVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 方案概述
     */
    private String summary;

    /**
     * 操作列表
     */
    private List<Op> ops = new ArrayList<>();

    /**
     * AI 给出但校验不通过、已丢弃的操作数
     */
    private Integer dropped;

    @Data
    public static class Op implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * create 新建 / rename 改名 / move 移动 / merge 合并（书签与子目录并入 into 后删除 folder）
         */
        private String op;

        /**
         * create：新目录的编号
         */
        private String key;

        /**
         * rename / move / merge：被操作的目录
         */
        private String folder;

        /**
         * create / move：新的上级目录
         */
        private String parent;

        /**
         * merge：并入的目录
         */
        private String into;

        /**
         * create / rename：名称
         */
        private String name;

        /**
         * AI 给的理由
         */
        private String reason;

        /**
         * 操作前的路径（展示用），create 为空
         */
        private String before;

        /**
         * 操作后的路径（展示用）；merge 为并入目录的路径
         */
        private String after;

        /**
         * merge：被并走的书签数（只算直接放在该目录里的）
         */
        private Integer bookmarkCount;
    }
}
