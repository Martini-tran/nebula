package com.nebula.space.service;

import com.nebula.space.dto.me.ProfileSaveRequest;
import com.nebula.space.vo.me.HandleCheckVO;
import com.nebula.space.vo.me.ProfilePublicVO;
import com.nebula.space.vo.me.ProfileVO;

/**
 * 公开主页服务：个人空间唯一的对外出口，默认关闭，逐块决定公开什么
 *
 * <p>随手记、任务、会议、周报、记账、人物卡、习惯明细永远不会出现在公开页。</p>
 */
public interface SpaceProfileService {

    /**
     * 当前用户的设置；从没保存过返回默认值（关闭、短名取自账号）
     */
    ProfileVO get();

    /**
     * 整份覆盖保存
     */
    ProfileVO save(ProfileSaveRequest req);

    /**
     * 短名是否可用：格式、保留词、有没有被别人占用
     */
    HandleCheckVO checkHandle(String handle);

    /**
     * 按还没保存的草稿拼一份访客页，和访客真正看到的同一套逻辑；总开关关着也能预览
     */
    ProfilePublicVO preview(ProfileSaveRequest req);

    /**
     * 访客打开 /@handle；没开或不存在按 404。每打开一次记一次访问
     */
    ProfilePublicVO publicPage(String handle);

    /**
     * 访客展开了一个合集
     */
    void collectionViewed(String handle, String collectionId);

    /**
     * 访客把一个合集导入了自己的书签
     */
    void collectionImported(String handle, String collectionId);
}
