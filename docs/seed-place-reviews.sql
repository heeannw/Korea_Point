-- ============================================================
--  관광지·맛집 별점 예시 데이터 (배포 서버용)
-- ============================================================
--  왜 필요한가
--    별점·리뷰 칸이 비어 있으면 상세 화면이 휑하고, '방문자 평점' 정렬도 보여줄 수가 없다.
--    심사 때 빈 화면을 내보이지 않으려고 채워 둔다.
--
--  어느 DB에서 돌려도 같은 결과가 나오게
--    - 작성자를 id 로 박지 않는다. provider='demo' + provider_id 로 찾고, 없으면 여기서 만든다.
--      (서버에 이미 같은 provider_id 가 있으면 그 계정을 그대로 쓴다. 중복으로 만들지 않는다.)
--    - 장소는 관광공사 contentId 로 가리킨다 — 어느 환경에서나 같은 값이다.
--    - 이미 있는 리뷰는 건드리지 않는다. 여러 번 돌려도 결과가 같다.
--
--  리뷰 언어를 섞어 둔 이유
--    리뷰 글은 번역을 타지 않는다(PlaceReviewService 에 번역 호출이 없다). 전부 한국어로 넣으면
--    영어·일본어 화면에서 리뷰만 한국어로 남는다. 그래서 국적이 다른 작성자를 섞었다 —
--    외국인 대상 서비스라는 컨셉과도 맞고, 어느 언어 화면에서도 읽을 수 있는 리뷰가 보인다.
--
--  한 사람이 같은 장소에 리뷰는 하나만 남길 수 있다(uq_place_review_user_content).
--  그래서 장소마다 작성자를 다르게 골랐다.
-- ============================================================

SET client_encoding = 'UTF8';
\set ON_ERROR_STOP on

BEGIN;

-- ── 1) 리뷰를 쓸 사람들. 이미 있으면 그대로 두고, 없는 사람만 만든다 ──
INSERT INTO users (id, provider, provider_id, name, email, role, point,
                   account_status, nationality, nationality_prompted, created_at)
SELECT nextval('user_sequence'), 'demo', v.pid, v.nm,
       v.pid || '@demo.koreapoint.invalid', 'USER', 0, 'ACTIVE', v.nat, true, NOW()
FROM (VALUES
    ('rv-ko-01', '바다보러왔어요',  NULL),
    ('rv-ko-02', '국밥순례자',      NULL),
    ('rv-ko-03', '골목산책',        NULL),
    ('rv-ko-04', '노을수집가',      NULL),
    ('rv-en-01', 'Emily Carter',    'US'),
    ('rv-en-02', 'Liam O''Connor',  'GB'),
    ('rv-ja-01', 'Kenji Watanabe',  'JP'),
    ('rv-ja-02', 'Yuki Nakamura',   'JP'),
    ('rv-zh-01', 'Wei Zhang',       'CN'),
    ('rv-zh-02', 'Li Chen',         'CN'),
    ('rv-fr-01', 'Sophie Laurent',  'FR')
) AS v(pid, nm, nat)
WHERE NOT EXISTS (
    SELECT 1 FROM users u WHERE u.provider = 'demo' AND u.provider_id = v.pid
);

-- ── 2) 리뷰 ──
INSERT INTO place_review (content_id, content_type_id, user_id, rating, text, created_at)
SELECT v.cid, v.ctype, u.id, v.rating, v.text, NOW() - v.ago
FROM (VALUES
  -- 해운대해수욕장
  ('126081','12','rv-ko-01', 5, '아침 일찍 가면 사람이 적어서 좋아요. 모래가 곱고 산책로도 잘 되어 있습니다.', INTERVAL '31 days'),
  ('126081','12','rv-en-01', 5, 'Wide, clean beach right next to the subway. The sunset here was the highlight of my trip.', INTERVAL '24 days'),
  ('126081','12','rv-ja-01', 4, '地下鉄の駅から歩いてすぐです。夏はかなり混みますが、朝は静かでした。', INTERVAL '18 days'),
  ('126081','12','rv-zh-01', 5, '沙滩很干净，晚上的灯光和海浪声都很棒，周边餐厅也多。', INTERVAL '9 days'),
  ('126081','12','rv-ko-02', 4, '주차가 힘들어서 지하철 타고 갔습니다. 저녁에 조명 켜지면 분위기 좋아요.', INTERVAL '4 days'),

  -- 광안리해수욕장
  ('126078','12','rv-ko-03', 5, '광안대교 야경 보려고 갔는데 기대 이상이었어요. 해변 카페에서 한참 앉아 있었습니다.', INTERVAL '27 days'),
  ('126078','12','rv-en-02', 5, 'The bridge lights up at night and it is genuinely beautiful. Plenty of cafes facing the sea.', INTERVAL '20 days'),
  ('126078','12','rv-zh-02', 4, '广安大桥的夜景非常漂亮，海边咖啡厅也很多，就是周末人有点多。', INTERVAL '12 days'),
  ('126078','12','rv-ja-02', 5, '広安大橋の夜景が本当にきれいでした。海沿いのカフェでゆっくりできます。', INTERVAL '6 days'),

  -- 부산 감천문화마을
  ('1997221','12','rv-ko-04', 4, '골목마다 색이 달라서 사진 찍기 좋아요. 다만 언덕이라 편한 신발 신고 가세요.', INTERVAL '29 days'),
  ('1997221','12','rv-en-01', 4, 'Colourful hillside village, great for photos. Be ready for a lot of stairs though.', INTERVAL '21 days'),
  ('1997221','12','rv-ja-01', 5, '坂は多いですが、路地ごとに色が違っていて写真をたくさん撮りました。', INTERVAL '15 days'),
  ('1997221','12','rv-ko-01', 4, '주말엔 사람이 많아서 평일 오전에 가는 걸 추천합니다.', INTERVAL '7 days'),

  -- 태종대
  ('126658','12','rv-ko-02', 5, '절벽이랑 바다가 시원하게 보여요. 다누비 열차 타면 편하게 한 바퀴 돌 수 있습니다.', INTERVAL '26 days'),
  ('126658','12','rv-zh-01', 4, '悬崖和大海的景色很壮观，可以坐园内小火车，省力很多。', INTERVAL '17 days'),
  ('126658','12','rv-en-02', 5, 'Dramatic cliffs and sea views. The little park train saves a lot of walking.', INTERVAL '10 days'),

  -- 범어사
  ('126148','12','rv-ko-03', 5, '도심에서 가까운데도 조용합니다. 가을에 단풍 들 때 특히 좋았어요.', INTERVAL '28 days'),
  ('126148','12','rv-ja-02', 5, '市内から近いのに静かで、落ち着いた時間を過ごせました。', INTERVAL '19 days'),
  ('126148','12','rv-en-01', 4, 'A calm mountain temple, easy to reach from the city. Worth the short uphill walk.', INTERVAL '8 days'),

  -- 흰여울문화마을
  ('2684712','12','rv-ko-04', 5, '바다 바로 옆 골목길이라 걷는 내내 풍경이 좋아요. 카페도 많습니다.', INTERVAL '23 days'),
  ('2684712','12','rv-zh-02', 5, '沿海的小巷，一路都能看到海，咖啡厅也很有特色。', INTERVAL '14 days'),
  ('2684712','12','rv-en-02', 4, 'Narrow lanes right above the sea. Lovely walk, but the paths are quite steep.', INTERVAL '5 days'),

  -- 해동용궁사
  ('126848','12','rv-ko-01', 5, '바다 바로 옆에 있는 절이라 다른 곳과 분위기가 완전히 달라요. 사진이 잘 나옵니다.', INTERVAL '25 days'),
  ('126848','12','rv-ja-01', 5, '海のすぐそばのお寺で、他では見られない景色でした。写真映えします。', INTERVAL '16 days'),
  ('126848','12','rv-en-01', 5, 'A temple built right on the rocks by the sea. Unlike anywhere else I have visited.', INTERVAL '11 days'),
  ('126848','12','rv-fr-01', 4, 'Un temple posé sur les rochers, face à la mer. Beaucoup de marches, mais la vue en vaut la peine.', INTERVAL '3 days'),

  -- 해운대 동백섬
  ('987810','12','rv-ko-02', 4, '해운대에서 이어지는 산책로가 잘 되어 있어요. 한 바퀴 30분이면 충분합니다.', INTERVAL '22 days'),
  ('987810','12','rv-zh-01', 4, '可以从海云台直接走过来，散步路线很舒服，大约半小时一圈。', INTERVAL '13 days'),

  -- 용두산공원
  ('126121','12','rv-ko-03', 4, '부산타워 올라가면 시내가 한눈에 보여요. 남포동 구경하고 올라가기 좋습니다.', INTERVAL '24 days'),
  ('126121','12','rv-en-02', 4, 'Good city view from the tower, and an easy stop after walking around Nampo-dong.', INTERVAL '9 days'),

  -- 보수동 책방골목
  ('1608633','12','rv-ko-04', 4, '오래된 책방이 줄지어 있어서 구경하는 재미가 있어요. 조용히 둘러보기 좋습니다.', INTERVAL '20 days'),
  ('1608633','12','rv-ja-02', 4, '古本屋が並ぶ路地で、のんびり見て回るのが楽しかったです。', INTERVAL '6 days'),

  -- 송정해수욕장
  ('126080','12','rv-ko-01', 4, '해운대보다 한적해서 좋아요. 서핑하는 사람 구경하는 재미가 있습니다.', INTERVAL '18 days'),
  ('126080','12','rv-en-01', 4, 'Quieter than Haeundae and popular with surfers. A nice place to slow down.', INTERVAL '7 days'),

  -- 전포카페거리
  ('1805965','12','rv-ko-02', 4, '골목마다 개성 있는 카페가 많아요. 커피 좋아하면 한나절은 그냥 갑니다.', INTERVAL '17 days'),
  ('1805965','12','rv-zh-02', 4, '每条小巷都有特色咖啡厅，喜欢咖啡的人可以待上半天。', INTERVAL '8 days'),

  -- 부산시민공원
  ('2663341','12','rv-ko-03', 4, '도심 한가운데인데 넓고 조용해요. 아이들 물놀이 시설도 잘 되어 있습니다.', INTERVAL '15 days'),
  ('2663341','12','rv-fr-01', 4, 'Un grand parc très bien entretenu en plein centre-ville. Parfait pour souffler entre deux visites.', INTERVAL '4 days'),

  -- 이기대
  ('1945309','12','rv-ko-04', 5, '해안 산책로가 진짜 좋아요. 광안대교가 계속 보여서 걷는 내내 시원합니다.', INTERVAL '19 days'),
  ('1945309','12','rv-ja-01', 5, '海沿いの遊歩道が素晴らしく、広安大橋を眺めながら歩けます。', INTERVAL '10 days'),

  -- 부산 자갈치시장
  ('132190','38','rv-ko-01', 4, '활어 종류가 정말 많아요. 2층에서 바로 회 떠서 먹을 수 있습니다.', INTERVAL '21 days'),
  ('132190','38','rv-en-02', 4, 'Busy fish market — pick your seafood downstairs and eat it upstairs. A great experience.', INTERVAL '12 days'),
  ('132190','38','rv-zh-01', 4, '海鲜种类非常多，可以在楼上现做现吃，建议和当地人一起去。', INTERVAL '5 days'),

  -- 국제시장
  ('132191','38','rv-ko-02', 4, '먹거리 골목이 제일 재밌어요. 씨앗호떡은 꼭 드셔 보세요.', INTERVAL '16 days'),
  ('132191','38','rv-ja-02', 4, '屋台の通りが一番楽しかったです。シアッホットクは必食でした。', INTERVAL '6 days'),

  -- 부평깡통시장
  ('1878218','38','rv-ko-03', 4, '저녁에 야시장 열리면 분위기가 확 살아나요. 먹을 게 정말 많습니다.', INTERVAL '14 days'),
  ('1878218','38','rv-zh-02', 5, '晚上的夜市很热闹，小吃种类多，价格也不贵。', INTERVAL '3 days'),

  -- 맛집: 동래할매파전
  ('2565287','39','rv-ko-04', 5, '파전이 두툼하고 바삭해요. 동래 쪽 갔을 때 한 번은 들를 만합니다.', INTERVAL '20 days'),
  ('2565287','39','rv-en-01', 4, 'Thick, crispy seafood pancake. A proper Busan classic, worth the detour.', INTERVAL '9 days'),

  -- 맛집: 쌍둥이돼지국밥 본점
  ('2759609','39','rv-ko-02', 5, '국물이 깔끔해서 처음 드시는 분도 부담 없어요. 부추 듬뿍 넣어 드세요.', INTERVAL '18 days'),
  ('2759609','39','rv-ja-01', 4, 'スープがすっきりしていて食べやすかったです。ニラをたっぷり入れるのがおすすめ。', INTERVAL '11 days'),
  ('2759609','39','rv-zh-01', 4, '汤头清爽，第一次吃猪肉汤饭的人也不会觉得腻。', INTERVAL '4 days'),

  -- 맛집: 해운대 가야밀면
  ('2786779','39','rv-ko-01', 4, '여름에 밀면 한 그릇이면 더위가 가셔요. 면이 쫄깃합니다.', INTERVAL '17 days'),
  ('2786779','39','rv-en-02', 4, 'Cold wheat noodles, perfect on a hot day — chewy and refreshing.', INTERVAL '7 days'),

  -- 맛집: 금수복국 해운대본점
  ('2778487','39','rv-ko-03', 5, '해장으로 최고예요. 국물이 맑고 깔끔합니다.', INTERVAL '13 days'),
  ('2778487','39','rv-ja-02', 5, '二日酔いの朝にぴったりでした。スープが澄んでいて上品な味です。', INTERVAL '5 days'),

  -- 맛집: 해변짚불곰장어
  ('1805619','39','rv-ko-04', 4, '짚불에 구워서 향이 좋아요. 기장 쪽 가면 한 번쯤 드셔 볼 만합니다.', INTERVAL '12 days'),
  ('1805619','39','rv-zh-02', 4, '用稻草火烤的，香味很特别，来机张的话值得试一次。', INTERVAL '2 days')
) AS v(cid, ctype, pid, rating, text, ago)
JOIN users u ON u.provider = 'demo' AND u.provider_id = v.pid
WHERE NOT EXISTS (
    SELECT 1 FROM place_review r WHERE r.user_id = u.id AND r.content_id = v.cid
);

COMMIT;

-- ── 확인 ──
SELECT count(*) AS "이 스크립트가 만든 계정 수"
  FROM users WHERE provider = 'demo' AND provider_id LIKE 'rv-%';

SELECT content_id AS "장소번호", count(*) AS "리뷰수", round(avg(rating), 1) AS "평균별점"
  FROM place_review
 WHERE user_id IN (SELECT id FROM users WHERE provider = 'demo' AND provider_id LIKE 'rv-%')
 GROUP BY content_id
 ORDER BY count(*) DESC, content_id;

SELECT count(*) AS "리뷰 전체"
  FROM place_review
 WHERE user_id IN (SELECT id FROM users WHERE provider = 'demo' AND provider_id LIKE 'rv-%');
