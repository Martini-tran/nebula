package com.nebula.space.dto.me;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 公开主页设置保存 / 预览请求：整份覆盖
 *
 * <p>前端把整份设置发回来，其中 nowUpdated 与 stats 由服务端维护，收到也不用。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileSaveRequest {

    private Boolean enabled;

    /**
     * 3-20 位小写字母、数字、- 和 _；规则在服务里校验，预览时不拦
     */
    @Size(max = 20, message = "短名最长 20 位")
    private String handle;

    @Size(max = 120, message = "介绍最长 120 字")
    private String bio;

    @Valid
    @Size(max = 10, message = "链接最多 10 个")
    private List<ProfileLink> links;

    @Size(max = 2000, message = "Now 最长 2000 字")
    private String now;

    @Size(max = 20, message = "区块数量不对")
    private List<ProfileBlock> blocks;

    @Valid
    @Size(max = 30, message = "书签合集最多公开 30 个")
    private List<ProfileCollection> collections;

    @Size(max = 200, message = "隐藏的文章最多 200 篇")
    private List<Long> hiddenReading;

    @Size(max = 50, message = "摘录最多挑 50 条")
    private List<Long> quoteIds;
}
