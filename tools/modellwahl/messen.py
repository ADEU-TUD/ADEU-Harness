#!/usr/bin/env python3
"""Modellwahl (AP-106): misst ein lokales Modell auf dem Messkorpus – deutsche Textaufgaben, Code, Postfach.

Läuft auf dem Gerät des Hauptnutzers gegen llama-server (dieselbe OpenAI-Schnittstelle wie in der App):

    # llama-server selbst starten (Programm und Modelle liegen im Datenordner der App unter llm/runtime und llm/models)
    python3 -I tools/modellwahl/messen.py --llama-server <…/llm/runtime/llama-server> --gguf <…/llm/models/Qwen3.5-4B-Q4_K_M.gguf>
    # oder gegen einen laufenden Server
    python3 -I tools/modellwahl/messen.py --server http://127.0.0.1:8080 --modell qwen3.5-4b

Standard ist der Entwicklungsteil. Der Prüfteil (--teil pruefung) wird erst gemessen, wenn die Wahl feststeht, und nie
zur Wahl von Modellen oder Einstellungen benutzt. Code-Aufgaben führen den vom Modell geschriebenen Python-Code mit den
Tests der Aufgabe in einem eigenen Prozess (isoliert, 10 s) in einem leeren Ordner aus.
Ausgabe: je Aufgabe eine Zeile in --ausgabe (JSONL), am Ende die Zusammenfassung je Zweck.
"""
import argparse, json, os, re, subprocess, sys, tempfile, time, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
GERMAN = {"der", "die", "das", "und", "ist", "nicht", "sie", "ich", "wir", "mit", "für", "zu", "den", "ein", "eine", "im", "auf", "bitte", "von", "es"}


def words(t):
    return [w for w in re.split(r"\s+", t) if any(c.isalnum() for c in w)]


def sentences(t):
    return [s for s in re.split(r"(?<=[.!?])\s+", t.strip()) if words(s)]


def bullets(t):
    return [l for l in t.splitlines() if re.match(r"\s*([-*•]|\d+[.)])\s+", l)]


def code_of(t):
    m = re.search(r"```(?:python|py)?\s*\n(.*?)```", t, re.S)
    return m.group(1) if m else t


def json_of(t):
    m = re.search(r"\{.*\}", t, re.S)
    try:
        return json.loads(m.group(0)) if m else None
    except Exception:
        return None


def norm_time(v):
    if v is None:
        return None
    m = re.match(r"^(\d{1,2})(?::(\d{2}))?", str(v).strip())
    return f"{int(m.group(1)):02d}:{m.group(2) or '00'}" if m else str(v)


def check(c, answer):
    a = answer.strip()
    t = c["type"]
    if t == "contains_all":
        return all(v.lower() in a.lower() for v in c["values"])
    if t == "contains_any":
        return any(v.lower() in a.lower() for v in c["values"])
    if t == "not_contains":
        return not any(v.lower() in a.lower() for v in c["values"])
    if t == "max_words":
        return len(words(a)) <= c["value"]
    if t == "german":
        ws = [w.strip(".,;:!?„“\"()").lower() for w in words(a)]
        return len(ws) > 0 and sum(w in GERMAN for w in ws) / len(ws) >= 0.08
    if t == "bullets":
        return len(bullets(a)) == c["value"]
    if t == "bullets_min":
        return len(bullets(a)) >= c["value"]
    if t == "sentences_between":
        n = len(sentences(re.sub(r"^\s*[-*•]\s+", "", a, flags=re.M)))
        return c["min"] <= n <= c["max"]
    if t == "max_avg_sentence_words":
        s = sentences(a)
        return bool(s) and sum(len(words(x)) for x in s) / len(s) <= c["value"]
    if t == "one_of":
        first = re.sub(r"[^\wäöüÄÖÜ]", "", a.split()[0]) if a.split() else ""
        return first in c["values"]
    if t == "json_equals":
        j = json_of(a)
        if not isinstance(j, dict):
            return False
        for k, v in c["values"].items():
            got = j.get(k)
            if k == "uhrzeit":
                got, v = norm_time(got), norm_time(v)
            if isinstance(got, str) and got.strip().lower() in ("", "null", "none"):
                got = None
            if (got or None) != v:
                return False
        return True
    if t == "python_tests":
        src = code_of(a) + "\n\n" + "\n".join(c["tests"]) + "\nprint('OK')\n"
        with tempfile.TemporaryDirectory() as d:
            f = os.path.join(d, "aufgabe.py")
            open(f, "w", encoding="utf-8").write(src)
            try:
                r = subprocess.run([sys.executable, "-I", f], cwd=d, capture_output=True, text=True, timeout=10)
                return r.returncode == 0 and "OK" in r.stdout
            except subprocess.TimeoutExpired:
                return False
    raise ValueError(t)


def ask(server, key, model, prompt):
    body = json.dumps({
        "model": model, "stream": False, "temperature": 0.2, "max_tokens": 700,
        "chat_template_kwargs": {"enable_thinking": False},
        "messages": [{"role": "user", "content": prompt}],
    }).encode()
    req = urllib.request.Request(server.rstrip("/") + "/v1/chat/completions", body, {"Content-Type": "application/json", "Authorization": f"Bearer {key}"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=600) as r:
        o = json.load(r)
    return o["choices"][0]["message"].get("content") or "", time.time() - t0, o.get("timings", {})


def start(binary, gguf, ctx):
    port = 18765
    p = subprocess.Popen([binary, "-m", gguf, "--host", "127.0.0.1", "--port", str(port), "--jinja", "-c", str(ctx), "-np", "1"],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    url = f"http://127.0.0.1:{port}"
    for _ in range(600):
        try:
            if urllib.request.urlopen(url + "/health", timeout=2).status == 200:
                return p, url
        except Exception:
            pass
        if p.poll() is not None:
            sys.exit("llama-server hat sich beendet")
        time.sleep(0.5)
    p.kill()
    sys.exit("llama-server lädt zu lange")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--server")
    ap.add_argument("--key", default="x")
    ap.add_argument("--modell", default="")
    ap.add_argument("--llama-server")
    ap.add_argument("--gguf")
    ap.add_argument("--kontext", type=int, default=8192)
    ap.add_argument("--teil", default="entwicklung", choices=["entwicklung", "pruefung"])
    ap.add_argument("--zweck", default="", help="nur TEXT, CODE oder MAIL")
    ap.add_argument("--ausgabe", default="")
    a = ap.parse_args()
    proc = None
    if a.llama_server:
        proc, server = start(a.llama_server, a.gguf, a.kontext)
        model = a.modell or os.path.basename(a.gguf)
    else:
        server, model = a.server, a.modell
    if not server:
        sys.exit("--server oder --llama-server angeben")
    tasks = [json.loads(l) for l in open(os.path.join(HERE, "messkorpus.jsonl"), encoding="utf-8") if l.strip()]
    tasks = [t for t in tasks if t["teil"] == a.teil and (not a.zweck or t["zweck"] == a.zweck)]
    out = open(a.ausgabe, "w", encoding="utf-8") if a.ausgabe else None
    summary = {}
    try:
        for t in tasks:
            answer, secs, timings = ask(server, a.key, model, t["prompt"])
            ok = all(check(c, answer) for c in t["checks"])
            s = summary.setdefault(t["zweck"], {"n": 0, "ok": 0, "s": 0.0, "tok": 0.0})
            s["n"] += 1; s["ok"] += ok; s["s"] += secs; s["tok"] += timings.get("predicted_per_second", 0.0)
            print(f"{t['id']:8} {'✓' if ok else '✗'} {secs:5.1f} s", flush=True)
            if out:
                out.write(json.dumps({"id": t["id"], "modell": model, "teil": a.teil, "richtig": ok, "sekunden": round(secs, 2),
                                      "timings": timings, "antwort": answer}, ensure_ascii=False) + "\n")
    finally:
        if proc:
            proc.terminate()
    print(f"\nModell: {model} · Teil: {a.teil}")
    for z, s in sorted(summary.items()):
        print(f"  {z:5} {s['ok']}/{s['n']} richtig ({100 * s['ok'] / s['n']:.0f} %) · Ø {s['s'] / s['n']:.1f} s je Aufgabe · Ø {s['tok'] / s['n']:.1f} Token/s")
    n = sum(s["n"] for s in summary.values())
    print(f"  Allrounder (alle Aufgaben): {sum(s['ok'] for s in summary.values())}/{n}")


if __name__ == "__main__":
    main()
