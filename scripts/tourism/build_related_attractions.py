#!/usr/bin/env python3
"""
TarRlteTarService1(관광지 연관성) 부산 16개 구를 받아
related_attractions_busan.json 으로 변환한다.

기준/연관 관광지 모두 데이터랩 해시 ID라 contentId 와 안 맞아서 관광지명으로 매칭한다.
연관은 '관광지' 대분류만 쓴다(음식·숙박 제외 — v1). 기준 하나당 상위 몇 개만 남긴다.

사용:  TOUR_API_SERVICE_KEY=... python3 scripts/tourism/build_related_attractions.py
출력:  src/main/resources/tourdata/tourism/related_attractions_busan.json
"""
import json
import os
import re
import sys
import urllib.request

OUT = "src/main/resources/tourdata/tourism/related_attractions_busan.json"
BASE_YM = "202506"
KEEP_PER_BASE = 8   # 매칭 실패분 고려해 넉넉히 담고, 프론트에서 3개만 노출
SIGNGU = ["26110", "26140", "26170", "26200", "26230", "26260", "26290", "26320",
          "26350", "26380", "26410", "26440", "26470", "26500", "26530", "26710"]


def norm(s):
    return re.sub(r"[\s()\[\]/·・\-_,'’.]", "", s).lower()


# 연관으로 남길 콘텐츠 유형: 관광지·문화시설·축제·레저. 쇼핑(38)·음식(39)·숙박(32)·코스(25) 제외.
KEEP_TYPES = {"12", "14", "15", "28"}


def load_tourapi_titles(key):
    """title -> (contentId, contentTypeId)"""
    titles = {}
    for pg in range(1, 6):
        url = ("https://apis.data.go.kr/B551011/KorService2/areaBasedList2"
               f"?serviceKey={key}&MobileOS=ETC&MobileApp=KoreaPoint&_type=json"
               f"&lDongRegnCd=26&numOfRows=1000&pageNo={pg}")
        with urllib.request.urlopen(url, timeout=30) as r:
            data = json.load(r)
        items = data["response"]["body"]["items"]
        if not items:
            break
        it = items["item"]
        it = it if isinstance(it, list) else [it]
        for x in it:
            if x.get("lDongRegnCd") == "26":
                titles[x["title"]] = (x["contentid"], x.get("contenttypeid"))
        if len(it) < 1000:
            break
    return titles


def pull_related(key):
    rows = []
    for sg in SIGNGU:
        url = ("https://apis.data.go.kr/B551011/TarRlteTarService1/areaBasedList1"
               f"?serviceKey={key}&MobileOS=ETC&MobileApp=KoreaPoint&_type=json"
               f"&numOfRows=1000&pageNo=1&areaCd=26&signguCd={sg}&baseYm={BASE_YM}")
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                data = json.load(r)
            it = data["response"]["body"]["items"]
            if not it:
                continue
            it = it["item"]
            rows += it if isinstance(it, list) else [it]
        except Exception as e:
            print("  ! signgu", sg, e)
    return rows


def main():
    key = os.environ.get("TOUR_API_SERVICE_KEY")
    if not key:
        sys.exit("TOUR_API_SERVICE_KEY 환경변수 필요")

    titles = load_tourapi_titles(key)
    by_norm = {norm(k): v for k, v in titles.items()}   # norm -> (contentId, typeId)
    print(f"TourAPI 부산 목록: {len(titles)}곳")

    def match(name):
        """이름 -> (contentId, typeId). 못 찾으면 None."""
        n = norm(name)
        hit = by_norm.get(n)
        if hit:
            return hit
        cands = [v for kn, v in by_norm.items() if n and (n in kn or kn in n)]
        return cands[0] if len(cands) == 1 else None

    rows = pull_related(key)
    print(f"연관 쌍: {len(rows)}, 기준 관광지: {len(set(r['tAtsNm'] for r in rows))}개")

    result = {}   # baseContentId -> {relContentId: rank}
    for r in rows:
        if r.get("rlteCtgryLclsNm") != "관광지":
            continue
        base = match(r["tAtsNm"])
        rel = match(r["rlteTatsNm"])
        if not base or not rel or base[0] == rel[0]:
            continue
        if rel[1] not in KEEP_TYPES:
            continue   # 쇼핑·음식·숙박 등은 '연관 관광지'에서 제외
        base, rel = base[0], rel[0]
        try:
            rank = int(r.get("rlteRank") or 999)
        except ValueError:
            rank = 999
        lst = result.setdefault(base, {})
        # 같은 연관지가 여러 번 나오면 더 높은(작은) 순위 유지
        if rel not in lst or rank < lst[rel]:
            lst[rel] = rank

    out = {}
    for base, rel_map in result.items():
        ordered = sorted(rel_map.items(), key=lambda kv: kv[1])[:KEEP_PER_BASE]
        out[base] = [{"contentId": cid, "rank": rk} for cid, rk in ordered]

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2, sort_keys=True)

    total_rel = sum(len(v) for v in out.values())
    print(f"기준 {len(out)}곳 · 연관 {total_rel}건 -> {OUT}")


if __name__ == "__main__":
    main()
