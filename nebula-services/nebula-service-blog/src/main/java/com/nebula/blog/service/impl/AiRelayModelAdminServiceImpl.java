package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.blog.dto.admin.AiRelayModelPageQuery;
import com.nebula.blog.dto.admin.AiRelayModelRequest;
import com.nebula.blog.entity.AiRelayModel;
import com.nebula.blog.entity.AiRelayPackageModel;
import com.nebula.blog.mapper.AiRelayModelMapper;
import com.nebula.blog.mapper.AiRelayPackageModelMapper;
import com.nebula.blog.service.AiRelayModelAdminService;
import com.nebula.blog.vo.admin.AiRelayModelAdminVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI模型管理服务实现（管理员端）
 */
@Service
@RequiredArgsConstructor
public class AiRelayModelAdminServiceImpl implements AiRelayModelAdminService {

    private final AiRelayModelMapper modelMapper;
    private final AiRelayPackageModelMapper packageModelMapper;

    @Override
    public PageResult<AiRelayModelAdminVO> page(AiRelayModelPageQuery query) {
        AiRelayModelPageQuery safe = query == null ? new AiRelayModelPageQuery() : query;
        Page<AiRelayModel> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiRelayModel> wrapper = new LambdaQueryWrapper<AiRelayModel>()
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiRelayModel::getCode, safe.getKeyword())
                        .or()
                        .like(AiRelayModel::getName, safe.getKeyword()))
                .eq(StringUtils.hasText(safe.getModelVendor()), AiRelayModel::getModelVendor, safe.getModelVendor())
                .eq(safe.getModelType() != null, AiRelayModel::getModelType, safe.getModelType())
                .eq(safe.getStatus() != null, AiRelayModel::getStatus, safe.getStatus())
                .orderByAsc(AiRelayModel::getSortOrder)
                .orderByDesc(AiRelayModel::getCreateTime);
        Page<AiRelayModel> result = modelMapper.selectPage(page, wrapper);
        List<AiRelayModelAdminVO> rows = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AiRelayModelAdminVO detail(Long id) {
        return toVO(requireModel(id));
    }

    @Override
    public Long create(AiRelayModelRequest req) {
        validateRequest(req);
        checkCodeUnique(req.getCode(), null);

        AiRelayModel entity = new AiRelayModel();
        applyRequest(entity, req);
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        modelMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, AiRelayModelRequest req) {
        validateRequest(req);
        AiRelayModel existing = requireModel(id);
        if (!req.getCode().equals(existing.getCode())) {
            checkCodeUnique(req.getCode(), id);
        }
        applyRequest(existing, req);
        modelMapper.updateById(existing);
    }

    @Override
    public void delete(Long id) {
        requireModel(id);
        long refCount = packageModelMapper.selectCount(
                new LambdaQueryWrapper<AiRelayPackageModel>().eq(AiRelayPackageModel::getModelId, id)
        );
        if (refCount > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "存在套餐已引用该模型，无法删除");
        }
        modelMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态不能为空");
        }
        AiRelayModel existing = requireModel(id);
        existing.setStatus(status);
        modelMapper.updateById(existing);
    }

    private void validateRequest(AiRelayModelRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(req.getCode())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "模型编码不能为空");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "模型名称不能为空");
        }
        if (req.getModelType() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "模型类型不能为空");
        }
    }

    private AiRelayModel requireModel(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "模型ID不能为空");
        }
        AiRelayModel entity = modelMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "模型不存在");
        }
        return entity;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<AiRelayModel> wrapper = new LambdaQueryWrapper<AiRelayModel>()
                .eq(AiRelayModel::getCode, code);
        if (excludeId != null) {
            wrapper.ne(AiRelayModel::getId, excludeId);
        }
        if (modelMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "模型编码已存在");
        }
    }

    private void applyRequest(AiRelayModel entity, AiRelayModelRequest req) {
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setModelVendor(req.getModelVendor());
        entity.setModelType(req.getModelType());
        entity.setDescription(req.getDescription());
        if (req.getSortOrder() != null) {
            entity.setSortOrder(req.getSortOrder());
        }
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
    }

    private AiRelayModelAdminVO toVO(AiRelayModel entity) {
        AiRelayModelAdminVO vo = new AiRelayModelAdminVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setModelVendor(entity.getModelVendor());
        vo.setModelType(entity.getModelType());
        vo.setDescription(entity.getDescription());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
