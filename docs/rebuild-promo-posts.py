# -*- coding: utf-8 -*-
"""홍보 게시글을 한국관광공사 관광정보 한 건에서 통째로 만든다.

왜 다시 만드나
    사진만 TourAPI 에서 가져오고 글은 따로 지어서, 해장국집 글에 떡볶이집 사진이,
    짐 보관 글에 절 사진이 붙어 있었다. 사진과 글이 서로 다른 가게를 가리키면
    관광 데이터를 쓴 게 아니라 그냥 배경 이미지를 깐 것이다.

무엇을 쓰나
    한 가게(contentid)에서 이름 · 주소 · 대표사진 · 소개글을 한꺼번에 가져와
    그것만으로 글을 만든다. 영업시간이나 가격 같은 없는 사실은 쓰지 않는다.
    실제로 있는 가게라서, 확인되지 않은 조건을 붙이면 그 가게에 대한 거짓말이 된다.

실행
    python docs/rebuild-promo-posts.py          # 미리보기만
    python docs/rebuild-promo-posts.py --apply  # docs/rebuild-promo-posts.sql 생성
"""
import io
import json
import os
import re
import sys
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = "https://apis.data.go.kr/B551011/KorService2"
BUSAN = "6"

# 바꿔 넣을 홍보 글. 글쓴이 · 올린 시각 · 노출 기간은 건드리지 않는다.
PROMO_POST_IDS = [15, 16, 17, 18, 105, 106, 107, 108, 109, 110, 111, 112]

CONTENT_TYPES = [("39", "음식점"), ("38", "쇼핑")]


def service_key():
    path = os.path.join(ROOT, "src", "main", "resources", "application-local.properties")
    for line in io.open(path, encoding="utf-8"):
        if line.startswith("TOUR_API_SERVICE_KEY="):
            return line.split("=", 1)[1].strip()
    raise SystemExit("TOUR_API_SERVICE_KEY 를 찾지 못했습니다")


KEY = service_key()


def call(op, **kw):
    params = {"serviceKey": KEY, "MobileOS": "ETC", "MobileApp": "KoreaPoint",
              "_type": "json", "numOfRows": "100", "pageNo": "1"}
    params.update(kw)
    url = BASE + "/" + op + "?" + urllib.parse.urlencode(params, safe="%")
    with urllib.request.urlopen(url, timeout=20) as res:
        return json.loads(res.read().decode("utf-8"))


def rows(payload):
    items = payload["response"]["body"].get("items")
    if not items:
        return []
    item = items["item"]
    return item if isinstance(item, list) else [item]


def plain(html):
    """소개글의 태그와 겹친 공백을 걷어낸다."""
    text = re.sub(r"<br\s*/?>", " ", html or "")
    text = re.sub(r"<[^>]+>", "", text)
    return re.sub(r"\s+", " ", text).strip()


def first_sentences(text, count=2, limit=220):
    """앞 몇 문장만 쓴다. 소개글 전체는 홍보 글로 읽기엔 너무 길다."""
    parts = re.split(r"(?<=다\.)\s+", text)
    picked = " ".join(parts[:count]).strip()
    if len(picked) > limit:
        picked = picked[:limit].rsplit(" ", 1)[0] + "…"
    return picked


def district(addr):
    """부산광역시 해운대구 … -> 해운대구"""
    m = re.search(r"부산(?:광역시)?\s+(\S+?[구군])", addr or "")
    return m.group(1) if m else None


def category_names():
    """cat3 코드를 사람이 읽는 이름으로. 제목에 업종을 붙이는 데 쓴다."""
    names = {}
    for cat2 in ["A0502", "A0401"]:
        try:
            for row in rows(call("categoryCode2", cat1=cat2[:3], cat2=cat2)):
                names[row["code"]] = row["name"]
        except Exception:
            pass
    return names


def collect():
    """사진과 소개글이 둘 다 있는 가게만 모은다."""
    cat3_names = category_names()
    found = []
    seen = set()
    for type_id, label in CONTENT_TYPES:
        for item in rows(call("areaBasedList2", areaCode=BUSAN,
                              contentTypeId=type_id, arrange="Q")):
            image = item.get("firstimage")
            gu = district(item.get("addr1", ""))
            if not image or not gu or item["contentid"] in seen:
                continue
            seen.add(item["contentid"])
            found.append({
                "contentid": item["contentid"], "typeid": type_id, "kind": label,
                "title": item["title"].strip(), "addr": item["addr1"].strip(),
                # 이미지가 http 로 오는 경우가 있는데 우리 페이지는 https 라 섞이면 안 뜬다
                "image": image.replace("http://", "https://"),
                "gu": gu, "cat3": cat3_names.get(item.get("cat3"), ""),
            })
    return found


def with_overview(candidates, need):
    """소개글이 실히 있는 것만 남긴다. 구가 겹치지 않게 고른다."""
    picked, used_gu = [], set()
    for cand in candidates:
        if len(picked) >= need:
            break
        if cand["gu"] in used_gu:
            continue
        try:
            detail = rows(call("detailCommon2", contentId=cand["contentid"]))
        except Exception:
            continue
        if not detail:
            continue
        overview = plain(detail[0].get("overview"))
        if len(overview) < 80:
            continue
        cand["overview"] = overview
        picked.append(cand)
        used_gu.add(cand["gu"])

    # 구가 겹치지 않게 고르다 모자라면, 겹치더라도 채운다
    for cand in candidates:
        if len(picked) >= need:
            break
        if any(p["contentid"] == cand["contentid"] for p in picked):
            continue
        try:
            detail = rows(call("detailCommon2", contentId=cand["contentid"]))
        except Exception:
            continue
        if not detail:
            continue
        overview = plain(detail[0].get("overview"))
        if len(overview) < 80:
            continue
        cand["overview"] = overview
        picked.append(cand)
    return picked


def build(shop):
    # 제목이 가게 이름을 갖고, 위치는 목록의 다른 글들처럼 장소 칩이 맡는다.
    # 둘 다 이름을 쓰면 카드 안에서 같은 말이 두 번 나온다.
    kind = shop["cat3"] or shop["kind"]
    title = "%s · %s" % (shop["title"], kind)
    body = (
        "<p>%s</p>"
        '<img src="%s" alt="%s">'
        "<p>주소: %s</p>"
        "<p>사진과 소개는 한국관광공사 관광정보에서 가져왔습니다.</p>"
    ) % (first_sentences(shop["overview"]), shop["image"], shop["title"], shop["addr"])
    return title, body


def sql_text(value):
    return "'" + value.replace("'", "''") + "'"


def main():
    shops = with_overview(collect(), len(PROMO_POST_IDS))
    if len(shops) < len(PROMO_POST_IDS):
        print("가게를 %d곳만 찾았습니다. 그만큼만 바꿉니다." % len(shops))

    statements = []
    for post_id, shop in zip(PROMO_POST_IDS, shops):
        title, body = build(shop)
        print("─" * 72)
        print("글 %-4d %s" % (post_id, title))
        print("       %s" % shop["image"])
        print("       %s" % first_sentences(shop["overview"])[:90])
        statements.append(
            "UPDATE post SET\n"
            "    title = %s,\n"
            "    content = %s,\n"
            "    place_title = %s,\n"
            "    place_content_id = %s,\n"
            "    place_content_type_id = %s,\n"
            # 예전 제목·본문으로 번역해 둔 것이 남아 있으면 새 글과 어긋난다
            "    title_ko = NULL, title_en = NULL, content_ko = NULL, content_en = NULL\n"
            "  WHERE id = %d;" % (
                sql_text(title), sql_text(body), sql_text(shop["gu"]),
                sql_text(shop["contentid"]), sql_text(shop["typeid"]), post_id))

    if "--apply" in sys.argv:
        out = os.path.join(ROOT, "docs", "rebuild-promo-posts.sql")
        io.open(out, "w", encoding="utf-8", newline="").write(
            "-- docs/rebuild-promo-posts.py 가 만든 파일. 손으로 고치지 말고 다시 생성할 것.\n"
            "-- 홍보 글의 사진·이름·주소·소개를 같은 가게 한 건에서 가져와 맞춘다.\n\n"
            "BEGIN;\n\n" + "\n\n".join(statements) + "\n\nCOMMIT;\n")
        print("\n%s 에 %d건을 썼습니다." % (out, len(statements)))
    else:
        print("\n미리보기입니다. 적용하려면 --apply 를 붙이세요.")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
