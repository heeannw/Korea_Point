package com.example.demo.chatbot.service;

import java.util.Optional;

/**
 * source_type별로 "원문을 다시 가져오는 방법"을 정의한다. vector_documents엔 원문이 없기 때문에,
 * 검색으로 찾은 source_id를 가지고 실제 원문을 얻으려면 이 인터페이스를 거쳐야 한다.
 * 나중에 관광지(TourAPI) 등 다른 소스가 추가되면 구현체만 하나 더 등록하면 된다.
 */
public interface SourceTextResolver {

    /** 이 리졸버가 담당하는 source_type (예: "busan_story"). */
    String sourceType();

    /** source_id로 원문을 다시 조회한다. 삭제됐거나 API 실패면 빈 값. */
    Optional<ResolvedSource> resolve(String sourceId);

    record ResolvedSource(String title, String text) {}
}
