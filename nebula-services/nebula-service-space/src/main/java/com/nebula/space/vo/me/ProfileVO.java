package com.nebula.space.vo.me;

import com.nebula.space.dto.me.ProfileBlock;
import com.nebula.space.dto.me.ProfileCollection;
import com.nebula.space.dto.me.ProfileLink;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 公开主页设置（自己看、自己改）
 */
@Data
public class ProfileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean enabled;

    private String handle;

    private String bio;

    private List<ProfileLink> links;

    private String now;

    private LocalDate nowUpdated;

    /**
     * 七个区块全在，按展示顺序
     */
    private List<ProfileBlock> blocks;

    private List<ProfileCollection> collections;

    private List<Long> hiddenReading;

    private List<Long> quoteIds;

    private Stats stats;

    /**
     * 计数用 int：全局把 Long 序列化成字符串，"0" 在前端是真值
     */
    @Data
    public static class Stats implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 近 30 天访问
         */
        private int visits;

        /**
         * 合集被展开的累计次数
         */
        private int collectionViews;

        /**
         * 合集被导入的累计次数
         */
        private int imports;
    }
}
