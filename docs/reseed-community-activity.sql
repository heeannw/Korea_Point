-- 커뮤니티 데모 데이터의 '활동 흔적'을 지금 시각 기준으로 다시 뿌린다.
--
-- 왜 다시 돌려야 하나
--   시드 날짜는 넣은 순간에만 맞다. 하루 지나면 '오늘 올라온 글'이 사라지고,
--   일간 베스트와 NEW 배지가 비어 버린다. 시연 전에 한 번 돌리면 된다.
--
-- 무엇을 건드리나
--   provider='demo' 계정이 쓴 글의 created_at / updated_at / view_count 와
--   거기 달린 댓글의 시각뿐이다. 팀이 실제로 쓴 provider='local' 글과
--   홍보 글(promo_until 로 노출 기간을 산 것)은 손대지 않는다.
--
-- 되돌리기
--   backup_post_dates_* / backup_comment_dates_* / backup_post_views_* 테이블에
--   직전 값이 남는다.
--
-- 실행
--   docker exec -i korea_point-postgres-1 psql -U postgres -d korea_point \
--     -v ON_ERROR_STOP=1 -f - < docs/reseed-community-activity.sql

-- 시간대를 앱에 맞춘다.
--   created_at 은 timestamp without time zone 이고, 앱은 LocalDateTime.now() 로
--   KST 벽시계 값을 그대로 쓴다. 그런데 이 컨테이너의 세션 시간대는 UTC 라
--   NOW() 를 그냥 쓰면 앱이 아는 '지금'보다 아홉 시간 뒤가 된다. 그러면 방금
--   넣은 글이 화면에 '9시간 전'으로 뜨고 일간·NEW 가 어긋난다.
SET TIME ZONE 'Asia/Seoul';

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

-- 댓글은 글과 같은 폭만큼 같이 옮긴다. 안 그러면 글보다 이른 댓글이 생긴다.
UPDATE comments c
   SET created_at = c.created_at + (m.new_at - m.old_at),
       updated_at = CASE WHEN c.updated_at IS NULL THEN NULL
                         ELSE c.updated_at + (m.new_at - m.old_at) END
  FROM remap m WHERE c.post_id = m.id;

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
