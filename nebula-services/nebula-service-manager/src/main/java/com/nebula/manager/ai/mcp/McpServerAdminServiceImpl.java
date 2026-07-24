package com.nebula.manager.ai.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.store.AiMcpServer;
import com.nebula.common.ai.flow.store.AiMcpServerMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.crypto.AesUtil;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.McpServerPageQuery;
import com.nebula.manager.dto.McpServerSaveRequest;
import com.nebula.manager.vo.McpServerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI MCP 服务器管理服务实现（管理员端）
 * 直接操作 {@code ai_mcp_server} 表完成 CRUD：写入端对明文 authToken 加密、对 args/env/headers/options 序列化为
 * JSON 字符串；读出端对 authToken 仅以掩码暴露，绝不回传明文。加解密密钥与 {@code DatabaseModelProfileRepository}
 * 共用配置 {@code nebula.ai.profile.secret}。
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class McpServerAdminServiceImpl implements McpServerAdminService {

    /**
     * 允许的传输类型
     */
    private static final Set<String> TRANSPORTS = Set.of("stdio", "sse", "streamable-http");

    private final AiMcpServerMapper mcpServerMapper;

    /**
     * JSON 处理器：自建实例而非容器注入。本工程 Web 层走 Jackson 3（{@code tools.jackson}），
     * 容器中并无 Jackson 2（{@code com.fasterxml.jackson}）的 {@code ObjectMapper} Bean，
     * 自建即可，与 {@code ModelProfileAdminServiceImpl} 的做法保持一致。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${nebula.ai.profile.secret:nebula-ai-profile-default-secret}")
    private String secret;

    @Override
    public PageResult<McpServerVO> page(McpServerPageQuery query) {
        McpServerPageQuery safe = query == null ? new McpServerPageQuery() : query;
        Page<AiMcpServer> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiMcpServer> wrapper = new LambdaQueryWrapper<AiMcpServer>()
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiMcpServer::getServerCode, safe.getKeyword())
                        .or()
                        .like(AiMcpServer::getName, safe.getKeyword())
                        .or()
                        .like(AiMcpServer::getUrl, safe.getKeyword()))
                .eq(StringUtils.hasText(safe.getTransport()), AiMcpServer::getTransport, safe.getTransport())
                .eq(safe.getStatus() != null, AiMcpServer::getStatus, safe.getStatus())
                .orderByDesc(AiMcpServer::getUpdateTime);
        Page<AiMcpServer> result = mcpServerMapper.selectPage(page, wrapper);
        List<McpServerVO> rows = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public McpServerVO detail(Long id) {
        return toVO(getExisting(id));
    }

    @Override
    public Long create(McpServerSaveRequest request) {
        validate(request, true);
        if (exists(request.getServerCode(), null)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务器编码已存在: " + request.getServerCode());
        }
        AiMcpServer entity = new AiMcpServer();
        applyRequest(entity, request, true);
        mcpServerMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, McpServerSaveRequest request) {
        validate(request, false);
        AiMcpServer entity = getExisting(id);
        // 服务器编码不可变更，忽略请求中的 serverCode
        applyRequest(entity, request, false);
        mcpServerMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        getExisting(id);
        mcpServerMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态值非法");
        }
        AiMcpServer entity = getExisting(id);
        entity.setStatus(status);
        mcpServerMapper.updateById(entity);
    }

    /**
     * 将保存请求合并进实体：加密 authToken、序列化 args/env/headers/options。
     *
     * @param entity   目标实体
     * @param request  保存请求
     * @param creating 是否为创建（创建时写入 serverCode 与默认状态）
     */
    private void applyRequest(AiMcpServer entity, McpServerSaveRequest request, boolean creating) {
        if (creating) {
            entity.setServerCode(request.getServerCode());
            entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        } else if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setName(request.getName());
        entity.setTransport(request.getTransport());
        entity.setCommand(request.getCommand());
        entity.setArgs(writeJson(request.getArgs()));
        entity.setEnv(writeJson(request.getEnv()));
        entity.setUrl(request.getUrl());
        entity.setHeaders(writeJson(request.getHeaders()));
        entity.setTimeoutMs(request.getTimeoutMs());
        entity.setOptions(writeJson(request.getOptions()));
        entity.setRemark(request.getRemark());
        // authToken：有明文则加密覆盖；留空时创建写 null、更新保留原值
        if (StringUtils.hasText(request.getAuthToken())) {
            entity.setAuthToken(AesUtil.encrypt(request.getAuthToken(), secret));
        } else if (creating) {
            entity.setAuthToken(null);
        }
    }

    /**
     * 实体转 VO：authToken 掩码处理，不回传明文；反序列化各 JSON 字段。
     */
    private McpServerVO toVO(AiMcpServer entity) {
        McpServerVO vo = new McpServerVO();
        vo.setId(entity.getId());
        vo.setServerCode(entity.getServerCode());
        vo.setName(entity.getName());
        vo.setTransport(entity.getTransport());
        vo.setCommand(entity.getCommand());
        vo.setArgs(readJson(entity.getArgs(), new TypeReference<List<String>>() {
        }));
        vo.setEnv(readJson(entity.getEnv(), new TypeReference<Map<String, String>>() {
        }));
        vo.setUrl(entity.getUrl());
        vo.setHeaders(readJson(entity.getHeaders(), new TypeReference<Map<String, String>>() {
        }));
        vo.setTimeoutMs(entity.getTimeoutMs());
        vo.setOptions(readJson(entity.getOptions(), new TypeReference<Map<String, Object>>() {
        }));
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        boolean hasToken = StringUtils.hasText(entity.getAuthToken());
        vo.setHasAuthToken(hasToken);
        vo.setAuthTokenMasked(hasToken ? mask(entity.getAuthToken()) : null);
        return vo;
    }

    /**
     * 生成凭证掩码。对密文解密后取明文尾部，避免泄露完整凭证；解密失败则给出通用掩码。
     */
    private String mask(String cipher) {
        try {
            String plain = AesUtil.decrypt(cipher, secret);
            if (plain == null || plain.isEmpty()) {
                return null;
            }
            int keep = Math.min(4, plain.length());
            return "****" + plain.substring(plain.length() - keep);
        } catch (Exception e) {
            return "****";
        }
    }

    /**
     * 序列化任意对象为 JSON 字符串；为空（null / 空集合 / 空 Map）返回 null。
     */
    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "参数序列化失败");
        }
    }

    /**
     * 反序列化 JSON 字符串为目标类型；解析失败或为空返回 null。
     */
    private <T> T readJson(String json, TypeReference<T> type) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    private AiMcpServer getExisting(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务器ID不能为空");
        }
        AiMcpServer entity = mcpServerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "MCP服务器不存在: " + id);
        }
        return entity;
    }

    private boolean exists(String serverCode, Long excludeId) {
        LambdaQueryWrapper<AiMcpServer> wrapper = new LambdaQueryWrapper<AiMcpServer>()
                .eq(AiMcpServer::getServerCode, serverCode)
                .ne(excludeId != null, AiMcpServer::getId, excludeId);
        return mcpServerMapper.selectCount(wrapper) > 0;
    }

    /**
     * 基础校验：编码必填（创建时）、传输类型合法、按传输类型校验必填项。
     */
    private void validate(McpServerSaveRequest request, boolean creating) {
        if (request == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求不能为空");
        }
        if (creating && !StringUtils.hasText(request.getServerCode())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务器编码不能为空");
        }
        String transport = request.getTransport();
        if (!StringUtils.hasText(transport) || !TRANSPORTS.contains(transport)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "传输类型非法，仅支持 stdio/sse/streamable-http");
        }
        if ("stdio".equals(transport)) {
            if (!StringUtils.hasText(request.getCommand())) {
                throw new BizException(HttpStatus.BAD_REQUEST, "stdio 传输需配置启动命令(command)");
            }
        } else if (!StringUtils.hasText(request.getUrl())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "远程传输需配置端点地址(url)");
        }
    }
}
