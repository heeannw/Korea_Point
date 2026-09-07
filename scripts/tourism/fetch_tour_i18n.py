#!/usr/bin/env python3
"""부산 관광 목록의 언어별 이름·주소 번역 오버레이를 생성한다.

  src/main/resources/tourdata/tourism/tour_i18n_{en,ja,zh,fr}.json  ->  { contentId: {title, addr} }

전략:
  - KorService2 를 기준 목록(master)으로 잡는다.
  - 언어별 서비스(Eng/Jpn/Chs/Fre)에 있는 항목은 그 공식 번역을 그대로 쓴다.
  - 없는 항목만 DeepL Free 로 이름·주소를 번역한다(문자열 중복 제거).
  - 결과는 커밋한다. 런타임에는 번역 호출이 없다. 새 관광지가 생기면 이 스크립트를 다시 돌린다.

키:
  TOUR_API_SERVICE_KEY  <- src/main/resources/application-local.properties
  translation.api.key   <- src/main/resources/application-local.properties  (DeepL Free, '...:fx')

사용:  python3 scripts/tourism/fetch_tour_i18n.py
"""
import json, sys, time, pathlib, urllib.parse, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "src/main/resources/tourdata/tourism"
LANGS = {"en": ("EngService2", "EN"), "ja": ("JpnService2", "JA"),
         "zh": ("ChsService2", "ZH"), "fr": ("FreService2", "FR")}
BUSAN = "26"


def prop(path, key):
    for line in (ROOT / path).read_text().splitlines():
        line = line.strip()
        if line.startswith(key + "="):
            return line[len(key) + 1:].strip()
    raise SystemExit(f"{key} not found in {path}")


TOUR_KEY = prop("src/main/resources/application-local.properties", "TOUR_API_SERVICE_KEY")
DEEPL_KEY = prop("src/main/resources/application-local.properties", "translation.api.key")


def get_json(url):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                return json.loads(r.read().decode())
        except Exception as e:  # noqa: BLE001
            if attempt == 3:
                print(f"  ! {e}", file=sys.stderr)
                return None
            time.sleep(2)


def rows(body):
    it = (body or {}).get("items")
    if not it:
        return []
    it = it["item"]
    return it if isinstance(it, list) else [it]


def addr_of(x):
    a = " ".join(p for p in (x.get("addr1", ""), x.get("addr2", "")) if p).strip()
    return a


def fetch_list(svc):
    """부산 areaBasedList2 전체 페이지 + 진행중 축제. contentId -> {title, addr}."""
    out = {}
    base = f"https://apis.data.go.kr/B551011/{svc}/areaBasedList2"
    for page in range(1, 21):
        q = urllib.parse.urlencode({
            "serviceKey": TOUR_KEY, "MobileOS": "ETC", "MobileApp": "KoreaPoint",
            "_type": "json", "numOfRows": 1000, "pageNo": page,
            "lDongRegnCd": BUSAN, "arrange": "Q"})
        body = (get_json(f"{base}?{q}") or {}).get("response", {}).get("body", {})
        rs = rows(body)
        for x in rs:
            cid = str(x.get("contentid") or "").strip()
            if not cid:
                continue
            # 좌표 없는 항목은 앱에서 대부분 버려진다(여행코스 25만 예외).
            if str(x.get("mapx", "0")) in ("0", "", "0.0") and str(x.get("contenttypeid")) != "25":
                continue
            out.setdefault(cid, {"title": (x.get("title") or "").strip(), "addr": addr_of(x)})
        if page * 1000 >= int(body.get("totalCount", 0) or 0):
            break
    fq = urllib.parse.urlencode({
        "serviceKey": TOUR_KEY, "MobileOS": "ETC", "MobileApp": "KoreaPoint",
        "_type": "json", "numOfRows": 1000, "pageNo": 1, "arrange": "A",
        "eventStartDate": time.strftime("%Y%m%d")})
    fbody = (get_json(f"https://apis.data.go.kr/B551011/{svc}/searchFestival2?{fq}") or {}) \
        .get("response", {}).get("body", {})
    for x in rows(fbody):
        if str(x.get("lDongRegnCd") or "") != BUSAN:
            continue
        cid = str(x.get("contentid") or "").strip()
        if cid:
            out.setdefault(cid, {"title": (x.get("title") or "").strip(), "addr": addr_of(x)})
    return out


class Deepl:
    def __init__(self):
        self.cache = {}
        self.chars = 0

    def translate(self, texts, target):
        texts = [t for t in dict.fromkeys(texts) if t and t not in self.cache.get(target, {})]
        self.cache.setdefault(target, {})
        for i in range(0, len(texts), 45):
            chunk = texts[i:i + 45]
            data = [("target_lang", target), ("source_lang", "KO")]
            data += [("text", t) for t in chunk]
            req = urllib.request.Request(
                "https://api-free.deepl.com/v2/translate",
                data=urllib.parse.urlencode(data).encode(),
                headers={"Authorization": f"DeepL-Auth-Key {DEEPL_KEY}"})
            for attempt in range(4):
                try:
                    with urllib.request.urlopen(req, timeout=40) as r:
                        res = json.loads(r.read().decode())["translations"]
                    for src, tr in zip(chunk, res):
                        self.cache[target][src] = tr["text"]
                        self.chars += len(src)
                    break
                except Exception as e:  # noqa: BLE001
                    if attempt == 3:
                        print(f"  ! deepl {target}: {e}", file=sys.stderr)
                        for src in chunk:
                            self.cache[target][src] = src
                    else:
                        time.sleep(3)
            time.sleep(0.4)

    def of(self, text, target):
        return self.cache.get(target, {}).get(text, text)


def main():
    print("KorService2 기준 목록 수집...")
    master = fetch_list("KorService2")
    print(f"  master {len(master)}건")
    deepl = Deepl()

    for lang, (svc, dl_target) in LANGS.items():
        print(f"[{lang}] {svc} 공식 번역 수집...")
        official = fetch_list(svc)
        need_title, need_addr = [], []
        for cid, ko in master.items():
            off = official.get(cid)
            if not (off and off["title"]):
                need_title.append(ko["title"])
            if not (off and off["addr"]):
                need_addr.append(ko["addr"])
        print(f"  공식 {len(set(master) & set(official))}건 / DeepL 대상 제목 {len(set(need_title))} 주소 {len(set(need_addr))}")
        deepl.translate(need_title, dl_target)
        deepl.translate(need_addr, dl_target)

        result = {}
        for cid, ko in master.items():
            off = official.get(cid)
            title = off["title"] if (off and off["title"]) else deepl.of(ko["title"], dl_target)
            addr = off["addr"] if (off and off["addr"]) else deepl.of(ko["addr"], dl_target)
            entry = {}
            if title and title != ko["title"]:
                entry["title"] = title
            if addr and addr != ko["addr"]:
                entry["addr"] = addr
            if entry:
                result[cid] = entry
        path = OUT_DIR / f"tour_i18n_{lang}.json"
        path.write_text(json.dumps(result, ensure_ascii=False, indent=1, sort_keys=True) + "\n")
        print(f"  -> {path.relative_to(ROOT)}  ({len(result)}건)")

    usage = get_json_auth("https://api-free.deepl.com/v2/usage")
    print(f"\nDeepL 이번 실행 {deepl.chars}자 사용. 월 누적 {usage}")


def get_json_auth(url):
    req = urllib.request.Request(url, headers={"Authorization": f"DeepL-Auth-Key {DEEPL_KEY}"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())
    except Exception:  # noqa: BLE001
        return "?"


if __name__ == "__main__":
    main()
