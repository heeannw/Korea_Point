-- ============================================================
--  커뮤니티 예시 데이터 (배포 서버용)
-- ============================================================
--  로컬에서 쓰던 스크립트는 user_id·category_id 를 숫자로 박아 뒀는데,
--  그 값은 서버마다 다르다. 여기서는 카테고리를 '이름'으로, 작성자를
--  'provider_id' 로 찾아 쓴다. 어느 DB 에서 돌려도 같은 결과가 나온다.
--
--  여러 번 돌려도 안전하다. 이미 있으면 건너뛴다.
--
--  실행:  psql -U postgres -d korea_point -v ON_ERROR_STOP=1 -f seed-demo-community.sql
--  되돌리기: 파일 맨 아래 주석 참고
-- ============================================================

BEGIN;

-- ── 1. 데모 계정 ─────────────────────────────────────────────
--  실제 이용자에게 글을 붙이지 않으려고 전용 계정을 만든다.
--  provider='demo' 라 OAuth 로 로그인될 수 없고, 지울 때도 이것만 지우면 된다.
--  이메일 도메인 .invalid 는 표준상 실제로 존재할 수 없는 주소다(RFC 2606).
INSERT INTO users (id, provider, provider_id, name, email, role, point,
                   account_status, nationality_prompted, created_at)
SELECT nextval('user_sequence'), 'demo', v.pid, v.nm,
       v.pid || '@demo.koreapoint.invalid', v.role, v.pt, 'ACTIVE', true, NOW()
FROM (VALUES
    ('demo-choi',  '최수진', 'USER',     0),
    ('demo-park',  '박민수', 'USER',     0),
    ('demo-kim',   '김영희', 'USER',     0),
    ('demo-lee',   '이지은', 'USER',     0),
    ('demo-jung',  '정우성', 'USER',     0),
    ('demo-han',   '한지민', 'USER',     0),
    -- 홍보 글은 사업자 회원만 쓸 수 있다. 데모에서도 그 규칙을 지킨다.
    ('demo-owner', '부산상회', 'OWNER', 3000)
) AS v(pid, nm, role, pt)
WHERE NOT EXISTS (
    SELECT 1 FROM users u WHERE u.provider = 'demo' AND u.provider_id = v.pid
);

-- ── 2. 일반 글 ───────────────────────────────────────────────
INSERT INTO post (id, user_id, category_id, title, content, title_en, content_en,
                  place_title, view_count, comment_count, created_at)
SELECT nextval('post_sequence'), u.id, c.id, v.title, v.content, v.title_en, v.content_en,
       v.place, v.views, 0, NOW() - v.ago
FROM (VALUES
 ('관광', 'demo-choi',
  '감천문화마을 다녀왔습니다, 골목마다 색이 달라요',
  '<p>주말에 감천문화마을 다녀왔습니다. 사진으로만 보던 곳인데 실제로 가니까 골목마다 벽 색이 달라서 걷는 재미가 있었어요.</p><p>어린왕자 포토존은 줄이 길어서 20분 정도 기다렸습니다. 오전 10시쯤 가면 사람이 훨씬 적다고 하네요.</p><p>경사가 꽤 있어서 편한 신발 꼭 신고 가세요.</p>',
  'Visited Gamcheon Culture Village',
  '<p>Every alley is painted a different color, which makes just walking around fun.</p><p>The Little Prince photo spot had a 20-minute line. It is much quieter around 10am.</p><p>The slopes are steep, so wear proper shoes.</p>',
  '감천문화마을', 842, INTERVAL '2 days'),

 ('교통', 'demo-park',
  '김해공항에서 서면까지 제일 빠른 방법 정리',
  '<p><strong>1. 경전철 + 지하철</strong> — 사상역에서 2호선 환승. 약 50분, 1,700원.</p><p><strong>2. 리무진버스</strong> — 서면까지 한 번에. 약 45분, 7,000원. 배차 20~30분.</p><p><strong>3. 택시</strong> — 25~35분, 2만원 안팎. 밤 늦게 도착하면 사실상 이것뿐입니다.</p><p>세 명 이상이면 택시가 오히려 낫습니다.</p>',
  'Fastest ways from Gimhae Airport to Seomyeon',
  '<p>Light rail + subway: about 50 min, 1,700 KRW. Limousine bus: 45 min, 7,000 KRW. Taxi: 25-35 min, around 20,000 KRW.</p><p>With three or more people the taxi actually wins.</p>',
  NULL, 1204, INTERVAL '4 days'),

 ('맛집', 'demo-kim',
  '돼지국밥 처음 먹어본 외국인 친구 반응',
  '<p>독일에서 온 친구가 부산 오면 돼지국밥은 꼭 먹어야 한다고 해서 서면 쪽으로 데려갔습니다.</p><p>처음엔 국물 색 보고 망설이더니 부추 넣고 새우젓 풀어주니까 한 그릇 다 비웠어요. "국물이 라멘보다 깔끔하다"는 게 첫 반응이었습니다.</p>',
  'My foreign friend tried dwaeji gukbap for the first time',
  '<p>He hesitated at the color of the broth, but after adding chives and salted shrimp he finished the whole bowl. His first comment was that the broth is cleaner than ramen.</p>',
  '서면', 417, INTERVAL '1 day'),

 ('자유', 'demo-lee',
  '부산 3박 4일 처음인데 코스 좀 봐주세요',
  '<p>1일차: 김해공항 → 숙소(서면) → 서면 저녁<br>2일차: 해운대 → 동백섬 → 광안리 야경<br>3일차: 감천문화마을 → 자갈치 → 남포동<br>4일차: 오전 카페 → 공항</p><p>2일차가 좀 빡빡한가요? 이동시간을 잘 몰라서요.</p>',
  'First time in Busan, 3 nights — please check my itinerary',
  '<p>Day 2 feels tight. I am not sure about travel times.</p>',
  NULL, 132, INTERVAL '3 days'),

 ('관광', 'demo-jung',
  '광안대교 야경은 여기서 봐야 합니다',
  '<p>광안리 해변 한가운데서 보는 것보다, 민락수변공원 쪽으로 조금 걸어가면 다리 전체가 한 프레임에 들어옵니다.</p><p>해 지고 30분 뒤가 제일 예뻐요. 하늘이 완전히 까매지기 전에 파란빛이 남아 있을 때입니다.</p>',
  'The best spot to see Gwangan Bridge at night',
  '<p>Walk toward Millak Waterside Park and the whole bridge fits in one frame. Thirty minutes after sunset is the best moment.</p>',
  '광안리해수욕장', 1203, INTERVAL '3 days'),

 ('맛집', 'demo-han',
  '자갈치시장 회 가격 정리해봤어요',
  '<p>광어 1kg 3~4만원 / 우럭 1kg 2.5~3만원 / 전복 5마리 2만원선</p><p>2층 초장집에서 상차림비가 인당 4~5천원 따로 붙습니다. 이거 모르고 갔다가 계산할 때 당황했어요.</p>',
  'Sashimi prices at Jagalchi Market',
  '<p>The second-floor restaurants charge 4,000-5,000 KRW per person for side dishes. I did not know and was surprised at the bill.</p>',
  '자갈치시장', 621, INTERVAL '5 days'),

 ('교통', 'demo-park',
  '부산 지하철 1일권 어디서 사나요?',
  '<p>내일 하루 종일 지하철로 돌아다닐 예정인데 1일권이 있다고 들었습니다.</p><p>역 창구에서만 파는 건가요? 자동판매기에서도 되는지 궁금합니다.</p>',
  'Where do I buy the Busan subway day pass?',
  '<p>Is it only sold at station counters, or can I get it from a machine?</p>',
  NULL, 58, INTERVAL '7 hours'),

 ('자유', 'demo-choi',
  '부산 사람들은 밀면을 여름에만 먹나요?',
  '<p>겨울에 갔을 때 밀면집이 한산하길래 궁금해서요. 현지 분들은 사계절 드시나요?</p><p>냉면이랑 뭐가 다른지도 아직 잘 모르겠습니다.</p>',
  'Do people in Busan eat milmyeon only in summer?',
  '<p>The milmyeon places were quiet when I visited in winter, so I got curious.</p>',
  NULL, 388, INTERVAL '6 days'),

 ('관광', 'demo-kim',
  '송정 서핑 강습 후기 (완전 초보 기준)',
  '<p>서핑 한 번도 안 해본 상태로 갔습니다. 2시간 강습에 보드 대여까지 6만원 정도였어요.</p><p>첫 한 시간은 모래에서 자세만 연습합니다. 결론은 두 번 정도 일어섰습니다. 그 두 번이 진짜 재밌었어요.</p>',
  'Surfing lesson at Songjeong, as a complete beginner',
  '<p>Two-hour lesson with board rental was around 60,000 KRW. I stood up twice, and those two times were genuinely fun.</p>',
  '송정해수욕장', 512, INTERVAL '8 days'),

 ('맛집', 'demo-lee',
  '남포동 씨앗호떡 웨이팅 얼마나 하나요',
  '<p>평일 오후에 가려는데 줄이 많이 긴가요? 30분 이상이면 다른 데 갈까 고민 중입니다.</p>',
  'How long is the wait for seed hotteok in Nampo-dong?',
  '<p>Planning to go on a weekday afternoon. Is the line long?</p>',
  NULL, 244, INTERVAL '9 days'),

 ('교통', 'demo-jung',
  '해운대에서 태종대 대중교통으로 가는 법',
  '<p>해운대역에서 2호선 타고 서면 → 1호선 환승 → 남포역 하차 → 8번, 30번, 88번 버스로 태종대. 총 1시간 20분 정도 걸렸습니다.</p><p>생각보다 멀어서 오전에 출발하시는 게 좋습니다.</p>',
  'Getting from Haeundae to Taejongdae by public transport',
  '<p>About 1 hour 20 minutes total. It is farther than it looks, so leave in the morning.</p>',
  '태종대', 176, INTERVAL '11 days'),

 ('자유', 'demo-han',
  '비 오는 날 부산에서 할 만한 것 추천받아요',
  '<p>이번 주 내내 비 예보인데 해수욕장이랑 야경 위주로 짜놨던 일정이 다 날아갔습니다.</p><p>실내로 갈 만한 곳 있을까요?</p>',
  'Recommendations for a rainy day in Busan',
  '<p>Rain is forecast all week and my beach and night-view plans are gone. Any good indoor options?</p>',
  NULL, 95, INTERVAL '13 days')
) AS v(cat, author, title, content, title_en, content_en, place, views, ago)
JOIN category c ON c.name = v.cat
JOIN users   u ON u.provider = 'demo' AND u.provider_id = v.author
WHERE NOT EXISTS (SELECT 1 FROM post p WHERE p.title = v.title);

-- ── 3. 가게 홍보 글 ──────────────────────────────────────────
--  홍보 글은 오른쪽 레일에 뜬다. promo_until 이 노출 기간이고,
--  이 값이 비어 있으면 레일에 안 나온다.
INSERT INTO post (id, user_id, category_id, title, content, title_en, content_en,
                  place_title, view_count, comment_count, created_at, promo_until)
SELECT nextval('post_sequence'), u.id, c.id, v.title, v.content, v.title_en, v.content_en,
       v.place, v.views, 0, NOW() - v.ago, NOW() + INTERVAL '7 days'
FROM (VALUES
 ('[해운대] 새벽 5시부터 여는 해장국집입니다',
  '<p>해운대해수욕장 뒷골목에서 12년째 하고 있습니다. 새벽 5시에 문 열어서 낮 3시까지 합니다.</p><p>외국인 손님이 요즘 부쩍 늘어서 영어 메뉴판 만들어 뒀습니다.</p>',
  '[Haeundae] Hangover soup, open from 5am',
  '<p>Open 5am to 3pm. More foreign guests lately, so we made an English menu.</p>',
  '해운대해수욕장', 312, INTERVAL '20 hours'),

 ('[광안리] 오션뷰 루프탑 카페, 야경 자리 예약받습니다',
  '<p>광안대교가 정면으로 보이는 4층 루프탑입니다. 해질 무렵 자리는 미리 말씀 주시면 빼 둡니다.</p><p>담요 준비해 두었으니 바람 부는 날도 괜찮습니다.</p>',
  '[Gwangalli] Rooftop cafe with ocean view',
  '<p>Fourth-floor rooftop facing Gwangan Bridge. Tell us ahead and we will hold a sunset seat.</p>',
  '광안리해수욕장', 508, INTERVAL '2 days'),

 ('[서면] 1인 삼겹살 됩니다, 혼밥 환영',
  '<p>서면역 2번 출구에서 3분입니다. 1인분부터 주문 되고 혼자 오셔도 눈치 안 보셔도 됩니다.</p><p>바 좌석이 여덟 자리 있고, 굽는 건 저희가 해 드립니다.</p>',
  '[Seomyeon] Single-serving samgyeopsal, solo diners welcome',
  '<p>One-person portions available. We have eight bar seats and we grill it for you.</p>',
  '서면', 741, INTERVAL '4 days'),

 ('[남포동] 짐 맡아 드립니다, 하루 3천원',
  '<p>남포동 지하상가 입구에서 짐 보관소 운영합니다. 캐리어 하루 3천원, 배낭 2천원입니다.</p><p>밤 10시까지 열고 여권 확인 후 맡아 드립니다.</p>',
  '[Nampo-dong] Luggage storage, 3,000 KRW a day',
  '<p>3,000 KRW a day for a suitcase. Open until 10pm.</p>',
  '남포동', 189, INTERVAL '6 days')
) AS v(title, content, title_en, content_en, place, views, ago)
JOIN category c ON c.name = '홍보'
JOIN users   u ON u.provider = 'demo' AND u.provider_id = 'demo-owner'
WHERE NOT EXISTS (SELECT 1 FROM post p WHERE p.title = v.title);

-- ── 4. 댓글 ─────────────────────────────────────────────────
INSERT INTO comments (id, post_id, user_id, parent_id, content, content_en, created_at)
SELECT nextval('comment_sequence'), p.id, u.id, NULL, v.content, v.content_en, NOW() - v.ago
FROM (VALUES
 ('감천문화마을 다녀왔습니다, 골목마다 색이 달라요', 'demo-park',
  '저도 지난달에 갔는데 진짜 오르막이 장난 아니더라고요. 물 챙겨가세요.',
  'The uphill is no joke. Bring water.', INTERVAL '45 hours'),
 ('감천문화마을 다녀왔습니다, 골목마다 색이 달라요', 'demo-han',
  '오전 10시 팁 감사합니다. 다음 주에 가는데 참고할게요.',
  'Thanks for the 10am tip.', INTERVAL '29 hours'),
 ('김해공항에서 서면까지 제일 빠른 방법 정리', 'demo-choi',
  '정리 감사합니다. 리무진 막차가 몇 시인지 아시나요?',
  'Do you know when the last limousine bus runs?', INTERVAL '94 hours'),
 ('김해공항에서 서면까지 제일 빠른 방법 정리', 'demo-jung',
  '캐리어 두 개면 경전철 환승 진짜 힘듭니다. 계단 구간이 있어요.',
  'With two suitcases the transfer is rough.', INTERVAL '72 hours'),
 ('돼지국밥 처음 먹어본 외국인 친구 반응', 'demo-lee',
  '새우젓 설명하는 게 제일 어렵죠. 저는 그냥 소금이라고 했습니다.',
  'Explaining salted shrimp is the hardest part.', INTERVAL '20 hours'),
 ('부산 3박 4일 처음인데 코스 좀 봐주세요', 'demo-jung',
  '2일차 괜찮습니다. 해운대에서 동백섬은 걸어서 20분이에요.',
  'Day 2 is fine. Dongbaek Island is a 20-minute walk.', INTERVAL '70 hours'),
 ('부산 3박 4일 처음인데 코스 좀 봐주세요', 'demo-kim',
  '3일차가 오히려 빡셉니다. 감천에서 자갈치까지 버스로 30분 넘게 걸려요.',
  'Day 3 is the tight one.', INTERVAL '66 hours'),
 ('부산 3박 4일 처음인데 코스 좀 봐주세요', 'demo-han',
  '마지막 날 공항 가는 시간 넉넉히 잡으세요. 아침에 도로가 막힙니다.',
  'Leave plenty of time for the airport.', INTERVAL '24 hours'),
 ('광안대교 야경은 여기서 봐야 합니다', 'demo-park',
  '민락수변공원 진짜 좋습니다. 돗자리 깔고 앉아 있는 분들 많아요.',
  'Millak Waterside Park is great.', INTERVAL '71 hours'),
 ('광안대교 야경은 여기서 봐야 합니다', 'demo-choi',
  '해 지고 30분 팁 유용하네요. 저는 항상 너무 늦게 갔었습니다.',
  'The 30-minutes-after-sunset tip is useful.', INTERVAL '10 hours'),
 ('자갈치시장 회 가격 정리해봤어요', 'demo-kim',
  '상차림비 진짜 모르고 가면 당황합니다. 미리 물어보는 게 맞아요.',
  'The side dish charge catches people off guard.', INTERVAL '117 hours'),
 ('부산 지하철 1일권 어디서 사나요?', 'demo-jung',
  '역 자동판매기에서도 살 수 있습니다. 5천원이고 당일 자정까지예요.',
  'You can buy it from the station machines. 5,000 KRW.', INTERVAL '5 hours'),
 ('부산 사람들은 밀면을 여름에만 먹나요?', 'demo-lee',
  '사계절 먹습니다. 다만 손님은 확실히 여름에 몰립니다.',
  'People eat it year-round, but summer is much busier.', INTERVAL '140 hours'),
 ('부산 사람들은 밀면을 여름에만 먹나요?', 'demo-han',
  '냉면은 메밀, 밀면은 밀가루라 식감이 다릅니다.',
  'Naengmyeon uses buckwheat, milmyeon uses wheat.', INTERVAL '120 hours'),
 ('송정 서핑 강습 후기 (완전 초보 기준)', 'demo-park',
  '두 번 일어서면 첫날치고 잘하신 겁니다. 저는 한 번도 못 섰어요.',
  'Standing up twice on day one is good.', INTERVAL '168 hours'),
 ('남포동 씨앗호떡 웨이팅 얼마나 하나요', 'demo-choi',
  '평일 오후면 10분 안쪽입니다. 주말은 30분 넘게 서봤어요.',
  'Under 10 minutes on a weekday afternoon.', INTERVAL '214 hours'),
 ('해운대에서 태종대 대중교통으로 가는 법', 'demo-kim',
  '다누비열차 줄이 길면 그냥 걸어도 됩니다. 경치는 더 좋아요.',
  'You can just walk. Better views.', INTERVAL '240 hours'),
 ('비 오는 날 부산에서 할 만한 것 추천받아요', 'demo-jung',
  '부산현대미술관이랑 국제시장 쪽 아케이드 추천합니다. 둘 다 비 안 맞아요.',
  'Busan MoCA and the covered arcades near Gukje Market.', INTERVAL '288 hours'),
 ('비 오는 날 부산에서 할 만한 것 추천받아요', 'demo-han',
  '온천 좋습니다. 동래 쪽에 오래된 곳들 많아요.',
  'The hot springs are good, especially around Dongnae.', INTERVAL '264 hours')
) AS v(post_title, author, content, content_en, ago)
JOIN post  p ON p.title = v.post_title
JOIN users u ON u.provider = 'demo' AND u.provider_id = v.author
WHERE NOT EXISTS (
    SELECT 1 FROM comments c WHERE c.post_id = p.id AND c.content = v.content
);

-- ── 5. 답글 ─────────────────────────────────────────────────
--  부모 댓글이 위 문장으로 이미 들어간 뒤라야 찾을 수 있어 문장을 나눈다.
INSERT INTO comments (id, post_id, user_id, parent_id, content, content_en, created_at)
SELECT nextval('comment_sequence'), parent.post_id, u.id, parent.id,
       v.content, v.content_en, NOW() - v.ago
FROM (VALUES
 ('정리 감사합니다. 리무진 막차가 몇 시인지 아시나요?', 'demo-park',
  '작년 기준으로 22시 조금 넘어서 끊겼습니다. 요즘은 확인이 필요할 것 같네요.',
  'It ended a little after 22:00 last year.', INTERVAL '91 hours'),
 ('3일차가 오히려 빡셉니다. 감천에서 자갈치까지 버스로 30분 넘게 걸려요.', 'demo-lee',
  '아 그렇군요. 감천을 오전에 넣어야겠네요. 감사합니다.',
  'Good to know. I will move Gamcheon to the morning.', INTERVAL '52 hours'),
 ('역 자동판매기에서도 살 수 있습니다. 5천원이고 당일 자정까지예요.', 'demo-park',
  '경전철은 별도입니다. 1일권으로는 안 되고 따로 결제해야 해요.',
  'The light rail is separate.', INTERVAL '3 hours')
) AS v(parent_content, author, content, content_en, ago)
JOIN comments parent ON parent.content = v.parent_content
JOIN users    u      ON u.provider = 'demo' AND u.provider_id = v.author
WHERE NOT EXISTS (
    SELECT 1 FROM comments c WHERE c.post_id = parent.post_id AND c.content = v.content
);

-- ── 6. 댓글 수 맞추기 ────────────────────────────────────────
--  화면에 [3] 이라고 떠 있는데 댓글이 없으면 그 자체가 버그처럼 보인다.
UPDATE post p
SET comment_count = (SELECT count(*) FROM comments c
                     WHERE c.post_id = p.id AND c.deleted_at IS NULL)
WHERE EXISTS (SELECT 1 FROM comments c WHERE c.post_id = p.id);

COMMIT;

-- 확인
SELECT c.name AS 카테고리, count(*) AS 글수
FROM post p JOIN category c ON c.id = p.category_id
WHERE p.deleted_at IS NULL
GROUP BY c.name, c.sort_order ORDER BY c.sort_order;

-- ============================================================
--  되돌리기 — 데모 계정이 쓴 것만 지운다. 실제 이용자 글은 건드리지 않는다.
-- ============================================================
--  BEGIN;
--  DELETE FROM comments WHERE user_id IN (SELECT id FROM users WHERE provider = 'demo');
--  DELETE FROM comments WHERE post_id IN (
--      SELECT id FROM post WHERE user_id IN (SELECT id FROM users WHERE provider = 'demo'));
--  DELETE FROM post     WHERE user_id IN (SELECT id FROM users WHERE provider = 'demo');
--  DELETE FROM users    WHERE provider = 'demo';
--  COMMIT;
