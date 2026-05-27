package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.admin.TravelTripDayCreateRequest;
import com.nebula.blog.dto.admin.TravelTripDayUpdateRequest;
import com.nebula.blog.entity.TravelCheckin;
import com.nebula.blog.entity.TravelTrip;
import com.nebula.blog.entity.TravelTripDay;
import com.nebula.blog.mapper.TravelCheckinMapper;
import com.nebula.blog.mapper.TravelTripDayMapper;
import com.nebula.blog.mapper.TravelTripMapper;
import com.nebula.blog.service.TravelTripDayAdminService;
import com.nebula.blog.vo.admin.TravelTripDayAdminVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 行程日管理服务实现（管理员端）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelTripDayAdminServiceImpl implements TravelTripDayAdminService {

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_LENGTH = 2000;
    private static final int ACCOMMODATION_MAX_LENGTH = 200;

    private final TravelTripMapper tripMapper;
    private final TravelTripDayMapper tripDayMapper;
    private final TravelCheckinMapper checkinMapper;

    @Override
    public List<TravelTripDayAdminVO> listByTrip(Long tripId) {
        requireTrip(tripId);
        return tripDayMapper.selectList(
                        new LambdaQueryWrapper<TravelTripDay>()
                                .eq(TravelTripDay::getTripId, tripId)
                                .orderByAsc(TravelTripDay::getDayNumber)
                                .orderByAsc(TravelTripDay::getSortOrder)
                                .orderByAsc(TravelTripDay::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    public TravelTripDayAdminVO detail(Long id) {
        return toVO(requireDay(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TravelTripDayCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (req.getTripId() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "tripId 不能为空");
        }
        requireTrip(req.getTripId());
        if (req.getDayNumber() == null || req.getDayNumber() < 1) {
            throw new BizException(HttpStatus.BAD_REQUEST, "dayNumber 必须 >= 1");
        }
        checkDayNumberUnique(req.getTripId(), req.getDayNumber(), null);
        checkLength(req.getTitle(), TITLE_MAX_LENGTH, "title");
        checkLength(req.getDescription(), DESCRIPTION_MAX_LENGTH, "description");
        checkLength(req.getAccommodation(), ACCOMMODATION_MAX_LENGTH, "accommodation");

        TravelTripDay day = new TravelTripDay();
        day.setTripId(req.getTripId());
        day.setDayNumber(req.getDayNumber());
        day.setTitle(req.getTitle());
        day.setDescription(req.getDescription());
        day.setAccommodation(req.getAccommodation());
        day.setMealCost(req.getMealCost());
        day.setTransportCost(req.getTransportCost());
        day.setOtherCost(req.getOtherCost());
        day.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        tripDayMapper.insert(day);
        return day.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TravelTripDayUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        TravelTripDay existing = requireDay(id);

        if (req.getDayNumber() != null && !req.getDayNumber().equals(existing.getDayNumber())) {
            if (req.getDayNumber() < 1) {
                throw new BizException(HttpStatus.BAD_REQUEST, "dayNumber 必须 >= 1");
            }
            checkDayNumberUnique(existing.getTripId(), req.getDayNumber(), id);
            existing.setDayNumber(req.getDayNumber());
        }
        if (StringUtils.hasText(req.getTitle())) {
            checkLength(req.getTitle(), TITLE_MAX_LENGTH, "title");
            existing.setTitle(req.getTitle());
        }
        if (req.getDescription() != null) {
            checkLength(req.getDescription(), DESCRIPTION_MAX_LENGTH, "description");
            existing.setDescription(req.getDescription());
        }
        if (req.getAccommodation() != null) {
            checkLength(req.getAccommodation(), ACCOMMODATION_MAX_LENGTH, "accommodation");
            existing.setAccommodation(req.getAccommodation());
        }
        if (req.getMealCost() != null) existing.setMealCost(req.getMealCost());
        if (req.getTransportCost() != null) existing.setTransportCost(req.getTransportCost());
        if (req.getOtherCost() != null) existing.setOtherCost(req.getOtherCost());
        if (req.getSortOrder() != null) existing.setSortOrder(req.getSortOrder());

        tripDayMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TravelTripDay day = requireDay(id);
        checkinMapper.delete(new LambdaQueryWrapper<TravelCheckin>()
                .eq(TravelCheckin::getTripDayId, day.getId()));
        tripDayMapper.deleteById(id);
    }

    // -------------------- 工具 --------------------

    private void requireTrip(Long tripId) {
        if (tripId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "tripId 不能为空");
        }
        TravelTrip trip = tripMapper.selectById(tripId);
        if (trip == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "游记不存在");
        }
    }

    private TravelTripDay requireDay(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "行程日ID不能为空");
        }
        TravelTripDay day = tripDayMapper.selectById(id);
        if (day == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "行程日不存在");
        }
        return day;
    }

    private void checkDayNumberUnique(Long tripId, Integer dayNumber, Long excludeId) {
        LambdaQueryWrapper<TravelTripDay> wrapper = new LambdaQueryWrapper<TravelTripDay>()
                .eq(TravelTripDay::getTripId, tripId)
                .eq(TravelTripDay::getDayNumber, dayNumber);
        if (excludeId != null) {
            wrapper.ne(TravelTripDay::getId, excludeId);
        }
        if (tripDayMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "该 tripId 下 dayNumber 已存在");
        }
    }

    private void checkLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 长度不能超过 " + maxLength);
        }
    }

    private TravelTripDayAdminVO toVO(TravelTripDay d) {
        TravelTripDayAdminVO vo = new TravelTripDayAdminVO();
        vo.setId(d.getId());
        vo.setTripId(d.getTripId());
        vo.setDayNumber(d.getDayNumber());
        vo.setTitle(d.getTitle());
        vo.setDescription(d.getDescription());
        vo.setAccommodation(d.getAccommodation());
        vo.setMealCost(d.getMealCost());
        vo.setTransportCost(d.getTransportCost());
        vo.setOtherCost(d.getOtherCost());
        vo.setSortOrder(d.getSortOrder());
        vo.setCreateTime(d.getCreateTime());
        vo.setUpdateTime(d.getUpdateTime());
        return vo;
    }
}
