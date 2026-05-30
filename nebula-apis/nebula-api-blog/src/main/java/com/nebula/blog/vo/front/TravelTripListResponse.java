package com.nebula.blog.vo.front;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 游记列表响应VO（前台）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TravelTripListResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<TravelTripListVO> items;

    /**
     * 下一页游标
     */
    private String nextCursor;
}
