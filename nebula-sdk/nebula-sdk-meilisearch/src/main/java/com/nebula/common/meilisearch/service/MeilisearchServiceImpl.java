package com.nebula.common.meilisearch.service;

import com.google.gson.Gson;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.model.SearchResult;
import com.meilisearch.sdk.model.SearchResultPaginated;
import com.meilisearch.sdk.model.Searchable;
import com.meilisearch.sdk.model.Settings;
import com.meilisearch.sdk.model.TaskInfo;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.meilisearch.api.IndexConfig;
import com.nebula.common.meilisearch.api.MeiliSearchQuery;
import com.nebula.common.meilisearch.api.MeiliSearchResult;
import com.nebula.common.meilisearch.api.MeilisearchService;
import com.nebula.common.meilisearch.properties.MeilisearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Meilisearch 服务实现
 *
 * @author nebula
 */
@Slf4j
@RequiredArgsConstructor
public class MeilisearchServiceImpl implements MeilisearchService {

    private final Client client;
    private final MeilisearchProperties properties;
    private final Gson gson = new Gson();

    @Override
    public void createIndexIfAbsent(IndexConfig indexConfig) {
        if (indexConfig == null || indexConfig.getUid() == null || indexConfig.getUid().isEmpty()) {
            throw new BizException("Meilisearch 索引配置或 uid 不能为空");
        }
        String uid = indexConfig.getUid();
        String primaryKey = indexConfig.getPrimaryKey() == null ? "id" : indexConfig.getPrimaryKey();

        try {
            client.getIndex(uid);
        } catch (MeilisearchApiException e) {
            if ("index_not_found".equals(e.getCode())) {
                try {
                    TaskInfo task = client.createIndex(uid, primaryKey);
                    waitForTask(uid, task);
                    log.info("Meilisearch index created: {}", uid);
                } catch (MeilisearchException ex) {
                    throw new BizException("创建 Meilisearch 索引失败: " + uid + " - " + ex.getMessage());
                }
            } else {
                throw new BizException("查询 Meilisearch 索引失败: " + uid + " - " + e.getMessage());
            }
        } catch (MeilisearchException e) {
            throw new BizException("查询 Meilisearch 索引失败: " + uid + " - " + e.getMessage());
        }

        applySettings(uid, indexConfig);
    }

    private void applySettings(String uid, IndexConfig indexConfig) {
        boolean hasUpdate = indexConfig.getSearchableAttributes() != null
                || indexConfig.getFilterableAttributes() != null
                || indexConfig.getSortableAttributes() != null
                || indexConfig.getDisplayedAttributes() != null;
        if (!hasUpdate) {
            return;
        }

        Settings settings = new Settings();
        if (indexConfig.getSearchableAttributes() != null) {
            settings.setSearchableAttributes(indexConfig.getSearchableAttributes().toArray(new String[0]));
        }
        if (indexConfig.getFilterableAttributes() != null) {
            settings.setFilterableAttributes(indexConfig.getFilterableAttributes().toArray(new String[0]));
        }
        if (indexConfig.getSortableAttributes() != null) {
            settings.setSortableAttributes(indexConfig.getSortableAttributes().toArray(new String[0]));
        }
        if (indexConfig.getDisplayedAttributes() != null) {
            settings.setDisplayedAttributes(indexConfig.getDisplayedAttributes().toArray(new String[0]));
        }
        try {
            TaskInfo task = client.index(uid).updateSettings(settings);
            waitForTask(uid, task);
        } catch (MeilisearchException e) {
            throw new BizException("更新 Meilisearch 索引设置失败: " + uid + " - " + e.getMessage());
        }
    }

    @Override
    public void deleteIndex(String indexUid) {
        try {
            TaskInfo task = client.deleteIndex(indexUid);
            waitForTask(indexUid, task);
        } catch (MeilisearchException e) {
            throw new BizException("删除 Meilisearch 索引失败: " + indexUid + " - " + e.getMessage());
        }
    }

    @Override
    public boolean indexExists(String indexUid) {
        try {
            client.getIndex(indexUid);
            return true;
        } catch (MeilisearchApiException e) {
            if ("index_not_found".equals(e.getCode())) {
                return false;
            }
            throw new BizException("查询 Meilisearch 索引失败: " + indexUid + " - " + e.getMessage());
        } catch (MeilisearchException e) {
            throw new BizException("查询 Meilisearch 索引失败: " + indexUid + " - " + e.getMessage());
        }
    }

    @Override
    public void addOrUpdateDocument(String indexUid, Object document) {
        if (document == null) {
            return;
        }
        addOrUpdateDocuments(indexUid, List.of(document));
    }

    @Override
    public void addOrUpdateDocuments(String indexUid, List<?> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        try {
            String json = gson.toJson(documents);
            TaskInfo task = client.index(indexUid).updateDocuments(json);
            waitForTask(indexUid, task);
        } catch (MeilisearchException e) {
            throw new BizException("Meilisearch 写入文档失败: " + indexUid + " - " + e.getMessage());
        }
    }

    @Override
    public void replaceDocuments(String indexUid, List<?> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        try {
            String json = gson.toJson(documents);
            TaskInfo task = client.index(indexUid).addDocuments(json);
            waitForTask(indexUid, task);
        } catch (MeilisearchException e) {
            throw new BizException("Meilisearch 替换文档失败: " + indexUid + " - " + e.getMessage());
        }
    }

    @Override
    public void deleteDocument(String indexUid, Object id) {
        if (id == null) {
            return;
        }
        try {
            TaskInfo task = client.index(indexUid).deleteDocument(String.valueOf(id));
            waitForTask(indexUid, task);
        } catch (MeilisearchException e) {
            throw new BizException("Meilisearch 删除文档失败: " + indexUid + " - " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings({"deprecation"})
    public void deleteDocuments(String indexUid, List<?> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<String> stringIds = new ArrayList<>(ids.size());
        for (Object id : ids) {
            stringIds.add(String.valueOf(id));
        }
        try {
            TaskInfo task = client.index(indexUid).deleteDocuments(stringIds);
            waitForTask(indexUid, task);
        } catch (MeilisearchException e) {
            throw new BizException("Meilisearch 批量删除文档失败: " + indexUid + " - " + e.getMessage());
        }
    }

    @Override
    public void deleteAllDocuments(String indexUid) {
        try {
            TaskInfo task = client.index(indexUid).deleteAllDocuments();
            waitForTask(indexUid, task);
        } catch (MeilisearchException e) {
            throw new BizException("Meilisearch 清空文档失败: " + indexUid + " - " + e.getMessage());
        }
    }

    @Override
    public <T> MeiliSearchResult<T> search(String indexUid, String keyword, Class<T> clazz) {
        return search(indexUid, new MeiliSearchQuery(keyword), clazz);
    }

    @Override
    public <T> MeiliSearchResult<T> search(String indexUid, MeiliSearchQuery query, Class<T> clazz) {
        try {
            Index index = client.index(indexUid);
            SearchRequest request = toSearchRequest(query);
            Searchable response = index.search(request);
            return convert(response, clazz);
        } catch (MeilisearchException e) {
            throw new BizException("Meilisearch 搜索失败: " + indexUid + " - " + e.getMessage());
        }
    }

    @Override
    public Client getClient() {
        return client;
    }

    private SearchRequest toSearchRequest(MeiliSearchQuery query) {
        SearchRequest request = new SearchRequest(query.getQ() == null ? "" : query.getQ());
        if (query.getOffset() != null) {
            request.setOffset(query.getOffset());
        }
        if (query.getLimit() != null) {
            request.setLimit(query.getLimit());
        }
        if (query.getPage() != null) {
            request.setPage(query.getPage());
        }
        if (query.getHitsPerPage() != null) {
            request.setHitsPerPage(query.getHitsPerPage());
        }
        if (query.getFilter() != null) {
            request.setFilter(new String[]{query.getFilter()});
        }
        if (query.getSort() != null) {
            request.setSort(query.getSort());
        }
        if (query.getAttributesToRetrieve() != null) {
            request.setAttributesToRetrieve(query.getAttributesToRetrieve());
        }
        if (query.getAttributesToHighlight() != null) {
            request.setAttributesToHighlight(query.getAttributesToHighlight());
        }
        if (query.getHighlightPreTag() != null) {
            request.setHighlightPreTag(query.getHighlightPreTag());
        }
        if (query.getHighlightPostTag() != null) {
            request.setHighlightPostTag(query.getHighlightPostTag());
        }
        if (query.getShowMatchesPosition() != null) {
            request.setShowMatchesPosition(query.getShowMatchesPosition());
        }
        if (query.getFacets() != null) {
            request.setFacets(query.getFacets());
        }
        return request;
    }

    @SuppressWarnings("unchecked")
    private <T> MeiliSearchResult<T> convert(Searchable response, Class<T> clazz) {
        MeiliSearchResult<T> result = new MeiliSearchResult<>();
        List<T> hits = new ArrayList<>();
        ArrayList<HashMap<String, Object>> rawHits = response.getHits();
        if (rawHits != null) {
            for (HashMap<String, Object> hit : rawHits) {
                T item = gson.fromJson(gson.toJson(hit), clazz);
                hits.add(item);
            }
        }
        result.setHits(hits);
        result.setQuery(response.getQuery());
        result.setProcessingTimeMs(response.getProcessingTimeMs());

        Object facetDistribution = response.getFacetDistribution();
        if (facetDistribution instanceof Map) {
            result.setFacetDistribution((Map<String, Map<String, Integer>>) facetDistribution);
        }

        if (response instanceof SearchResult sr) {
            result.setOffset(sr.getOffset());
            result.setLimit(sr.getLimit());
            result.setEstimatedTotalHits(sr.getEstimatedTotalHits());
        } else if (response instanceof SearchResultPaginated srp) {
            result.setPage(srp.getPage());
            result.setHitsPerPage(srp.getHitsPerPage());
            result.setTotalHits(srp.getTotalHits());
            result.setTotalPages(srp.getTotalPages());
        }
        return result;
    }

    private void waitForTask(String indexUid, TaskInfo task) {
        if (!properties.isWaitForTask() || task == null) {
            return;
        }
        try {
            client.index(indexUid).waitForTask(task.getTaskUid(), properties.getTaskTimeoutMs(), properties.getTaskIntervalMs());
        } catch (MeilisearchException e) {
            throw new BizException("等待 Meilisearch 任务完成失败: " + indexUid + " - " + e.getMessage());
        }
    }
}
