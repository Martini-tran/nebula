package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.entity.TravelDestination;
import com.nebula.blog.mapper.TravelDestinationMapper;
import com.nebula.blog.service.TravelDestinationFrontService;
import com.nebula.blog.vo.front.TravelDestinationSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 旅游目的地前台服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelDestinationFrontServiceImpl implements TravelDestinationFrontService {

    private static final int DEFAULT_CHIPS_LIMIT = 30;
    private static final int MAX_CHIPS_LIMIT = 100;

    private final TravelDestinationMapper destinationMapper;

    @Override
    public List<TravelDestinationSummaryVO> listChips(int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_CHIPS_LIMIT : Math.min(limit, MAX_CHIPS_LIMIT);

        List<TravelDestination> rows = destinationMapper.selectList(
                new LambdaQueryWrapper<TravelDestination>()
                        .eq(TravelDestination::getStatus, 1)
                        .orderByAsc(TravelDestination::getSortOrder)
                        .orderByDesc(TravelDestination::getVisitCount)
                        .last("LIMIT " + safeLimit));

        return rows.stream().map(d -> {
            TravelDestinationSummaryVO vo = new TravelDestinationSummaryVO();
            vo.setId(d.getId());
            vo.setName(d.getName());
            vo.setSlug(d.getSlug());
            vo.setType(d.getType());
            return vo;
        }).collect(Collectors.toList());
    }
}
