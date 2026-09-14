-- 신고 사유 값의 이름과 뜻을 맞춘다.
--
-- 왜 필요한가
--   사유 목록에 성적 콘텐츠와 자살·자해 자리가 없었다. 그래서 신고 창이 그 둘을
--   SPAM 과 MISINFORMATION 에 끼워 넣고, 스팸은 MISLEADING 으로 밀어 두었다.
--   신고 창·관리자 화면·DB 세 군데가 같은 오해를 공유해서 이용자와 관리자에게는
--   맞아 보였지만, DB 를 직접 열거나 통계를 내는 사람은 SPAM 을 스팸으로 읽는다.
--
--   자리를 만들었으니(SEXUAL, SELF_HARM) 쌓여 있는 값도 같이 옮긴다.
--
-- 무엇을 하나
--   SPAM           -> SEXUAL          (성적 콘텐츠)
--   MISINFORMATION -> SELF_HARM       (자살·자해)
--   WRONG_INFO     -> MISINFORMATION  (잘못된 정보)
--   MISLEADING     -> SPAM            (스팸)
--   나머지는 그대로. 옮기는 순서 때문에 값이 겹치지 않도록 UPDATE 한 번으로 끝낸다.
--
-- 언제 돌리나
--   새 코드를 올린 직후 한 번. 코드가 SEXUAL 을 쓰기 시작하는데 표의 허용값 제약이
--   옛 목록이면 신고가 저장되지 않는다. 제약은 자동으로 안 바뀌므로 여기서 바꾼다.
--
--   sudo -u postgres psql -d koreapoint -v ON_ERROR_STOP=1 < ~/fix-report-reason-codes.sql
--
--   두 번 돌려도 안전하다. 이미 옮긴 표는 건너뛴다.
SET client_encoding = 'UTF8';
\set ON_ERROR_STOP on

DO $$
DECLARE
    old_schema boolean;
    moved      integer;
BEGIN
    -- 옛 목록인지 제약 정의로 판단한다. 다 옮긴 뒤에는 WRONG_INFO 가 사라진다.
    -- 이 판단이 없으면 두 번째 실행에서 진짜 스팸(SPAM)이 성적 콘텐츠로 바뀐다.
    SELECT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'content_report_reason_code_check'
           AND pg_get_constraintdef(oid) LIKE '%WRONG_INFO%'
    ) INTO old_schema;

    IF NOT old_schema THEN
        RAISE NOTICE '이미 옮겨 둔 표다. 아무것도 하지 않는다.';
        RETURN;
    END IF;

    ALTER TABLE content_report DROP CONSTRAINT content_report_reason_code_check;

    UPDATE content_report
       SET reason_code = CASE reason_code
               WHEN 'SPAM'           THEN 'SEXUAL'
               WHEN 'MISINFORMATION' THEN 'SELF_HARM'
               WHEN 'WRONG_INFO'     THEN 'MISINFORMATION'
               WHEN 'MISLEADING'     THEN 'SPAM'
               ELSE reason_code
           END
     WHERE reason_code IN ('SPAM', 'MISINFORMATION', 'WRONG_INFO', 'MISLEADING');
    GET DIAGNOSTICS moved = ROW_COUNT;

    ALTER TABLE content_report ADD CONSTRAINT content_report_reason_code_check
        CHECK (reason_code IN ('SEXUAL', 'ABUSE', 'HATE', 'HARASSMENT', 'HARMFUL',
                               'SELF_HARM', 'MISINFORMATION', 'CHILD_ABUSE',
                               'TERRORISM', 'SPAM', 'OTHER'));

    RAISE NOTICE '옮긴 신고 %건', moved;
END $$;

-- 확인. 옛 이름이 남아 있으면 안 된다.
SELECT reason_code, count(*) AS 건수
  FROM content_report
 GROUP BY reason_code
 ORDER BY 2 DESC;

SELECT '옛 이름 남은 것' AS t,
       count(*)::text AS v
  FROM content_report
 WHERE reason_code IN ('WRONG_INFO', 'MISLEADING');
