# -*- coding: utf-8 -*-
"""부산 관광지·맛집 별점 예시 데이터를 만든다.

관광공사 부산 목록(실제 contentId)을 읽어, 장소 종류에 맞는 문장을 조합해 리뷰를 만든다.
같은 문장이 반복되지 않게 '여는 문장 + 자세한 문장 (+ 아쉬운 점)' 을 섞어 쓰고,
쓰는 사람의 국적에 맞춰 한국어·영어·일본어·중국어·프랑스어로 나눠 넣는다.
"""
import json, os, random, sys, collections

sys.stdout.reconfigure(encoding='utf-8')
SP = r'C:\Users\tjftj\AppData\Local\Temp\claude\C--Users-tjftj\3da79794-e849-4145-8722-9be031c8bf86\scratchpad'
OUT = r'C:\Korea_Point\docs\seed-place-reviews.sql'
random.seed(20260919)

# ── 관광공사 부산 목록 읽기 ─────────────────────────────────────────────
rows = []
for p in (1, 2, 3):
    f = os.path.join(SP, f'full_KorService2_{p}.json')
    if not os.path.exists(f):
        continue
    body = json.load(open(f, encoding='utf-8'))['response']['body']
    it = (body.get('items') or {}).get('item')
    if it:
        rows += it if isinstance(it, list) else [it]

FOOD_LCLS = {'FD020100': 'chinese', 'FD020200': 'japanese', 'FD020300': 'western',
             'FD020400': 'foreign', 'FD020500': 'fusion', 'FD050100': 'cafe',
             'FD050200': 'teahouse', 'FD050300': 'drink', 'FD01': 'korean',
             'FD02': 'foreign', 'FD03': 'snack', 'FD04': 'bar', 'FD05': 'cafe'}
CAFE = {'cafe', 'teahouse', 'drink'}


def food_cat(r):
    s = r.get('lclsSystm3')
    if s in FOOD_LCLS:
        return FOOD_LCLS[s]
    return FOOD_LCLS.get(r.get('lclsSystm2'), 'etc')


NATURE_WORDS = ('해수욕장', '해변', '섬', '산', '공원', '계곡', '숲', '전망대', '둘레길',
                '산책로', '해안', '포구', '온천', '호수', '유원지', '오솔길', '폭포')
TEMPLE_WORDS = ('사', '암', '절', '서원', '향교', '성당', '교회', '왕릉', '고분', '유적')


def bucket(r):
    """리뷰 문장을 고를 때 쓰는 큰 갈래."""
    t = str(r.get('contenttypeid'))
    title = r.get('title', '')
    if t == '39':
        return 'cafe' if food_cat(r) in CAFE else 'rest'
    if t == '38':
        return 'shop'
    if t == '15':
        return 'culture'
    if t == '28':
        return 'nature'
    if t == '14':
        return 'culture'
    if t == '12':
        if any(w in title for w in NATURE_WORDS):
            return 'nature'
        if any(title.endswith(w) for w in TEMPLE_WORDS):
            return 'culture'
        return 'nature' if random.random() < 0.6 else 'culture'
    return None


# ── 문장 곳간 ───────────────────────────────────────────────────────────
# OPEN = 첫 문장, DETAIL = 덧붙이는 문장, CAVEAT = 별 4점 이하에 붙는 아쉬운 점
OPEN = {
 'ko': {
  'rest': ['맛있게 잘 먹었습니다.', '현지 분들이 많이 오는 집이더라고요.', '부산 와서 여기가 제일 기억에 남아요.',
           '기대 없이 갔는데 괜찮았습니다.', '점심시간에 들렀는데 자리가 금방 찼어요.',
           '가격 대비 만족스러웠습니다.', '오래된 집 같은데 이유가 있네요.', '재방문 의사 있습니다.',
           '가족이랑 다녀왔는데 다들 잘 먹었어요.', '혼자 가서 먹기에도 부담 없었습니다.'],
  'cafe': ['커피가 괜찮았습니다.', '자리 잡고 한참 앉아 있었어요.', '분위기 보고 들어갔는데 잘 골랐네요.',
           '조용해서 쉬어 가기 좋았습니다.', '창가 자리가 특히 좋아요.', '디저트도 같이 시켰는데 괜찮았어요.',
           '동네 카페 같은데 기대 이상이었습니다.', '사진 찍기 좋은 곳이에요.',
           '산책하다가 잠깐 들렀습니다.', '직원분이 친절하셨어요.'],
  'nature': ['날씨 좋은 날 가면 정말 좋아요.', '천천히 걷기 좋았습니다.', '사진이 예쁘게 나오는 곳이에요.',
             '부산 오면 한 번은 들를 만합니다.', '생각보다 넓어서 시간 넉넉히 잡으세요.',
             '바람 쐬러 가기 좋았어요.', '아침에 가니 사람이 적어서 좋았습니다.',
             '저녁 무렵 분위기가 제일 좋아요.', '아이들이랑 가기에도 무난했습니다.',
             '지하철로 접근이 편해서 좋았어요.'],
  'culture': ['조용히 둘러보기 좋았습니다.', '생각보다 볼거리가 많았어요.', '설명이 잘 되어 있어서 이해가 쉬웠습니다.',
              '비 오는 날 가기 좋은 곳이에요.', '한 시간 정도면 충분히 둘러봅니다.',
              '부산 역사에 대해 알게 되어 좋았어요.', '규모가 아담해서 부담 없었습니다.',
              '평일에 가서 여유롭게 봤습니다.', '아이 데리고 가도 좋을 것 같아요.',
              '무료인데 이 정도면 괜찮습니다.'],
  'shop': ['구경하는 재미가 있어요.', '필요한 건 대충 다 있습니다.', '가격이 괜찮은 편이에요.',
           '사람 많은 시간 피하면 편합니다.', '선물 사기 좋았어요.',
           '골목 구경하다가 들렀습니다.', '종류가 많아서 고르는 재미가 있어요.',
           '직원분이 잘 알려주셨습니다.', '주변에 먹을 곳도 많아서 같이 다니기 좋아요.',
           '한 바퀴 돌면서 구경했습니다.'],
 },
 'en': {
  'rest': ['Really enjoyed the meal here.', 'Mostly locals inside, which is always a good sign.',
           'One of the better meals I had in Busan.', 'Went in without expectations and was pleasantly surprised.',
           'Good value for what you get.', 'Simple place, but the food was solid.'],
  'cafe': ['Good coffee and a relaxed atmosphere.', 'Ended up staying much longer than planned.',
           'Nice spot to rest between sightseeing.', 'The window seats are worth waiting for.',
           'Quiet enough to actually sit and read.', 'Friendly staff and a calm room.'],
  'nature': ['Beautiful on a clear day.', 'Lovely place for a slow walk.',
             'Worth visiting at least once while in Busan.', 'Bigger than I expected, so allow some time.',
             'Went early and had it almost to myself.', 'The light in the late afternoon is lovely.'],
  'culture': ['A calm place to wander around.', 'More to see here than I expected.',
              'The explanations were clear and easy to follow.', 'A good option on a rainy day.',
              'An hour is enough to see everything properly.', 'Learned a fair bit about the area.'],
  'shop': ['Fun just to walk around and look.', 'You can find pretty much anything here.',
           'Reasonable prices overall.', 'Good place to pick up souvenirs.',
           'Lots of variety, easy to lose track of time.', 'Staff were helpful when I asked.'],
 },
 'ja': {
  'rest': ['おいしくいただきました。', '地元の人が多いお店でした。', '釜山で食べた中でも印象に残っています。',
           '期待せずに入りましたが、よかったです。', '値段のわりに満足できました。', '素朴ですが味はしっかりしています。'],
  'cafe': ['コーヒーがおいしかったです。', '思ったより長居してしまいました。', '観光の合間に休むのにちょうどいいです。',
           '窓際の席が特によかったです。', '静かで落ち着けました。', '店員さんが親切でした。'],
  'nature': ['天気のいい日は本当にきれいです。', 'のんびり歩くのにちょうどいい場所でした。',
             '釜山に来たら一度は寄る価値があります。', '思ったより広いので時間に余裕を持って。',
             '朝早く行くと人が少なくて快適でした。', '夕方の雰囲気がいちばん良かったです。'],
  'culture': ['静かに見て回れました。', '思っていたより見どころが多かったです。',
              '説明がわかりやすかったです。', '雨の日に行くのにちょうどいい場所です。',
              '一時間あれば十分に回れます。', '釜山の歴史を少し知ることができました。'],
  'shop': ['見て回るだけでも楽しいです。', 'ひととおり何でもそろいます。', '値段も手ごろでした。',
           'お土産を買うのによかったです。', '種類が多くて迷いました。', '店員さんが丁寧に教えてくれました。'],
 },
 'zh': {
  'rest': ['味道很不错，吃得很满足。', '店里大多是本地人，感觉很地道。', '是这次釜山之行印象最深的一餐。',
           '本来没抱期待，结果意外地好吃。', '性价比很高。', '店面朴素，但味道扎实。'],
  'cafe': ['咖啡不错，环境也舒服。', '不知不觉坐了很久。', '逛累了进来休息刚刚好。',
           '靠窗的位置特别好。', '店里很安静，适合发呆。', '店员很亲切。'],
  'nature': ['天气好的时候特别漂亮。', '很适合慢慢散步的地方。', '来釜山的话值得去一次。',
             '比想象中大，建议多留点时间。', '一大早去人很少，很舒服。', '傍晚的氛围最好。'],
  'culture': ['可以安静地逛一圈。', '看点比想象中多。', '说明做得很清楚，容易理解。',
              '下雨天来这里刚好。', '一个小时就能看完。', '对釜山的历史有了一点了解。'],
  'shop': ['光是逛逛就很有意思。', '基本上想要的都能找到。', '价格也比较合理。',
           '买伴手礼很合适。', '种类很多，挑得眼花缭乱。', '店员讲解得很耐心。'],
 },
 'fr': {
  'rest': ['Très bon repas, sans prétention.', 'Beaucoup de gens du quartier, bon signe.',
           'Un des meilleurs repas de mon séjour à Busan.', 'Rapport qualité-prix très correct.'],
  'cafe': ['Bon café et ambiance tranquille.', 'On y reste plus longtemps que prévu.',
           'Parfait pour souffler entre deux visites.', 'Personnel accueillant, salle calme.'],
  'nature': ['Magnifique par temps clair.', 'Idéal pour une promenade tranquille.',
             'À voir au moins une fois à Busan.', 'Plus grand que prévu, prévoyez du temps.'],
  'culture': ['Un endroit calme où flâner.', 'Plus de choses à voir que je ne pensais.',
              'Les explications sont claires.', 'Une bonne option un jour de pluie.'],
  'shop': ['Agréable rien que pour se promener.', 'On trouve à peu près de tout.',
           'Des prix raisonnables dans l’ensemble.', 'Bon endroit pour les souvenirs.'],
 },
}

DETAIL = {
 'ko': {
  'rest': ['양도 넉넉했어요.', '반찬도 깔끔하게 나옵니다.', '국물이 진해서 좋았습니다.',
           '자리가 넓어서 편했어요.', '주문하고 금방 나왔습니다.', '사장님이 친절하셨어요.',
           '혼밥 하기에도 눈치 안 보여요.', '가게는 작지만 회전이 빠릅니다.'],
  'cafe': ['원두를 고를 수 있어서 좋았어요.', '디저트 종류가 꽤 많습니다.', '콘센트가 있어서 작업하기도 좋아요.',
           '바깥 풍경이 잘 보입니다.', '음악 소리가 적당했어요.', '테이블 간격이 넉넉합니다.',
           '아이스 음료가 시원하게 잘 나와요.', '포장도 빠르게 해주십니다.'],
  'nature': ['산책로가 잘 정비되어 있어요.', '벤치가 곳곳에 있어서 쉬기 좋습니다.', '바다가 계속 보여서 걷는 맛이 있어요.',
             '해 질 무렵 풍경이 특히 좋습니다.', '사진 찍을 곳이 많아요.', '근처에 카페도 많습니다.',
             '한 바퀴 도는 데 한 시간쯤 걸렸어요.', '길이 평탄해서 편하게 걸었습니다.'],
  'culture': ['전시 구성이 깔끔했습니다.', '안내판이 여러 언어로 되어 있어요.', '내부가 시원해서 쉬어 가기 좋아요.',
              '사진 촬영도 가능했습니다.', '주차장이 넓은 편이에요.', '직원분 설명이 친절했습니다.',
              '규모에 비해 알차게 봤습니다.', '주변이랑 같이 둘러보기 좋아요.'],
  'shop': ['먹거리 골목도 바로 옆에 있어요.', '카드 결제도 잘 됩니다.', '가게마다 값이 조금씩 달라요.',
           '늦게까지 열어서 편했습니다.', '주말엔 사람이 꽤 많습니다.', '지하철역에서 가까워요.',
           '짐 맡길 곳도 있었습니다.', '구경만 해도 시간이 잘 갑니다.'],
 },
 'en': {
  'rest': ['Generous portions too.', 'The side dishes were fresh.', 'Service was quick.',
           'Easy to eat alone here.', 'Small place but it turns over fast.', 'The owner was friendly.'],
  'cafe': ['Good selection of desserts.', 'Plenty of space between tables.', 'Nice view from the window.',
           'Music was at a sensible volume.', 'There are power sockets if you need to work.', 'Takeaway was quick.'],
  'nature': ['The walking paths are well kept.', 'Benches along the way if you need a rest.',
             'The sea stays in view the whole walk.', 'Lots of good photo spots.',
             'Takes about an hour to go round.', 'Plenty of cafes nearby.'],
  'culture': ['The exhibits are well laid out.', 'Signs are in several languages.',
              'Cool inside, a nice break from the heat.', 'Photography is allowed.',
              'Staff were happy to explain things.', 'Easy to combine with nearby sights.'],
  'shop': ['Street food right next door.', 'Cards are accepted almost everywhere.',
           'Prices vary a bit between stalls.', 'Open until late.',
           'Close to the subway station.', 'Busy at weekends.'],
 },
 'ja': {
  'rest': ['量もしっかりありました。', 'おかずも丁寧でした。', '注文してすぐ出てきます。',
           '一人でも入りやすい雰囲気です。', '店は狭いですが回転が早いです。', '店主さんが親切でした。'],
  'cafe': ['デザートの種類も多いです。', '席の間隔がゆったりしています。', '窓からの眺めがいいです。',
           '音楽の音量もちょうどよかったです。', 'コンセントがあるので作業もできます。', 'テイクアウトも早いです。'],
  'nature': ['遊歩道がよく整備されています。', 'ベンチが所々にあって休めます。', '歩いている間ずっと海が見えます。',
             '写真を撮れる場所がたくさんあります。', '一周一時間ほどでした。', '近くにカフェも多いです。'],
  'culture': ['展示の構成がわかりやすいです。', '案内が多言語で書かれています。', '中は涼しくて休憩にもなります。',
              '写真撮影もできました。', '職員の方の説明が丁寧でした。', '周辺と合わせて回りやすいです。'],
  'shop': ['すぐ隣に屋台通りがあります。', 'カードもほぼ使えます。', '店によって値段が少し違います。',
           '遅くまで開いています。', '地下鉄の駅から近いです。', '週末はかなり混みます。'],
 },
 'zh': {
  'rest': ['分量也很足。', '小菜也很新鲜。', '上菜很快。', '一个人来也不会尴尬。',
           '店不大，但翻台很快。', '老板很热情。'],
  'cafe': ['甜点种类也不少。', '桌子之间的间隔很宽敞。', '窗外的景色很好。',
           '音乐音量刚刚好。', '有插座，办公也方便。', '外带也很快。'],
  'nature': ['步道维护得很好。', '沿途有长椅可以休息。', '一路上都能看到海。',
             '可以拍照的地方很多。', '走一圈大概一小时。', '附近咖啡厅也很多。'],
  'culture': ['展览安排得很清楚。', '指示牌有多种语言。', '室内很凉快，可以顺便休息。',
              '可以拍照。', '工作人员讲解得很耐心。', '和周边景点一起逛很方便。'],
  'shop': ['旁边就是小吃街。', '基本都能刷卡。', '每家店价格略有不同。',
           '营业到很晚。', '离地铁站很近。', '周末人比较多。'],
 },
 'fr': {
  'rest': ['Les portions sont généreuses.', 'Service rapide.', 'On peut y manger seul sans gêne.'],
  'cafe': ['Bon choix de pâtisseries.', 'Les tables sont bien espacées.', 'Jolie vue depuis la fenêtre.'],
  'nature': ['Les sentiers sont bien entretenus.', 'On voit la mer tout au long de la promenade.',
             'Beaucoup de jolis points de vue.'],
  'culture': ['Les panneaux sont en plusieurs langues.', 'Il fait frais à l’intérieur.',
              'Facile à combiner avec les sites voisins.'],
  'shop': ['La rue des snacks est juste à côté.', 'La carte passe presque partout.',
           'Tout près de la station de métro.'],
 },
}

CAVEAT = {
 'ko': ['다만 주말엔 사람이 많아요.', '주차가 조금 불편합니다.', '대기 시간이 있을 수 있어요.',
        '계단이 많아서 편한 신발이 좋습니다.', '화장실이 조금 멀어요.', '가격은 살짝 있는 편입니다.',
        '여름엔 그늘이 부족해요.', '찾아가는 길이 조금 헷갈립니다.'],
 'en': ['It does get busy at weekends though.', 'Parking is a bit awkward.',
        'You may have to wait at peak times.', 'Wear comfortable shoes, there are a lot of steps.',
        'A little pricey for what it is.', 'Not much shade in summer.'],
 'ja': ['ただ週末は混みます。', '駐車はやや不便です。', '時間帯によっては待つかもしれません。',
        '階段が多いので歩きやすい靴で。', '値段は少し高めです。', '夏は日陰が少ないです。'],
 'zh': ['不过周末人比较多。', '停车稍微有点麻烦。', '高峰时段可能要排队。',
        '台阶不少，建议穿舒服的鞋。', '价格稍微偏高一点。', '夏天遮阴的地方不多。'],
 'fr': ['En revanche, c’est bondé le week-end.', 'Le stationnement est compliqué.',
        'Il faut parfois patienter.', 'Prévoyez de bonnes chaussures, il y a des marches.'],
}

# ── 쓰는 사람들 ─────────────────────────────────────────────────────────
KO_NAMES = ['바다보러왔어요', '국밥순례자', '골목산책', '노을수집가', '부산세번째', '광안리새벽',
            '여행하는곰', '해운대주민', '밀면덕후', '사진만찍는사람', '짐가벼운여행', '동백섬한바퀴',
            '기차타고부산', '혼자다녀요', '가족여행중', '주말만여행자', '영도고양이', '지하철러버',
            '시장구경꾼', '커피찾아삼만리', '해장이필요해', '느린여행', '계단조심', '부산초보',
            '다대포노을', '감천골목', '청사포바람', '어묵한개더', '출장왔다가', '카메라들고',
            '부산말배우는중', '야경보러', '새벽비행기', '배낭하나', '퇴근하고부산', '산책이취미']
EN_NAMES = [('Emily Carter', 'US'), ('Marcus Bell', 'US'), ('Jordan Reese', 'US'), ('Hannah Diaz', 'US'),
            ('Ryan Mitchell', 'CA'), ('Chloe Tremblay', 'CA'), ("Liam O''Connor", 'GB'),
            ('Emma Whitfield', 'GB'), ('Jack Sullivan', 'AU'), ('Mia Thompson', 'AU'),
            ('Lukas Brandt', 'DE'), ('Anna Vogel', 'DE'), ('Ethan Lim', 'SG'), ('Nurul Aziz', 'MY')]
JA_NAMES = [('Kenji Watanabe', 'JP'), ('Yuki Nakamura', 'JP'), ('Haruka Sato', 'JP'),
            ('Sho Tanaka', 'JP'), ('Aoi Kimura', 'JP'), ('Ren Fujita', 'JP')]
ZH_NAMES = [('Wei Zhang', 'CN'), ('Li Chen', 'CN'), ('Xin Liu', 'CN'), ('Mei Wang', 'CN'),
            ('Hao Yang', 'CN'), ('Ting Hsu', 'TW')]
FR_NAMES = [('Sophie Laurent', 'FR'), ('Julien Moreau', 'FR'), ('Camille Petit', 'FR'),
            ('Lucas Girard', 'FR')]

users = []   # (pid, name, nationality, lang)
for i, nm in enumerate(KO_NAMES, 1):
    users.append((f'rv-ko-{i:03d}', nm, None, 'ko'))
for i, (nm, nat) in enumerate(EN_NAMES, 1):
    users.append((f'rv-en-{i:03d}', nm, nat, 'en'))
for i, (nm, nat) in enumerate(JA_NAMES, 1):
    users.append((f'rv-ja-{i:03d}', nm, nat, 'ja'))
for i, (nm, nat) in enumerate(ZH_NAMES, 1):
    users.append((f'rv-zh-{i:03d}', nm, nat, 'zh'))
for i, (nm, nat) in enumerate(FR_NAMES, 1):
    users.append((f'rv-fr-{i:03d}', nm, nat, 'fr'))

BY_LANG = collections.defaultdict(list)
for u in users:
    BY_LANG[u[3]].append(u)

# 리뷰 하나가 어느 언어로 쓰일지. 한국어가 절반 조금 넘게.
LANG_WEIGHTS = [('ko', 54), ('en', 18), ('ja', 11), ('zh', 11), ('fr', 6)]


def pick_lang():
    n = random.randint(1, sum(w for _, w in LANG_WEIGHTS))
    for lang, w in LANG_WEIGHTS:
        n -= w
        if n <= 0:
            return lang
    return 'ko'


def pick_rating():
    n = random.random()
    if n < 0.45: return 5
    if n < 0.80: return 4
    if n < 0.95: return 3
    return 2


# 같은 뜻을 두 번 말하지 않으려고 붙여 둔 꼬리표. 한 리뷰 안에서 겹치면 안 된다.
TOPICS = (('사진', 'photo', '写真', '拍照', 'vue'),
          ('산책', '걷', 'walk', 'promenade', '歩', '散步', '步道'),
          ('사람이 적', '사람이 많', '혼잡', 'busy', 'crowd', '混み', '人很少', '人比较多'),
          ('카페', 'cafe', 'カフェ', '咖啡'),
          ('양도', '분량', 'portion', '量も', '分量'),
          ('친절', 'friendly', 'helpful', '親切', '亲切', '热情', 'accueillant'),
          ('가격', '값', 'price', 'pricey', '値段', '价格', 'prix'))


def topics_of(sentence):
    return {i for i, words in enumerate(TOPICS) if any(w in sentence for w in words)}


def pick_unused(pool, used, tries=6):
    """그 장소에서 아직 안 쓴 문장을 고른다. 다 썼으면 그냥 아무거나."""
    for _ in range(tries):
        c = random.choice(pool)
        if c not in used:
            return c
    return random.choice(pool)


# 줄 서서 기다린다는 말은 먹고 사는 곳에나 어울린다. 해수욕장·절에 붙으면 어색하다.
WAIT_WORDS = ('대기 시간', '待つ', '排队', 'wait', 'patienter')


def caveat_pool(lang, cat):
    if cat in ('rest', 'cafe', 'shop'):
        return CAVEAT[lang]
    return [c for c in CAVEAT[lang] if not any(w in c for w in WAIT_WORDS)]


def make_text(lang, cat, rating, used):
    opener = pick_unused(OPEN[lang][cat], used)
    used.add(opener)
    parts = [opener]

    if random.random() < 0.85:
        for _ in range(6):
            detail = pick_unused(DETAIL[lang][cat], used)
            # 여는 문장과 같은 얘기를 또 하면 어색하다 — 다른 걸 고른다.
            if not (topics_of(opener) & topics_of(detail)):
                used.add(detail)
                parts.append(detail)
                break

    if rating <= 4 and random.random() < (0.7 if rating <= 3 else 0.35):
        caveat = pick_unused(caveat_pool(lang, cat), used)
        if not any(topics_of(caveat) & topics_of(p) for p in parts):
            used.add(caveat)
            parts.append(caveat)
    return ' '.join(parts)


# ── 장소를 고른다 ───────────────────────────────────────────────────────
# (쓸 비율, 리뷰 수 범위) — 이름난 곳일수록 리뷰가 많은 게 자연스럽다.
PLAN = {'39-rest': (0.85, 2, 6), '39-cafe': (0.85, 2, 6), '12': (0.90, 2, 8),
        '14': (0.85, 2, 6), '28': (0.80, 2, 5), '38': (0.35, 1, 4), '15': (0.60, 2, 4)}

# 사람이 몰리는 곳은 리뷰도 많이 달린다. 이런 데가 텅 비어 있으면 어색하다.
FAMOUS = ('해운대해수욕장', '광안리해수욕장', '감천문화마을', '태종대', '자갈치시장', '국제시장',
          '해동용궁사', '범어사', '흰여울문화마을', '송정해수욕장', '다대포해수욕장', '동백섬',
          '용두산공원', '부산타워', '이기대', '오륙도', '부산시민공원', '전포카페거리',
          '깡통시장', '보수동 책방골목', '청사포', '송도해수욕장', '영도 하늘전망대',
          '부산국제영화제', '광안대교', '삼락생태공원', '을숙도', '아미동')


def is_famous(title):
    t = title.replace(' ', '')
    return any(f.replace(' ', '') in t for f in FAMOUS)


picked = []
for r in rows:
    t = str(r.get('contenttypeid'))
    if t == '25':
        continue
    key = t
    if t == '39':
        key = '39-cafe' if food_cat(r) in CAFE else '39-rest'
        if food_cat(r) == 'bar':
            continue
    if key not in PLAN:
        continue
    share, lo, hi = PLAN[key]
    famous = is_famous(r.get('title', ''))
    if not famous and random.random() > share:
        continue
    b = bucket(r)
    if not b:
        continue
    n = random.randint(7, 12) if famous else random.randint(lo, hi)
    picked.append((r, b, n))

# ── 리뷰를 만든다 ───────────────────────────────────────────────────────
reviews = []
for r, b, n in picked:
    cid = r['contentid']
    ctype = str(r.get('contenttypeid'))
    used_writers = set()
    used_sentences = set()
    for _ in range(n):
        lang = pick_lang()
        pool = [u for u in BY_LANG[lang] if u[0] not in used_writers]
        if not pool:
            continue
        u = random.choice(pool)
        used_writers.add(u[0])
        rating = pick_rating()
        reviews.append((cid, ctype, u[0], rating,
                        make_text(lang, b, rating, used_sentences),
                        random.randint(1, 120)))

random.shuffle(reviews)

# ── SQL 로 쓴다 ─────────────────────────────────────────────────────────
def q(s):
    return "'" + s.replace("'", "''") + "'" if "''" not in s else "'" + s + "'"


def qname(s):
    # 이름 목록에는 이미 SQL 용으로 '' 를 써 둔 것이 있다.
    return "'" + s + "'" if "''" in s else "'" + s.replace("'", "''") + "'"


lines = []
lines.append("""-- ============================================================
--  관광지·맛집 별점 예시 데이터 (배포 서버용)
-- ============================================================
--  이 파일은 scratchpad/gen_reviews.py 가 만들었다. 손으로 고치지 말고 다시 생성할 것.
--
--  왜 필요한가
--    별점 칸이 비어 있으면 상세 화면이 휑하고 '방문자 평점' 정렬도 보여줄 수가 없다.
--    관광·맛집·카페 화면 어디를 눌러도 리뷰가 보이도록 부산 전역에 깔아 둔다.
--
--  어느 DB에서 돌려도 같은 결과가 나오게
--    - 작성자를 id 로 박지 않는다. provider='demo' + provider_id 로 찾고, 없으면 여기서 만든다.
--    - 장소는 관광공사 contentId 로 가리킨다 — 어느 환경에서나 같은 값이다.
--    - 이미 있는 리뷰는 건드리지 않는다. 여러 번 돌려도 결과가 같다.
--
--  리뷰 언어를 섞은 이유
--    리뷰 글은 번역을 타지 않는다(PlaceReviewService 에 번역 호출이 없다). 전부 한국어로 넣으면
--    외국어 화면에서 리뷰만 한국어로 남는다. 그래서 국적이 다른 작성자를 섞어,
--    어느 언어 화면에서도 읽을 수 있는 리뷰가 보이게 했다.
-- ============================================================

SET client_encoding = 'UTF8';
\\set ON_ERROR_STOP on

BEGIN;

-- ── 1) 리뷰를 쓸 사람들. 이미 있으면 그대로 두고, 없는 사람만 만든다 ──
INSERT INTO users (id, provider, provider_id, name, email, role, point,
                   account_status, nationality, nationality_prompted, created_at)
SELECT nextval('user_sequence'), 'demo', v.pid, v.nm,
       v.pid || '@demo.koreapoint.invalid', 'USER', 0, 'ACTIVE', v.nat, true, NOW()
FROM (VALUES""")

vals = []
for pid, nm, nat, lang in users:
    vals.append(f"    ('{pid}', {qname(nm)}, {('NULL' if nat is None else chr(39)+nat+chr(39))})")
lines.append(',\n'.join(vals))
lines.append(""") AS v(pid, nm, nat)
WHERE NOT EXISTS (
    SELECT 1 FROM users u WHERE u.provider = 'demo' AND u.provider_id = v.pid
);

-- ── 2) 리뷰 ──
INSERT INTO place_review (content_id, content_type_id, user_id, rating, text, created_at)
SELECT v.cid, v.ctype, u.id, v.rating, v.text, NOW() - (v.days || ' days')::interval
FROM (VALUES""")

vals = []
for cid, ctype, pid, rating, text, days in reviews:
    vals.append(f"    ('{cid}','{ctype}','{pid}',{rating},{q(text)},{days})")
lines.append(',\n'.join(vals))
lines.append(""") AS v(cid, ctype, pid, rating, text, days)
JOIN users u ON u.provider = 'demo' AND u.provider_id = v.pid
WHERE NOT EXISTS (
    SELECT 1 FROM place_review r WHERE r.user_id = u.id AND r.content_id = v.cid
);

COMMIT;

-- ── 확인 ──
SELECT count(DISTINCT content_id) AS "리뷰 달린 장소", count(*) AS "리뷰 전체",
       round(avg(rating), 2) AS "평균 별점"
  FROM place_review
 WHERE user_id IN (SELECT id FROM users WHERE provider = 'demo' AND provider_id LIKE 'rv-%');""")

open(OUT, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')

# ── 요약 ────────────────────────────────────────────────────────────────
per_type = collections.Counter()
for r, b, n in picked:
    per_type[str(r.get('contenttypeid'))] += 1
NAME = {'12': '관광지', '14': '문화시설', '15': '축제', '28': '레저', '38': '쇼핑', '39': '음식·카페'}
print(f'장소 {len(picked)}곳 / 리뷰 {len(reviews)}개')
for k, v in sorted(per_type.items()):
    print(f'   {NAME.get(k, k):10s} {v:>4}곳')
print()
print('언어 비율:', collections.Counter(
    next(u[3] for u in users if u[0] == p) for _, _, p, _, _, _ in reviews).most_common())
print('별점 분포:', sorted(collections.Counter(r[3] for r in reviews).items()))
print(f'\n파일 크기: {os.path.getsize(OUT) // 1024} KB')
