package com.example.demo.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 챗봇 전용 벡터 테이블을 앱 기동 시 준비한다. Hibernate ddl-auto는 pgvector 타입을 모르기 때문에
 * 이 테이블만 별도로 관리한다. 다른 모듈의 스키마/설정은 건드리지 않는다.
 * pgvector 확장이 없는 Postgres 이미지에서는 조용히 실패하고 경고만 남긴다(앱은 계속 뜬다).
 */
@Component
public class VectorSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public VectorSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS vector_documents (
                        id BIGSERIAL PRIMARY KEY,
                        source_type VARCHAR(50) NOT NULL,
                        source_id VARCHAR(255) NOT NULL,
                        title VARCHAR(500),
                        embedding vector(768) NOT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT now(),
                        UNIQUE (source_type, source_id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_vector_documents_embedding
                    ON vector_documents USING hnsw (embedding vector_cosine_ops)
                    """);
            log.info("챗봇 벡터 스키마(vector_documents) 준비 완료");
        } catch (DataAccessException e) {
            log.warn("pgvector 스키마 초기화에 실패했습니다. Postgres 이미지에 pgvector 확장이 있는지 확인하세요: {}",
                    e.getMessage());
        }
    }
}
