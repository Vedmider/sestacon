#!/usr/bin/env python3
"""Live retrieval eval against a running Sestacon, same metric definitions as EvalHarness (py3.6, stdlib only).

  python3 scripts/eval-live.py --base http://localhost:18080 --label baseline --out /tmp/eval-baseline.jsonl
  python3 scripts/eval-live.py --label fix --out /tmp/eval-fix.jsonl --compare /tmp/eval-baseline.jsonl
"""
import argparse
import json
import os
import random
import time
import urllib.error
import urllib.request
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
GOLDEN = os.environ.get("GOLDEN") or os.path.join(HERE, "..", "src", "test", "resources", "eval", "golden.json")


def call(base, path, data, headers):
    req = urllib.request.Request(base + path, data=data, method="POST", headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")


def ingest(base, doc_id, text):
    b = uuid.uuid4().hex
    body = ('--%s\r\nContent-Disposition: form-data; name="files"; filename="%s"\r\nContent-Type: text/markdown\r\n\r\n'
            % (b, doc_id)).encode() + text.encode("utf-8") + ("\r\n--%s--\r\n" % b).encode()
    return call(base, "/api/ingest", body, {"Content-Type": "multipart/form-data; boundary=" + b})[0]


def retrieve(base, query, top_k):
    t = time.time()
    code, body = call(base, "/api/retrieve", json.dumps({"query": query, "topK": top_k}).encode("utf-8"),
                      {"Content-Type": "application/json"})
    if code != 200:
        raise SystemExit("retrieve %s -> %s %s" % (query, code, body[:200]))
    return json.loads(body), (time.time() - t) * 1000.0


def score(case, hits, latency):
    relevant = set(case["relevant"])
    rel, first, found = 0, 0, set()
    for i, h in enumerate(hits):
        if h.get("source") in relevant:
            rel += 1
            found.add(h["source"])
            first = first or i + 1
    neg = not relevant
    return {"query": case["query"], "slice": case["slice"], "negative": neg, "hits": len(hits),
            "precision": rel / len(hits) if hits else 0.0, "recall": 0.0 if neg else len(found) / len(relevant),
            "rr": 1.0 / first if first else 0.0, "hit": 1 if first else 0, "abstained": 1 if not hits else 0,
            "topScore": round(hits[0]["score"], 3) if hits else None,
            "topSource": hits[0].get("source") if hits else None, "latencyMs": round(latency, 1),
            "topVector": hits[0].get("vectorScore") if hits else None,
            "topLexical": hits[0].get("lexicalScore") if hits else None}


def fmt(x):
    return "-" if x is None else "%.2f" % x


def mean(xs):
    return sum(xs) / len(xs) if xs else 0.0


def summary(rows):
    pos = [r for r in rows if not r["negative"]]
    neg = [r for r in rows if r["negative"]]
    return {"pos": len(pos), "neg": len(neg), "P": mean([r["precision"] for r in pos]),
            "R": mean([r["recall"] for r in pos]), "MRR": mean([r["rr"] for r in pos]),
            "hit": mean([r["hit"] for r in pos]), "abst": mean([r["abstained"] for r in neg])}


def ci95(deltas):
    if not deltas:
        return 0.0, 0.0
    rnd = random.Random(42)
    means = sorted(mean([rnd.choice(deltas) for _ in deltas]) for _ in range(2000))
    return means[int(0.025 * len(means))], means[int(0.975 * len(means)) - 1]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:18080")
    ap.add_argument("--label", default="run")
    ap.add_argument("--top-k", type=int, default=5)
    ap.add_argument("--out")
    ap.add_argument("--compare")
    args = ap.parse_args()
    golden = json.load(open(GOLDEN, encoding="utf-8"))
    for d in golden["docs"]:
        code = ingest(args.base, d["id"], d["text"])
        if code != 200:
            raise SystemExit("ingest %s -> %s" % (d["id"], code))
    run_id = "%s-%d" % (args.label, int(time.time()))
    rows = []
    for c in golden["cases"]:
        hits, latency = retrieve(args.base, c["query"], args.top_k)
        r = score(c, hits, latency)
        r.update({"run": run_id, "label": args.label, "topK": args.top_k})
        rows.append(r)

    slices = sorted({r["slice"] for r in rows})
    print("| slice | pos | neg | P | R | MRR | hit | abstention |\n|---|---:|---:|---:|---:|---:|---:|---:|")
    for s in slices + ["ALL"]:
        m = summary([r for r in rows if s == "ALL" or r["slice"] == s])
        print("| %s | %d | %d | %.2f | %.2f | %.2f | %.2f | %.2f |" % (s, m["pos"], m["neg"], m["P"], m["R"],
                                                                   m["MRR"], m["hit"], m["abst"]))
    lat = sorted(r["latencyMs"] for r in rows)
    print("latency p50 %.1f ms p95 %.1f ms" % (lat[len(lat) // 2], lat[min(len(lat) - 1, int(0.95 * len(lat)))]))
    for r in rows:
        if (r["negative"] and not r["abstained"]) or (not r["negative"] and not r["hit"]):
            print("MISS %-26s %-52s top=%s vec=%s lex=%s %s" % (r["slice"], r["query"][:52], r["topScore"],
                                                              fmt(r["topVector"]), fmt(r["topLexical"]), r["topSource"]))
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            for r in rows:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
    if args.compare:
        base_rows = {r["query"]: r for r in (json.loads(l) for l in open(args.compare, encoding="utf-8"))}
        pairs = [(base_rows[r["query"]], r) for r in rows if r["query"] in base_rows]
        rr = [b["rr"] - a["rr"] for a, b in pairs if not a["negative"]]
        ab = [b["abstained"] - a["abstained"] for a, b in pairs if a["negative"]]
        w = sum(1 for d in rr if d > 0)
        l = sum(1 for d in rr if d < 0)
        print("paired vs %s: MRR delta %+.3f CI95 [%+.3f, %+.3f] win/loss/tie %d/%d/%d | abstention delta %+.3f "
              "CI95 [%+.3f, %+.3f]" % (args.compare, mean(rr), ci95(rr)[0], ci95(rr)[1], w, l, len(rr) - w - l,
                                       mean(ab), ci95(ab)[0], ci95(ab)[1]))


if __name__ == "__main__":
    main()
