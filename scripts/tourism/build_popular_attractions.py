#!/usr/bin/env python3
"""
데이터랩 '세대별 인기관광지' CSV 6개(전체+20~60대)를 우리 contentId 기준
popular_attractions_busan.json 으로 변환한다.

CSV의 관광지ID는 데이터랩 해시라 contentId 와 안 맞아서 관광지명으로 매칭한다.
TourAPI(KorService2 areaBasedList2, lDongRegnCd=26) 목록의 title 과 대조.

사용:  TOUR_API_SERVICE_KEY=... python3 scripts/tourism/build_popular_attractions.py
출력:  src/main/resources/tourdata/tourism/popular_attractions_busan.json
"""
import csv
import json
import os
import re
import sys
import urllib.request

CSV_DIR = "src/main/resources/tourdata/tourism/datalab"
OUT = "src/main/resources/tourdata/tourism/popular_attractions_busan.json"
AGES = ["20", "30", "40", "50", "60"]

# CSV 관광지명 -> TourAPI title (자동 매칭 실패분 수동 보정)
# 벡스코 제1·2전시장은 같은 장소(벡스코) 하나로 합친다 -> 더 높은 순위만 남긴다.
ALIAS = {
    "범어사": "범어사(부산)",
    "벡스코제1전시장": "벡스코(BEXCO)",
    "벡스코제2전시장": "벡스코(BEXCO)",
}
# 관광지가 아니라 제외 (부두·골프장 등)
DROP = {"신선대부두", "대변항", "부산신항", "감만부두", "베이사이드GC", "해운대CC"}


def norm(s):
    return re.sub(r"[\s()\[\]·・\-_,'’.]", "", s).lower()


def load_tourapi_titles(key):
    titles = {}
    for pg in range(1, 6):
        url = (
            "https://apis.data.go.kr/B551011/KorService2/areaBasedList2"
            f"?serviceKey={key}&MobileOS=ETC&MobileApp=KoreaPoint&_type=json"
            f"&lDongRegnCd=26&numOfRows=1000&pageNo={pg}"
        )
        with urllib.request.urlopen(url, timeout=30) as r:
            data = json.load(r)
        items = data["response"]["body"]["items"]
        if not items:
            break
        it = items["item"]
        it = it if isinstance(it, list) else [it]
        for x in it:
            if x.get("lDongRegnCd") == "26":
                titles[x["title"]] = x["contentid"]
        if len(it) < 1000:
            break
    return titles


def match(name, by_norm):
    n = norm(ALIAS.get(name, name))
    hit = by_norm.get(n)
    if hit:
        return hit
    cands = [v for kn, v in by_norm.items() if n and (n in kn or kn in n)]
    return cands[0] if len(cands) == 1 else None


def read_csv(path):
    with open(path, encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def main():
    key = os.environ.get("TOUR_API_SERVICE_KEY")
    if not key:
        sys.exit("TOUR_API_SERVICE_KEY 환경변수 필요")

    titles = load_tourapi_titles(key)
    by_norm = {norm(k): v for k, v in titles.items()}
    print(f"TourAPI 부산 목록: {len(titles)}곳")

    result = {}          # contentId -> entry
    unmatched = []

    # 전체 파일: rank / ratio / gubun
    for row in read_csv(f"{CSV_DIR}/popular_all.csv"):
        name = row["관심지점명"].strip()
        if name in DROP:
            continue
        cid = match(name, by_norm)
        if not cid:
            unmatched.append(name)
            continue
        rank, ratio = int(row["순위"]), float(row["비율"])
        prev = result.get(cid)
        if prev and prev["rank"] is not None and prev["rank"] <= rank:
            continue  # 같은 장소 중복(예: 벡스코 제1/2전시장) -> 더 높은 순위 유지
        result[cid] = {
            "name": name,
            "rank": rank,
            "ratio": ratio,
            "gubun": row["구분"].strip(),
            "byAge": (prev or {}).get("byAge", {}),
        }

    # 세대 파일: byAge
    for age in AGES:
        for row in read_csv(f"{CSV_DIR}/popular_{age}.csv"):
            name = row["관심지점명"].strip()
            if name in DROP:
                continue
            cid = match(name, by_norm)
            if not cid:
                continue
            entry = result.get(cid)
            if entry is None:  # 세대 top엔 있는데 전체 top엔 없던 곳
                entry = result[cid] = {
                    "name": name, "rank": None, "ratio": None,
                    "gubun": row["구분"].strip(), "byAge": {},
                }
            v = float(row["비율"])
            entry["byAge"][age] = max(v, entry["byAge"].get(age, 0.0))

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2, sort_keys=True)

    print(f"매칭 {len(result)}곳 -> {OUT}")
    if unmatched:
        print("전체 파일 미매칭:", ", ".join(sorted(set(unmatched))))


if __name__ == "__main__":
    main()
