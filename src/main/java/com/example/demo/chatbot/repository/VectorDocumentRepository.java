package com.example.demo.chatbot.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * vector_documents 테이블 접근. 벡터(임베딩)와 원본을 가리키는 최소 식별자만 저장하고,
 * 원문 텍스트는 절대 담지 않는다 — 원문은 필요할 때마다 원본 API에서 다시 받아온다.
 * pgvector 타입을 JPA가 이해하지 못해 순수 JdbcTemplate으로 다룬다.
 */
@Repository
public class VectorDocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public VectorDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** source_type + source_id 기준으로 없으면 넣고 있으면 벡터를 갱신한다. */
    public void upsert(String sourceType, String sourceId, String title, float[] embedding) {
        String sql = """
                INSERT INTO vector_documents (source_type, source_id, title, embedding, updated_at)
                VALUES (?, ?, ?, ?::vector, now())
                ON CONFLICT (source_type, source_id)
                DO UPDATE SET title = EXCLUDED.title, embedding = EXCLUDED.embedding, updated_at = now()
                """;
        jdbcTemplate.update(sql, sourceType, sourceId, title, toVectorLiteral(embedding));
    }

    /**
     * 코사인 거리(embedding <=> 질의벡터) 기준으로 가장 가까운 문서를 찾는다.
     * sourceType이 null이면 전체 유형에서 찾는다. 반환값엔 원문이 없다 — source_id로 다시 조회해야 한다.
     */
    public List<VectorMatch> findNearest(String sourceType, float[] queryEmbedding, int topK) {
        String literal = toVectorLiteral(queryEmbedding);
        if (sourceType == null || sourceType.isBlank()) {
            String sql = """
                    SELECT source_type, source_id, title, embedding <=> ?::vector AS distance
                    FROM vector_documents
                    ORDER BY distance ASC
                    LIMIT ?
                    """;
            return jdbcTemplate.query(sql, VectorDocumentRepository::mapRow, literal, topK);
        }
        String sql = """
                SELECT source_type, source_id, title, embedding <=> ?::vector AS distance
                FROM vector_documents
                WHERE source_type = ?
                ORDER BY distance ASC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, VectorDocumentRepository::mapRow, literal, sourceType, topK);
    }

    private static VectorMatch mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new VectorMatch(
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("title"),
                rs.getDouble("distance"));
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }

    /** 유사도 검색 결과 한 건. 원문은 없고 어디서 다시 받아와야 하는지(sourceId)만 담는다. */
    public record VectorMatch(String sourceType, String sourceId, String title, double distance) {}
}
