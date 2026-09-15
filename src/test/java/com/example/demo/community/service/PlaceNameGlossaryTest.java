package com.example.demo.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지명 표가 제대로 읽히는지 본다.
 *
 * <p>표가 비어 있으면 번역기가 지명을 매번 새로 지어내는데, 화면은 멀쩡히 나와서
 * 눈으로 보지 않으면 모른다. 실제로 기장이 중국어에서 세 가지 이름으로 나왔다.
 *
 * <p>번역기를 실제로 부르는 부분은 여기서 확인하지 않는다. 키가 필요하고 돈이 든다.
 */
class PlaceNameGlossaryTest {

    private PlaceNameGlossary glossary;

    @BeforeEach
    void setUp() {
        glossary = new PlaceNameGlossary(new ObjectMapper());
        glossary.load();
    }

    @Test
    void 표를_읽는다() {
        assertThat(glossary.size()).isGreaterThan(20);
    }

    @Test
    void 세_언어가_모두_채워져_있다() {
        for (String lang : new String[]{"ja", "zh", "fr"}) {
            String entries = glossary.entriesFor(lang);
            assertThat(entries.split("\n"))
                    .as("%s 표기가 빠진 이름이 있으면 그 이름만 번역기 마음대로 나간다", lang)
                    .hasSize(glossary.size());
        }
    }

    @Test
    void 용어집은_국문과_그_언어_표기_두_칸이다() {
        for (String line : glossary.entriesFor("ja").split("\n")) {
            assertThat(line.split("\t"))
                    .as("줄: %s", line)
                    .hasSize(2);
        }
    }

    @Test
    void 자주_쓰는_지명이_들어_있다() {
        String ja = glossary.entriesFor("ja");
        assertThat(ja).contains("해운대\t海雲台");
        assertThat(ja).contains("기장\t機張");
        assertThat(glossary.entriesFor("zh")).contains("영도\t影岛");
        assertThat(glossary.entriesFor("fr")).contains("흰여울\tHuinnyeoul");
    }

    @Test
    void 없는_언어를_물으면_빈_값이다() {
        assertThat(glossary.entriesFor("de")).isEmpty();
    }
}
