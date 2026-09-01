#!/usr/bin/env python3
"""TourAPI 언어별 서비스에서 lclsSystm 분류체계 이름을 받아 lcls_systm_<lang>.json 생성."""
import json, sys, time, urllib.parse, urllib.request, pathlib

KEY = None
for line in pathlib.Path("src/main/resources/application-local.properties").read_text().splitlines():
    if line.startswith("TOUR_API_SERVICE_KEY="):
        KEY = line.split("=", 1)[1].strip()
assert KEY, "no key"

SERVICES = {"en": "EngService2", "ja": "JpnService2", "zh": "ChsService2", "fr": "FreService2"}
BASE = "https://apis.data.go.kr/B551011/{svc}/lclsSystmCode2"
OUT = pathlib.Path("src/main/resources/tourdata/tourism")
KO = json.loads((OUT / "lcls_systm.json").read_text())

def call(svc, **params):
    q = {"serviceKey": KEY, "MobileOS": "ETC", "MobileApp": "KoreaPoint",
         "_type": "json", "numOfRows": 100, "pageNo": 1, **params}
    url = BASE.format(svc=svc) + "?" + urllib.parse.urlencode(q)
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=20) as r:
                data = json.loads(r.read().decode())
            item = data["response"]["body"].get("items")
            if not item:
                return []
            item = item["item"]
            return item if isinstance(item, list) else [item]
        except Exception as e:
            if attempt == 3:
                print(f"  ! {svc} {params}: {e}", file=sys.stderr)
                return []
            time.sleep(1.5)

for lang, svc in SERVICES.items():
    names = {}
    l1 = call(svc)
    for a in l1:
        names[a["code"]] = a["name"].strip()
        time.sleep(0.15)
        l2 = call(svc, lclsSystm1=a["code"])
        for b in l2:
            names[b["code"]] = b["name"].strip()
            time.sleep(0.15)
            l3 = call(svc, lclsSystm1=a["code"], lclsSystm2=b["code"])
            for c in l3:
                names[c["code"]] = c["name"].strip()
            time.sleep(0.1)
    missing = sorted(set(KO) - set(names))
    extra = sorted(set(names) - set(KO))
    print(f"{lang}: {len(names)} codes  (KO={len(KO)}, missing={len(missing)}, extra={len(extra)})")
    if missing:
        print("   missing:", ", ".join(missing[:20]), "..." if len(missing) > 20 else "")
    (OUT / f"lcls_systm_{lang}.json").write_text(
        json.dumps(names, ensure_ascii=False, indent=1, sort_keys=True) + "\n")
print("done")
