#!/usr/bin/env python3
"""
데모용 user_activity 시드. 빈 계정 대신 '취향이 뚜렷한' 유저로 시연하기 위한 것.

  TOUR_API_SERVICE_KEY=... python3 scripts/tourism/seed_demo_activity.py --user 1 --flavor culture

flavor: culture(문화·미술관) | nature(자연·해변) | activity(레저·축제)
DB는 로컬 Postgres(docker korea_point-postgres-1) 기준. 기존 user_activity 는 지우고 새로 넣는다.
"""
import argparse
import json
import os
import subprocess
import sys
import urllib.request

FLAVOR = {
    "culture":  {"types": ["14", "12"], "kw": "미술관",   "cat": "culture",
                 "detail_hint": ["미술관", "박물관", "전시", "유적", "고택", "사찰"]},
    "nature":   {"types": ["12"],       "kw": "해수욕장", "cat": "attraction",
                 "detail_hint": ["해수욕장", "공원", "산", "숲", "해변", "섬", "계곡"]},
    "activity": {"types": ["28", "15"], "kw": "체험",     "cat": "leisure",
                 "detail_hint": ["체험", "레저", "테마파크", "스포츠", "축제"]},
}


def tourapi(key, content_type, rows=100):
    url = ("https://apis.data.go.kr/B551011/KorService2/areaBasedList2"
           f"?serviceKey={key}&MobileOS=ETC&MobileApp=KoreaPoint&_type=json"
           f"&lDongRegnCd=26&numOfRows={rows}&pageNo=1&contentTypeId={content_type}&arrange=P")
    d = json.load(urllib.request.urlopen(url, timeout=30))
    items = d["response"]["body"]["items"]
    if not items:
        return []
    it = items["item"]
    return it if isinstance(it, list) else [it]


def sql_str(v):
    return "NULL" if v is None else "'" + str(v).replace("'", "''") + "'"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--user", type=int, required=True)
    ap.add_argument("--flavor", choices=list(FLAVOR), required=True)
    args = ap.parse_args()
    key = os.environ.get("TOUR_API_SERVICE_KEY")
    if not key:
        sys.exit("TOUR_API_SERVICE_KEY 환경변수 필요")

    fl = FLAVOR[args.flavor]
    pool = []
    for ct in fl["types"]:
        for p in tourapi(key, ct):
            detail = ""  # detailName 은 목록엔 안 오지만, addr 로 area 는 뽑을 수 있음
            pool.append((p["contentid"], p["contenttypeid"], p.get("addr1", ""), p.get("title", "")))
    # 부산진구·해운대구 같은 특정 구로 쏠리게: addr 에 해운대/수영/부산진 있는 것 우선
    pick_gu = {"culture": "부산진구", "nature": "해운대구", "activity": "기장군"}[args.flavor]
    pool.sort(key=lambda x: (pick_gu not in x[2],))
    pool = pool[:8]
    if len(pool) < 4:
        sys.exit("후보가 부족하다 — flavor/키 확인")

    rows = []
    # DETAIL_VIEW 6건 (최근 6일에 하나씩), 그중 2건은 SAVE 도
    for i, (cid, ctype, addr, title) in enumerate(pool[:6]):
        gu = next((t for t in addr.split() if t.endswith("구") or t.endswith("군")), None)
        offset = f"NOW() - INTERVAL '{i} days'"
        rows.append(("DETAIL_VIEW", cid, ctype, None, None, gu, offset))
        if i < 2:
            rows.append(("SAVE", cid, ctype, None, None, gu, f"NOW() - INTERVAL '{i} days 1 hours'"))
    rows.append(("SEARCH", None, None, None, fl["kw"], None, "NOW() - INTERVAL '2 days'"))
    rows.append(("CATEGORY", None, None, fl["cat"], None, None, "NOW() - INTERVAL '1 days'"))

    values = ",\n".join(
        f"({args.user}, '{a}', {sql_str(cid)}, {sql_str(ct)}, {sql_str(cs)}, {sql_str(kw)}, {sql_str(gu)}, {ts})"
        for (a, cid, ct, cs, kw, gu, ts) in rows
    )
    stmt = (f"DELETE FROM user_activity WHERE user_id = {args.user};\n"
            "INSERT INTO user_activity "
            "(user_id, action, content_id, content_type_id, category_slug, keyword, area, created_at) VALUES\n"
            + values + ";\n"
            f"DELETE FROM user_tour_pref WHERE user_id = {args.user};")

    print(stmt)
    subprocess.run(
        ["docker", "exec", "-i", "korea_point-postgres-1",
         "psql", "-U", "postgres", "-d", "korea_point"],
        input=stmt, text=True, check=True,
    )
    print(f"\n시드 완료: user={args.user} flavor={args.flavor} ({len(rows)}건)")


if __name__ == "__main__":
    main()
