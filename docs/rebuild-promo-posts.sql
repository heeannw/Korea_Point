-- docs/rebuild-promo-posts.py 가 만든 파일. 손으로 고치지 말고 다시 생성할 것.
-- 홍보 글의 사진·이름·주소·소개를 같은 가게 한 건에서 가져와 맞춘다.

BEGIN;

UPDATE post SET
    title = '클라우드32 · 서양식',
    content = '<p>탁 트인 바다 위에서 부산의 아름다운 전망을 한눈에 담을 수 있는 클라우드32는 품격 있는 다이닝을 즐길 수 있는 정통 이탈리안 레스토랑이다. 오션뷰와 도시의 풍경이 어우러져 식사 시간 내내 특별한 분위기를 선사한다.</p><img src="https://tong.visitkorea.or.kr/cms/resource/35/3585635_image2_1.jpg" alt="클라우드32"><p>주소: 부산광역시 해운대구 마린시티3로 52</p><p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>',
    place_title = '해운대구',
    place_content_id = '843489',
    place_content_type_id = '39',
    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL
  WHERE id = 15;

UPDATE post SET
    title = '사미헌 · 한식',
    content = '<p>부산 진구에 위치한 사미헌은 한우 숯불구이, 갈비탕 등을 판매하는 한식 전문점이다. 모든 과정은 숙련된 한우 명장의 손길로 깐깐한 작업을 거치며, 정직한 식문화 철학으로 국내외 인사들이 방문하는 곳이다.</p><img src="https://tong.visitkorea.or.kr/cms/resource/78/3583678_image2_1.jpg" alt="사미헌"><p>주소: 부산광역시 부산진구 서면문화로 19</p><p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>',
    place_title = '부산진구',
    place_content_id = '841730',
    place_content_type_id = '39',
    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL
  WHERE id = 16;

UPDATE post SET
    title = '이가네떡볶이 · 한식',
    content = '<p>이가네 떡볶이는 예능프로그램에서 우승하면서 더욱 유명해진 분식집이다. 특히 무 떡볶이로 유명하며 무를 오랫동안 끓여 무에서 나온 즙을 활용해 떡볶이 국물을 만들고 있다.</p><img src="https://tong.visitkorea.or.kr/cms/resource/61/3582861_image2_1.jpg" alt="이가네떡볶이"><p>주소: 부산광역시 중구 부평1길 48</p><p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>',
    place_title = '중구',
    place_content_id = '2726193',
    place_content_type_id = '39',
    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL
  WHERE id = 17;

UPDATE post SET
    title = '브라운피크닉 · 카페/전통찻집',
    content = '<p>부산 기장군 철마에 위치한 브라운피크닉은 동화 속에 나오는 집처럼 예쁜 공간이다. 숲 속에 조그맣게 지어진 하얀색 건물 외관이 주변 자연과 조화를 이룬다.</p><img src="https://tong.visitkorea.or.kr/cms/resource/15/2753515_image2_1.jpg" alt="브라운피크닉"><p>주소: 부산광역시 기장군 철마면 개좌로 763-25</p><p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>',
    place_title = '기장군',
    place_content_id = '2752996',
    place_content_type_id = '39',
    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL
  WHERE id = 18;

UPDATE post SET
    title = '카페 만디 · 카페/전통찻집',
    content = '<p>부산 사하구 괴정동 산 중턱에 위치한 ‘카페 만디’는 일상에서 벗어나 조용한 휴식을 즐기기에 안성맞춤인 감성적인 공간이다. 산길을 조금만 오르면 바로 눈앞에 넓은 전용 주차장이 나타나며, 멀리서도 눈에 띄는 이국적인 입구가 마치 유럽 동화 속을 떠올리게 한다.</p><img src="https://tong.visitkorea.or.kr/cms/resource/06/2852406_image2_1.jpg" alt="카페 만디"><p>주소: 부산광역시 사하구 오작로 104-7 (괴정동)</p><p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>',
    place_title = '사하구',
    place_content_id = '2852408',
    place_content_type_id = '39',
    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL
  WHERE id = 105;

UPDATE post SET
    title = '듀스포레 · 카페/전통찻집',
    content = '<p>부산 남구에 위치한 브런치 카페 ''듀스포레''는 케이크, 수제 잼, 쿠키 등 직접 만든 건강한 디저트와 함께 수프, 샐러드, 샌드위치, 파스타 등 다양한 브런치 메뉴를 제공한다. 실내는 넓고 밝은 분위기로 꾸며져 있어 편안하게 식사와 휴식을 즐길 수 있으며, 잔디정원에는 여러 개의 야외 테이블이 마련돼 도심 속에서도 녹지 공간의 여유를 느낄 수 있다.</p><img src="https://tong.visitkorea.or.kr/cms/resource/40/2891740_image2_1.jpg" alt="듀스포레"><p>주소: 부산광역시 남구 황령대로319번가길 193 (대연동)</p><p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>',
    place_title = '남구',
    place_content_id = '2891745',
    place_content_type_id = '39',
    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL
  WHERE id = 106;

UPDATE post SET
    title = '은하갈비 · 한식',
    content = '<p>초량동에 위치한 은하갈비는 돼지갈비가 맛있기로 유명한 곳이다. 초량 돼지갈비 골목의 역사 초창기부터 지금까지 동일한 상호로 장사를 하는 유일한 식당이다.</p><img src="https://tong.visitkorea.or.kr/cms/resource/79/2870379_image2_1.JPG" alt="은하갈비"><p>주소: 부산광역시 동구 초량중로 86</p><p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>',
    place_title = '동구',
    place_content_id = '2870394',
    place_content_type_id = '39',
    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL
  WHERE id = 107;

UPDATE post SET
    title = '홍순덕 전포양곱창 · 한식',
    content = '<p>홍순덕 전포양곱창은 50년 이상의 전통을 자랑하는 양 곱창구이 전문점이다. 부산에는 곱창 좋아하는 사람이라면 누구나 알만한 유명한 곳으로 맛도 있고, 양도 푸짐하다.</p><img src="https://tong.visitkorea.or.kr/cms/resource/29/2869329_image2_1.JPG" alt="홍순덕 전포양곱창"><p>주소: 부산광역시 수영구 과정로16번길 23</p><p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>',
    place_title = '수영구',
    place_content_id = '2869343',
    place_content_type_id = '39',
    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL
  WHERE id = 108;

UPDATE post SET
    title = '영남냉면밀면 · 한식',
    content = '<p>영남냉면밀면은 벚꽃 명소로 유명한 부산시 서구 동대신동, 삼익 아파트와 서여고 사이에 있다. 부산 3대 밀면집으로 34년 이상의 전통을 자랑하는 곳이다.</p><img src="https://tong.visitkorea.or.kr/cms/resource/07/2867607_image2_1.JPG" alt="영남냉면밀면"><p>주소: 부산광역시 서구 보수대로201번길 7</p><p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>',
    place_title = '서구',
    place_content_id = '2867626',
    place_content_type_id = '39',
    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL
  WHERE id = 109;

UPDATE post SET
    title = '디저트시네마 · 카페/전통찻집',
    content = '<p>디저트시네마는 매일 영업하지 않아서 사람들을 안달나게 하는 베이커리 맛집이다. 2년 연속 블루리본서베이에 선정되었을 만큼 빵지 순례의 필수 코스로 손꼽힌다.</p><img src="https://tong.visitkorea.or.kr/cms/resource/10/2759810_image2_1.jpg" alt="디저트시네마"><p>주소: 부산광역시 연제구 쌍미천로 32-1 (연산동)</p><p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>',
    place_title = '연제구',
    place_content_id = '2759629',
    place_content_type_id = '39',
    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL
  WHERE id = 110;

UPDATE post SET
    title = '명지첫집 · 한식',
    content = '<p>40년 경력의 2대를 이어온 명지첫집은 좋은 소금으로 만든 9가지 젓갈과 명지 대파로 맛을 더한 보쌈과 족발 요리를 선보이는 집이다. 수육과 갖가지 반찬이 된장찌개와 함께 나오는 보쌈정식이 대표 메뉴이며 점심 세트메뉴에는 기본 보쌈정식 메뉴에 고등어구이 또는 젓갈 9종 중 택일할 수 있다.</p><img src="https://tong.visitkorea.or.kr/cms/resource/82/2753482_image2_1.jpg" alt="명지첫집"><p>주소: 부산광역시 강서구 명지국제6로232번길 18 (명지동)</p><p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>',
    place_title = '강서구',
    place_content_id = '2752976',
    place_content_type_id = '39',
    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL
  WHERE id = 111;

UPDATE post SET
    title = '에테르 · 카페/전통찻집',
    content = '<p>독특한 구조의 카페 에테르는 흰여울 문화마을 비탈길에 있다. 총 4층 건물로 1층~2층 실내 공간, 3층 테라스, 4층 루프탑으로 되어있는 큰 규모의 카페이다.</p><img src="https://tong.visitkorea.or.kr/cms/resource/08/2836408_image2_1.jpg" alt="에테르"><p>주소: 부산광역시 영도구 절영로 234</p><p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>',
    place_title = '영도구',
    place_content_id = '2836412',
    place_content_type_id = '39',
    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL
  WHERE id = 112;

COMMIT;
