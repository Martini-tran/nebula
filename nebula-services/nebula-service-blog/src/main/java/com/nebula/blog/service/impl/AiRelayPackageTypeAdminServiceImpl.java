package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.admin.AiRelayPackageTypeRequest;
import com.nebula.blog.entity.AiRelayPackageType;
import com.nebula.blog.entity.AiRelayProviderPackage;
import com.nebula.blog.mapper.AiRelayPackageTypeMapper;
import com.nebula.blog.mapper.AiRelayProviderPackageMapper;
import com.nebula.blog.service.AiRelayPackageTypeAdminService;
import com.nebula.blog.vo.admin.AiRelayPackageTypeAdminVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI中转套餐类型 管理服务实现（字典）
 */
@Service
@RequiredArgsConstructor
public class AiRelayPackageTypeAdminServiceImpl implements AiRelayPackageTypeAdminService {

    private final AiRelayPackageTypeMapper packageTypeMapper;
    private final AiRelayProviderPackageMapper providerPackageMapper;

    @Override
    public List<AiRelayPackageTypeAdminVO> list(Integer status) {
        LambdaQueryWrapper<AiRelayPackageType> wrapper = new LambdaQueryWrapper<AiRelayPackageType>()
                .eq(status != null, AiRelayPackageType::getStatus, status)
                .orderByAsc(AiRelayPackageType::getSortOrder)
                .orderByDesc(AiRelayPackageType::getCreateTime);
        return packageTypeMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public AiRelayPackageTypeAdminVO detail(Long id) {
        return toVO(requirePackageType(id));
    }

    @Override
    public Long create(AiRelayPackageTypeRequest req) {
        validateRequest(req);
        checkCodeUnique(req.getCode(), null);

        AiRelayPackageType entity = new AiRelayPackageType();
        applyRequest(entity, req);
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        packageTypeMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, AiRelayPackageTypeRequest req) {
        validateRequest(req);
        AiRelayPackageType existing = requirePackageType(id);
        if (!req.getCode().equals(existing.getCode())) {
            checkCodeUnique(req.getCode(), id);
        }
        applyRequest(existing, req);
        packageTypeMapper.updateById(existing);
    }

    @Override
    public void delete(Long id) {
        requirePackageType(id);
        long refCount = providerPackageMapper.selectCount(
                new LambdaQueryWrapper<AiRelayProviderPackage>().eq(AiRelayProviderPackage::getPackageTypeId, id)
        );
        if (refCount > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "存在套餐引用该类型，无法删除");
        }
        packageTypeMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态不能为空");
        }
        AiRelayPackageType existing = requirePackageType(id);
        existing.setStatus(status);
        packageTypeMapper.updateById(existing);
    }

    private void validateRequest(AiRelayPackageTypeRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(req.getCode())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐类型编码不能为空");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐类型名称不能为空");
        }
        if (req.getBillingMode() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "计费模式不能为空");
        }
    }

    private AiRelayPackageType requirePackageType(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐类型ID不能为空");
        }
        AiRelayPackageType entity = packageTypeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "套餐类型不存在");
        }
        return entity;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<AiRelayPackageType> wrapper = new LambdaQueryWrapper<AiRelayPackageType>()
                .eq(AiRelayPackageType::getCode, code);
        if (excludeId != null) {
            wrapper.ne(AiRelayPackageType::getId, excludeId);
        }
        if (packageTypeMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐类型编码已存在");
        }
    }

    private void applyRequest(AiRelayPackageType entity, AiRelayPackageTypeRequest req) {
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setBillingMode(req.getBillingMode());
        entity.setDurationValue(req.getDurationValue());
        entity.setDurationUnit(req.getDurationUnit());
        entity.setDescription(req.getDescription());
        if (req.getSortOrder() != null) {
            entity.setSortOrder(req.getSortOrder());
        }
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
    }

    private AiRelayPackageTypeAdminVO toVO(AiRelayPackageType entity) {
        AiRelayPackageTypeAdminVO vo = new AiRelayPackageTypeAdminVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setBillingMode(entity.getBillingMode());
        vo.setDurationValue(entity.getDurationValue());
        vo.setDurationUnit(entity.getDurationUnit());
        vo.setDescription(entity.getDescription());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
