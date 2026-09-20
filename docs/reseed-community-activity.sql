-- 커뮤니티 데모 데이터의 '활동 흔적'을 지금 시각 기준으로 다시 뿌린다.
--
-- 왜 다시 돌려야 하나
--   시드 날짜는 넣은 순간에만 맞다. 하루 지나면 '오늘 올라온 글'이 사라지고,
--   일간 베스트와 NEW 배지가 비어 버린다. 시연 전에 한 번 돌리면 된다.
--
-- 무엇을 건드리나
--   provider='demo' 계정이 쓴 글의 created_at / updated_at / view_count 와
--   거기에 데모 계정이 단 댓글의 시각뿐이다. 팀이나 방문자가 실제로 쓴
--   provider='local' 글·댓글과 홍보 글(promo_until 로 노출 기간을 산 것)은 손대지 않는다.
--   다만 실제 댓글도 글이 댓글보다 뒤로 밀리면 순서를 지키려고 글 시각 뒤로 당긴다.
--
-- 되돌리기
--   backup_post_dates_* / backup_comment_dates_* / backup_post_views_* 테이블에
--   직전 값이 남는다.
--
-- 실행
--   서버:   sudo -u postgres psql -d koreapoint -v apptz=UTC \
--             < docs/reseed-community-activity.sql
--   로컬:   docker exec -i korea_point-postgres-1 psql -U postgres -d korea_point \
--     -v ON_ERROR_STOP=1 -f - < docs/reseed-community-activity.sql

-- 시간대를 앱에 맞춘다.
--   created_at 은 timestamp without time zone 이고, 앱은 LocalDateTime.now() 가 주는
--   벽시계 값을 그대로 쓴다. 이 스크립트도 앱과 같은 시간대를 봐야 한다. 다르면 방금
--   넣은 글이 '9시간 전'이나 '9시간 뒤'로 뜨고 일간·NEW 배지가 어긋난다.
--
--   앱이 어느 시간대를 쓰는지는 도는 컴퓨터마다 다르다.
--     - 로컬(개발자 PC, 한국 시간): 기본값 Asia/Seoul 그대로
--     - 배포 서버(GCP, UTC):        psql 에 -v apptz=UTC 를 붙인다
--   확인법: `date -u` 와 `sudo journalctl -u koreapoint -n 3` 의 시각이 같으면 앱은 UTC 다.
\if :{?apptz}
\else
\set apptz 'Asia/Seoul'
\endif
SET TIME ZONE :'apptz';
\echo '기준 시간대:' :apptz

BEGIN;

DROP TABLE IF EXISTS backup_post_dates_prev;
CREATE TABLE backup_post_dates_prev AS SELECT id, created_at, updated_at FROM post;
DROP TABLE IF EXISTS backup_comment_dates_prev;
CREATE TABLE backup_comment_dates_prev AS SELECT id, created_at, updated_at FROM comments;
DROP TABLE IF EXISTS backup_post_views_prev;
CREATE TABLE backup_post_views_prev AS SELECT id, view_count FROM post;

-- ── 1. 글이 올라온 시각 ──────────────────────────────────────
-- 나이 = 60 * (순위/전체)^1.6.
-- 지수를 1보다 크게 두면 최근 쪽이 촘촘해진다. 실제 게시판이 그렇고,
-- 그래야 일간 / 주간 / 월간 / 전체가 서로 다른 목록이 된다.
-- 1.6 은 하루 13건 · 일주일 44건 · 한 달 110건이 나오게 맞춘 값이다.
CREATE TEMP TABLE remap AS
WITH d AS (
    SELECT p.id, p.created_at AS old_at,
           row_number() OVER (ORDER BY random()) AS r,
           count(*) OVER () AS n
    FROM post p JOIN users u ON u.id = p.user_id
    WHERE p.deleted_at IS NULL AND u.provider = 'demo'
)
SELECT id, old_at,
       LOCALTIMESTAMP - (60.0 * power(r::numeric / n, 1.6)) * INTERVAL '1 day' AS new_at
FROM d;

UPDATE post p
   SET created_at = m.new_at,
       updated_at = CASE WHEN p.updated_at IS NULL THEN NULL
                         ELSE m.new_at + (p.updated_at - p.created_at) END
  FROM remap m WHERE p.id = m.id;

-- 데모 계정이 단 댓글만 글과 같은 폭으로 같이 옮긴다. 안 그러면 글보다 이른 댓글이 생긴다.
--
-- 실제 사람이 단 댓글은 건드리지 않는다. 글쓴이만 보고 댓글 전부를 옮기면, 오늘 심사위원이
-- 데모 글에 단 댓글이 그날 밤 몇 주 전 날짜로 밀린다. 방금 쓴 글이 한 달 전 것으로 보인다.
UPDATE comments c
   SET created_at = c.created_at + (m.new_at - m.old_at),
       updated_at = CASE WHEN c.updated_at IS NULL THEN NULL
                         ELSE c.updated_at + (m.new_at - m.old_at) END
  FROM remap m, users cu
 WHERE c.post_id = m.id AND cu.id = c.user_id AND cu.provider = 'demo';

-- 글이 댓글보다 뒤로 밀린 경우만 바로잡는다. 옮기지 않은 실제 댓글도 여기서 순서를 지킨다.
UPDATE comments c
   SET created_at = GREATEST(LEAST(c.created_at, LOCALTIMESTAMP), p.created_at + INTERVAL '1 minute')
  FROM post p
 WHERE p.id = c.post_id AND (c.created_at < p.created_at OR c.created_at > LOCALTIMESTAMP);

-- ── 2. 조회수 ────────────────────────────────────────────────
-- 조회수 값 자체는 그대로 두고 순서만 다시 배정한다. 숫자를 지어내지 않는다.
--
-- 나이와 상관을 주되(오래된 글일수록 많이 쌓인다) 로그 공간에 큰 잡음을 섞는다.
-- 상관을 완전히 주면 조회수가 나이에 비례해 '하루당 조회수'가 상수가 되고,
-- 그러면 지금 뜨는 글을 가려낼 신호가 사라진다. 가끔 터지는 글이 있어야 한다.
WITH ranked AS (
    SELECT p.id,
           row_number() OVER (
               ORDER BY ln(EXTRACT(EPOCH FROM (LOCALTIMESTAMP - p.created_at)) / 86400.0 + 1)
                        + 1.1 * sqrt(-2 * ln(random())) * cos(2 * pi() * random())
           ) AS rk
    FROM post p JOIN users u ON u.id = p.user_id
    WHERE p.deleted_at IS NULL AND u.provider = 'demo'
), vals AS (
    SELECT p.view_count, row_number() OVER (ORDER BY p.view_count) AS rk
    FROM post p JOIN users u ON u.id = p.user_id
    WHERE p.deleted_at IS NULL AND u.provider = 'demo'
)
UPDATE post p SET view_count = v.view_count
  FROM ranked r JOIN vals v ON v.rk = r.rk
 WHERE p.id = r.id;

COMMIT;

-- ── 확인 ────────────────────────────────────────────────────
SELECT count(*) FILTER (WHERE created_at >= LOCALTIMESTAMP - INTERVAL '3 hours')  AS new_badge,
       count(*) FILTER (WHERE created_at >= LOCALTIMESTAMP - INTERVAL '1 day')    AS daily,
       count(*) FILTER (WHERE created_at >= LOCALTIMESTAMP - INTERVAL '7 days')   AS week,
       count(*) FILTER (WHERE created_at >= LOCALTIMESTAMP - INTERVAL '30 days')  AS month,
       count(*) AS total
  FROM post WHERE deleted_at IS NULL AND (category_id IS NULL OR category_id <> 5);
