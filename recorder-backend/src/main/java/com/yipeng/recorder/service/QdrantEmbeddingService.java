package com.yipeng.recorder.service;

import com.yipeng.recorder.model.Label;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.request.QdrantDeleteRequest;
import com.yipeng.recorder.request.QdrantExistsRequest;
import com.yipeng.recorder.request.QdrantQueryRequest;
import com.yipeng.recorder.request.QdrantUpsertRequest;
import com.yipeng.recorder.response.QdrantQueryResponse;
import com.yipeng.recorder.response.QdrantQueryResult;
import com.yipeng.recorder.response.QdrantUpsertResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class QdrantEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(QdrantEmbeddingService.class);

    private final RestTemplate restTemplate;
    private final String qdrantBaseUrl;
    private final String qdrantCollection;

    public QdrantEmbeddingService(
            RestTemplate restTemplate,
            @Value("${market.pulse.url}") String marketPulseUrl,
            @Value("${qdrant.collection:records_chunks}") String qdrantCollection) {
        this.restTemplate = restTemplate;
        String base = marketPulseUrl.endsWith("/") ? marketPulseUrl.substring(0, marketPulseUrl.length() - 1) : marketPulseUrl;
        this.qdrantBaseUrl = base + "/qdrant";
        this.qdrantCollection = qdrantCollection;
    }

    @Async
    public CompletableFuture<List<String>> upsertRecordAsync(Record record, User user) {
        if (record.getId() == null) {
            return failedFuture(new IllegalArgumentException("Record id must not be null for upsert"));
        }
        QdrantUpsertRequest body = new QdrantUpsertRequest(
                record.getId().toString(),
                user.getId() != null ? user.getId().toString() : user.getUsername(),
                record.isPublic(),
                record.getEmbeddingString(),
                extractLabelNames(record),
                qdrantCollection
        );
        try {
            QdrantUpsertResponse resp = post("/upsert", body, QdrantUpsertResponse.class);
            List<String> ids = resp != null && resp.getPointIds() != null ? resp.getPointIds() : List.of();
            logger.info("Upserting record with id {} to qdrant.", record.getId());
            return CompletableFuture.completedFuture(ids);
        } catch (Exception ex) {
            logger.error("Failed to upsert record {} into Qdrant", record.getId(), ex);
            return failedFuture(ex);
        }
    }

    public List<String> upsertRecordSync(Record record, User user) {
        if (record.getId() == null) {
            throw new IllegalArgumentException("Record id must not be null for upsert");
        }
        QdrantUpsertRequest body = new QdrantUpsertRequest(
                record.getId().toString(),
                user.getId() != null ? user.getId().toString() : user.getUsername(),
                record.isPublic(),
                record.getEmbeddingString(),
                extractLabelNames(record),
                qdrantCollection
        );
        QdrantUpsertResponse resp = post("/upsert", body, QdrantUpsertResponse.class);
        logger.info("Upserted record synchronously with id {} to qdrant.", record.getId());
        return resp != null && resp.getPointIds() != null ? resp.getPointIds() : List.of();
    }

    @Async
    public CompletableFuture<Boolean> deleteRecordIfExistsAsync(Record record) {
        if (record.getId() == null) {
            return failedFuture(new IllegalArgumentException("Record id must not be null for delete"));
        }
        String recordId = record.getId().toString();
        try {
            boolean exists = recordExists(recordId);
            if (exists) {
                QdrantDeleteRequest body = new QdrantDeleteRequest(recordId, qdrantCollection);
                post("/delete", body, Void.class);
            }
            logger.info("Deleting record with id {} from qdrant.", record.getId());
            return CompletableFuture.completedFuture(exists);
        } catch (Exception ex) {
            logger.error("Failed to delete record {} from Qdrant", recordId, ex);
            return failedFuture(ex);
        }
    }

    public List<QdrantQueryResult> querySimilarRecords(
            String queryText,
            User user,
            Double similarityThreshold,
            Integer limit) {
        try {
            QdrantQueryRequest body = new QdrantQueryRequest(
                    user.getId() != null ? user.getId().toString() : user.getUsername(),
                    queryText,
                    similarityThreshold,
                    limit != null ? limit : 20,
                    qdrantCollection
            );
            QdrantQueryResponse resp = post("/query", body, QdrantQueryResponse.class);
            return resp != null && resp.getResults() != null ? resp.getResults() : List.of();
        } catch (Exception ex) {
            logger.error("Failed to query similar records", ex);
            throw ex;
        }
    }

    public boolean recordExists(String recordId) {
        QdrantExistsRequest body = new QdrantExistsRequest(recordId, qdrantCollection);
        Map<String, Object> resp = post("/exists", body, Map.class);
        Object exists = resp != null ? resp.get("exists") : null;
        return exists instanceof Boolean && (Boolean) exists;
    }

    public boolean deleteRecordIfExistsSync(String recordId) {
        boolean exists = recordExists(recordId);
        if (exists) {
            QdrantDeleteRequest body = new QdrantDeleteRequest(recordId, qdrantCollection);
            post("/delete", body, Void.class);
        }
        logger.info("Deleted record with id {} from qdrant if it existed.", recordId);
        return exists;
    }

    private List<String> extractLabelNames(Record record) {
        if (record.getLabels() == null) {
            return List.of();
        }
        return record.getLabels().stream()
                .map(Label::getLabelName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        ResponseEntity<T> response = restTemplate.exchange(
                qdrantBaseUrl + path,
                HttpMethod.POST,
                entity,
                responseType
        );
        return response.getBody();
    }

    private <T> CompletableFuture<T> failedFuture(Throwable ex) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

}
