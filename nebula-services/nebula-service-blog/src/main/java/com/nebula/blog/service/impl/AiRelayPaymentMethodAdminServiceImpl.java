package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.admin.AiRelayPaymentMethodRequest;
import com.nebula.blog.entity.AiRelayPaymentMethod;
import com.nebula.blog.entity.AiRelayProviderPaymentMethod;
import com.nebula.blog.mapper.AiRelayPaymentMethodMapper;
import com.nebula.blog.mapper.AiRelayProviderPaymentMethodMapper;
import com.nebula.blog.service.AiRelayPaymentMethodAdminService;
import com.nebula.blog.vo.admin.AiRelayPaymentMethodAdminVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI中转支付方式 管理服务实现（字典）
 */
@Service
@RequiredArgsConstructor
public class AiRelayPaymentMethodAdminServiceImpl implements AiRelayPaymentMethodAdminService {

    private final AiRelayPaymentMethodMapper paymentMethodMapper;
    private final AiRelayProviderPaymentMethodMapper providerPaymentMethodMapper;

    @Override
    public List<AiRelayPaymentMethodAdminVO> list(Integer status) {
        LambdaQueryWrapper<AiRelayPaymentMethod> wrapper = new LambdaQueryWrapper<AiRelayPaymentMethod>()
                .eq(status != null, AiRelayPaymentMethod::getStatus, status)
                .orderByAsc(AiRelayPaymentMethod::getSortOrder)
                .orderByDesc(AiRelayPaymentMethod::getCreateTime);
        return paymentMethodMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public AiRelayPaymentMethodAdminVO detail(Long id) {
        return toVO(requirePaymentMethod(id));
    }

    @Override
    public Long create(AiRelayPaymentMethodRequest req) {
        validateRequest(req);
        checkCodeUnique(req.getCode(), null);

        AiRelayPaymentMethod entity = new AiRelayPaymentMethod();
        applyRequest(entity, req);
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        paymentMethodMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, AiRelayPaymentMethodRequest req) {
        validateRequest(req);
        AiRelayPaymentMethod existing = requirePaymentMethod(id);
        if (!req.getCode().equals(existing.getCode())) {
            checkCodeUnique(req.getCode(), id);
        }
        applyRequest(existing, req);
        paymentMethodMapper.updateById(existing);
    }

    @Override
    public void delete(Long id) {
        requirePaymentMethod(id);
        long refCount = providerPaymentMethodMapper.selectCount(
                new LambdaQueryWrapper<AiRelayProviderPaymentMethod>()
                        .eq(AiRelayProviderPaymentMethod::getPaymentMethodId, id)
        );
        if (refCount > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "存在服务商已绑定该支付方式，无法删除");
        }
        paymentMethodMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态不能为空");
        }
        AiRelayPaymentMethod existing = requirePaymentMethod(id);
        existing.setStatus(status);
        paymentMethodMapper.updateById(existing);
    }

    private void validateRequest(AiRelayPaymentMethodRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(req.getCode())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "支付方式编码不能为空");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "支付方式名称不能为空");
        }
    }

    private AiRelayPaymentMethod requirePaymentMethod(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "支付方式ID不能为空");
        }
        AiRelayPaymentMethod entity = paymentMethodMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "支付方式不存在");
        }
        return entity;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<AiRelayPaymentMethod> wrapper = new LambdaQueryWrapper<AiRelayPaymentMethod>()
                .eq(AiRelayPaymentMethod::getCode, code);
        if (excludeId != null) {
            wrapper.ne(AiRelayPaymentMethod::getId, excludeId);
        }
        if (paymentMethodMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "支付方式编码已存在");
        }
    }

    private void applyRequest(AiRelayPaymentMethod entity, AiRelayPaymentMethodRequest req) {
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setIconFileId(req.getIconFileId());
        entity.setDescription(req.getDescription());
        if (req.getSortOrder() != null) {
            entity.setSortOrder(req.getSortOrder());
        }
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
    }

    private AiRelayPaymentMethodAdminVO toVO(AiRelayPaymentMethod entity) {
        AiRelayPaymentMethodAdminVO vo = new AiRelayPaymentMethodAdminVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setIconFileId(entity.getIconFileId());
        vo.setDescription(entity.getDescription());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
