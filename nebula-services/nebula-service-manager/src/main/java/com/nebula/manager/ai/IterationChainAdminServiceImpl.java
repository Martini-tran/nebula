package com.nebula.manager.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.ai.flow.store.AiAgentInstance;
import com.nebula.common.ai.flow.store.AiAgentInstanceMapper;
import com.nebula.common.ai.flow.store.AiAgentIteration;
import com.nebula.common.ai.flow.store.AiAgentIterationMapper;
import com.nebula.common.ai.flow.store.FlowJsonCodec;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.IterationChainPageQuery;
import com.nebula.manager.dto.IterationChainSaveRequest;
import com.nebula.manager.vo.IterationChainVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 跨实例迭代链管理服务实现（管理员端）
 * 链 CRUD 直接操作 {@link AiAgentIterationMapper}（照 {@code AiAgentAdminServiceImpl} 风格）；推进交给
 * {@code IterationDriver} 定时负责，本类不碰推进逻辑。见 docs/跨实例迭代层设计.md。
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class IterationChainAdminServiceImpl implements IterationChainAdminService {

    private final AiAgentIterationMapper iterationMapper;

    private final AiAgentInstanceMapper instanceMapper;

    @Override
    public PageResult<IterationChainVO> page(IterationChainPageQuery query) {
        IterationChainPageQuery safe = query == null ? new IterationChainPageQuery() : query;
        Page<AiAgentIteration> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiAgentIteration> wrapper = new LambdaQueryWrapper<AiAgentIteration>()
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiAgentIteration::getName, safe.getKeyword())
                        .or()
                        .like(AiAgentIteration::getAgentCode, safe.getKeyword()))
                .eq(StringUtils.hasText(safe.getStatus()), AiAgentIteration::getStatus, safe.getStatus())
                .orderByDesc(AiAgentIteration::getUpdateTime);
        Page<AiAgentIteration> result = iterationMapper.selectPage(page, wrapper);
        List<IterationChainVO> rows = result.getRecords().stream().map(this::toHead).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public IterationChainVO detail(String chainId) {
        AiAgentIteration entity = requireChain(chainId);
        IterationChainVO vo = toHead(entity);
        // 时间线：阶段 I 实例未落 chainId 关联，先展示当前 last_instance_id 那一篇（链头指针）。
        // 完整回放整条系列需阶段 II 用 parent_instance_id 串链（见 docs 4.3）。
        if (StringUtils.hasText(entity.getLastInstanceId())) {
            AiAgentInstance last = instanceMapper.selectOne(new LambdaQueryWrapper<AiAgentInstance>()
                    .eq(AiAgentInstance::getInstanceId, entity.getLastInstanceId())
                    .last("limit 1"));
            if (last != null) {
                IterationChainVO.IterationRunVO run = new IterationChainVO.IterationRunVO();
                run.setInstanceId(last.getInstanceId());
                run.setStatus(last.getStatus());
                run.setCreateTime(last.getCreateTime());
                vo.getRuns().add(run);
            }
        }
        return vo;
    }

    @Override
    public String create(IterationChainSaveRequest request) {
        validate(request);
        AiAgentIteration entity = new AiAgentIteration();
        String chainId = request.getAgentCode() + "-chain-" + UUID.randomUUID().toString().replace("-", "");
        entity.setChainId(chainId);
        applySave(entity, request);
        entity.setSeq(0);
        entity.setStatus("ACTIVE");
        entity.setConsecutiveFails(0);
        entity.setLockVersion(0);
        // 首轮触发时间：显式给了用它，否则立即到点（创建后下次扫描即跑首轮）
        entity.setNextRunAt(request.getFirstRunAt() != null ? request.getFirstRunAt() : LocalDateTime.now());
        iterationMapper.insert(entity);
        return chainId;
    }

    @Override
    public void update(String chainId, IterationChainSaveRequest request) {
        AiAgentIteration entity = requireChain(chainId);
        validate(request);
        applySave(entity, request);
        iterationMapper.updateById(entity);
    }

    @Override
    public void pause(String chainId) {
        requireChain(chainId);
        int affected = iterationMapper.update(null, new LambdaUpdateWrapper<AiAgentIteration>()
                .eq(AiAgentIteration::getChainId, chainId)
                .eq(AiAgentIteration::getStatus, "ACTIVE")
                .set(AiAgentIteration::getStatus, "PAUSED"));
        if (affected == 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "只有 ACTIVE 的链可暂停");
        }
    }

    @Override
    public void resume(String chainId) {
        requireChain(chainId);
        int affected = iterationMapper.update(null, new LambdaUpdateWrapper<AiAgentIteration>()
                .eq(AiAgentIteration::getChainId, chainId)
                .eq(AiAgentIteration::getStatus, "PAUSED")
                .set(AiAgentIteration::getStatus, "ACTIVE")
                .set(AiAgentIteration::getConsecutiveFails, 0)
                .set(AiAgentIteration::getErrorMsg, null));
        if (affected == 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "只有 PAUSED 的链可恢复");
        }
    }

    @Override
    public void runNow(String chainId) {
        AiAgentIteration entity = requireChain(chainId);
        if (!"ACTIVE".equals(entity.getStatus())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "只有 ACTIVE 的链可立即推进（当前 " + entity.getStatus() + "）");
        }
        // 把 next_run_at 置当前，下次定时扫描（≤ scan-interval）即跑；仍走 Redis 锁，与定时同一条路径
        iterationMapper.update(null, new LambdaUpdateWrapper<AiAgentIteration>()
                .eq(AiAgentIteration::getChainId, chainId)
                .set(AiAgentIteration::getNextRunAt, LocalDateTime.now()));
    }

    @Override
    public void delete(String chainId) {
        AiAgentIteration entity = requireChain(chainId);
        iterationMapper.deleteById(entity.getId());
    }

    /* ===================== 内部 ===================== */

    private AiAgentIteration requireChain(String chainId) {
        if (!StringUtils.hasText(chainId)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "链标识不能为空");
        }
        AiAgentIteration entity = iterationMapper.selectOne(new LambdaQueryWrapper<AiAgentIteration>()
                .eq(AiAgentIteration::getChainId, chainId)
                .last("limit 1"));
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "迭代链不存在: " + chainId);
        }
        return entity;
    }

    private void applySave(AiAgentIteration entity, IterationChainSaveRequest request) {
        entity.setName(request.getName());
        entity.setAgentCode(request.getAgentCode());
        entity.setCron(request.getCron());
        entity.setMaxIterations(request.getMaxIterations());
        entity.setUntilExpr(request.getUntilExpr());
        entity.setCarryOver(FlowJsonCodec.write(request.getCarryOver()));
        entity.setSeedInputs(FlowJsonCodec.write(request.getSeedInputs()));
        entity.setUserId(request.getUserId());
        entity.setConversationId(request.getConversationId());
    }

    private IterationChainVO toHead(AiAgentIteration e) {
        IterationChainVO vo = new IterationChainVO();
        vo.setId(e.getId());
        vo.setChainId(e.getChainId());
        vo.setName(e.getName());
        vo.setAgentCode(e.getAgentCode());
        vo.setCron(e.getCron());
        vo.setNextRunAt(e.getNextRunAt());
        vo.setSeq(e.getSeq());
        vo.setMaxIterations(e.getMaxIterations());
        vo.setUntilExpr(e.getUntilExpr());
        vo.setCarryOver(FlowJsonCodec.readStringMap(e.getCarryOver()));
        vo.setSeedInputs(FlowJsonCodec.readObjectMap(e.getSeedInputs()));
        vo.setLastInstanceId(e.getLastInstanceId());
        vo.setStatus(e.getStatus());
        vo.setConsecutiveFails(e.getConsecutiveFails());
        vo.setErrorMsg(e.getErrorMsg());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    private void validate(IterationChainSaveRequest request) {
        if (request == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (!StringUtils.hasText(request.getAgentCode())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "agentCode 不能为空");
        }
        if (!StringUtils.hasText(request.getCron())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "cron 不能为空");
        }
    }
}
