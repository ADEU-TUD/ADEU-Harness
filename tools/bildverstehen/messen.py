#!/usr/bin/env python3
"""Bilder verstehen lokal (AP-110): Messkorpus und Messung eines lokalen Modells mit Bildteil.

Der Korpus wird beim Start aus festen Startwerten erzeugt (nur erfundene Inhalte, keine echten Bilder): Screenshots von
Programmen, Fotos von Tabellen (leicht gedreht, verrauscht), Scans von Briefen, Balkendiagramme. Je Art 3 Bilder im
Entwicklungs- und 3 im Prüfteil. Der Prüfteil wird erst gemessen, wenn die Wahl feststeht, und nie zur Wahl benutzt.

    pip install pillow
    python3 -I tools/bildverstehen/messen.py --llama-server <…/llm/runtime/llama-server> --gguf <…/llm/models/Qwen3.5-4B-Q4_K_M.gguf> \
        --mmproj <…/llm/models/Qwen3.5-4B-mmproj-F16.gguf> [--teil pruefung] [--bilder ordner]

Ausgabe je Bild: richtig/falsch und Sekunden (Ziel AP-110: unter 15 s je Bild auf einem Office-PC).
"""
import argparse, base64, io, json, os, random, subprocess, sys, time, urllib.request

try:
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
except ImportError:
    sys.exit("Bitte zuerst: pip install pillow")


def font(size, bold=False):
    for name in (["DejaVuSans-Bold.ttf", "Arial Bold.ttf", "arialbd.ttf"] if bold else ["DejaVuSans.ttf", "Arial.ttf", "arial.ttf"]):
        for d in ["", "/usr/share/fonts/truetype/dejavu/", "/Library/Fonts/", "C:/Windows/Fonts/", "/System/Library/Fonts/Supplemental/"]:
            try:
                return ImageFont.truetype(d + name, size)
            except OSError:
                pass
    return ImageFont.load_default()


ERRORS = [("Speichern fehlgeschlagen", "Die Datei ist schreibgeschützt.", "schreibgeschützt"),
          ("Verbindung unterbrochen", "Der Server antwortet nicht (Zeitüberschreitung).", "Zeitüberschreitung"),
          ("Druck nicht möglich", "Im Drucker fehlt Papier in Fach 2.", "Papier"),
          ("Anmeldung gescheitert", "Das Passwort ist abgelaufen.", "abgelaufen"),
          ("Upload abgebrochen", "Die Datei ist größer als 50 MB.", "50 MB"),
          ("Export fehlgeschlagen", "Die Schrift „Open Sans“ fehlt.", "Open Sans")]
ROOMS = ["HSZ 204", "BZW A 120", "TOE 211", "POT 81", "WEB 136", "SE2 102", "GER 37", "ZEU 160"]
DAYS = ["Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag"]
DATES = ["15. Mai", "3. Juni", "28. Februar", "12. Oktober", "1. Dezember", "9. Juli"]
CATS = ["Lehre", "Forschung", "Verwaltung", "Transfer", "Weiterbildung"]


def screenshot(r):
    title, text, key = r.choice(ERRORS)
    im = Image.new("RGB", (900, 560), (236, 239, 244))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 900, 40], fill=(40, 60, 110)); d.text((14, 10), "Lora – Dokument", font=font(18), fill="white")
    d.rounded_rectangle([170, 150, 730, 410], 14, fill="white", outline=(180, 180, 190))
    d.text((200, 180), title, font=font(26, True), fill=(160, 30, 30)); d.text((200, 240), text, font=font(20), fill=(30, 30, 30))
    for x, label in [(420, "Abbrechen"), (580, "Erneut versuchen")]:
        d.rounded_rectangle([x, 340, x + 140, 380], 8, outline=(40, 60, 110)); d.text((x + 10, 350), label, font=font(15), fill=(40, 60, 110))
    return im, "Welche Fehlermeldung zeigt der Screenshot? Antworte in einem Satz.", [key]


def table_photo(r):
    rooms = r.sample(ROOMS, 5)
    im = Image.new("RGB", (1000, 640), (250, 250, 246))
    d = ImageDraw.Draw(im)
    d.text((40, 30), "Raumplan Seminar Didaktik", font=font(28, True), fill="black")
    for i, (day, room) in enumerate(zip(DAYS, rooms)):
        y = 110 + i * 90
        d.rectangle([40, y, 960, y + 90], outline="black", width=2)
        d.text((60, y + 28), day, font=font(26), fill="black"); d.text((420, y + 28), room, font=font(26), fill="black")
        d.text((700, y + 28), f"{9 + i}:15 Uhr", font=font(26), fill="black")
    im = im.rotate(r.uniform(-4, 4), expand=True, fillcolor=(120, 110, 100))
    noise = Image.effect_noise(im.size, 18).convert("RGB")
    im = Image.blend(im, noise, 0.12).filter(ImageFilter.GaussianBlur(0.8))
    i = r.randrange(5)
    return im, f"Welcher Raum steht in dieser Tabelle am {DAYS[i]}? Antworte nur mit dem Raum.", [rooms[i]]


def scan(r):
    date = r.choice(DATES)
    name = r.choice(["Dr. Wendt", "Frau Lorenz", "Herrn Kerber", "Prof. Sander"])
    im = Image.new("L", (1240, 1754), 248)
    d = ImageDraw.Draw(im)
    lines = ["Institut für Erziehungswissenschaft", "", f"Sehr geehrte/r {name},", "",
             "wir laden Sie herzlich zur Fachtagung „Lesen lernen“ ein.", "Bitte geben Sie uns bis zum " + date + " Rückmeldung,",
             "ob Sie teilnehmen und einen Beitrag einreichen möchten.", "", "Mit freundlichen Grüßen", "Das Tagungsteam"]
    for k, line in enumerate(lines):
        d.text((140, 200 + k * 58), line, font=font(34), fill=20)
    im = im.rotate(r.uniform(-1.5, 1.5), fillcolor=255).filter(ImageFilter.GaussianBlur(0.6))
    noise = Image.effect_noise(im.size, 30)
    im = Image.blend(im, noise, 0.10).convert("RGB")
    return im, "Bis wann wird um Rückmeldung gebeten? Antworte nur mit dem Datum.", [date]


def chart(r):
    vals = {c: r.randint(10, 90) for c in r.sample(CATS, 4)}
    top = max(vals, key=vals.get)
    if list(vals.values()).count(vals[top]) > 1:
        vals[top] += 7
    im = Image.new("RGB", (900, 600), "white")
    d = ImageDraw.Draw(im)
    d.text((40, 20), "Anträge 2026 nach Bereich", font=font(26, True), fill="black")
    d.line([80, 520, 860, 520], fill="black", width=2); d.line([80, 80, 80, 520], fill="black", width=2)
    for i, (c, v) in enumerate(vals.items()):
        x = 130 + i * 180
        d.rectangle([x, 520 - v * 4.5, x + 100, 520], fill=(40, 90, 160))
        d.text((x, 530), c, font=font(18), fill="black"); d.text((x + 30, 490 - v * 4.5), str(v), font=font(18), fill="black")
    return im, "Welcher Bereich hat den höchsten Balken? Antworte nur mit dem Namen.", [top]


KINDS = [("screenshot", screenshot), ("tabelle", table_photo), ("scan", scan), ("diagramm", chart)]


def corpus(teil):
    seed0 = 1000 if teil == "entwicklung" else 5000
    out = []
    for kind, make in KINDS:
        for k in range(3):
            r = random.Random(seed0 + sum(map(ord, kind)) * 10 + k)
            im, q, expect = make(r)
            out.append((f"{kind}-{k + 1}", im, q, expect))
    return out


def ask(server, key, model, im, q):
    buf = io.BytesIO(); im.save(buf, "PNG")
    url = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
    body = json.dumps({"model": model, "stream": False, "temperature": 0.1, "max_tokens": 120,
                       "chat_template_kwargs": {"enable_thinking": False},
                       "messages": [{"role": "user", "content": [{"type": "image_url", "image_url": {"url": url}}, {"type": "text", "text": q}]}]}).encode()
    req = urllib.request.Request(server.rstrip("/") + "/v1/chat/completions", body, {"Content-Type": "application/json", "Authorization": f"Bearer {key}"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=600) as resp:
        o = json.load(resp)
    return o["choices"][0]["message"].get("content") or "", time.time() - t0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--server"); ap.add_argument("--key", default="x"); ap.add_argument("--modell", default="")
    ap.add_argument("--llama-server"); ap.add_argument("--gguf"); ap.add_argument("--mmproj")
    ap.add_argument("--bildtokens", type=int, default=512)
    ap.add_argument("--teil", default="entwicklung", choices=["entwicklung", "pruefung"])
    ap.add_argument("--bilder", default="", help="Ordner: die erzeugten Bilder dort ablegen (zum Ansehen)")
    a = ap.parse_args()
    items = corpus(a.teil)
    if a.bilder:
        os.makedirs(a.bilder, exist_ok=True)
        for id_, im, q, e in items:
            im.save(os.path.join(a.bilder, id_ + ".png"))
    proc = None
    server, model = a.server, a.modell
    if a.llama_server:
        port = 18766
        proc = subprocess.Popen([a.llama_server, "-m", a.gguf, "--mmproj", a.mmproj, "--host", "127.0.0.1", "--port", str(port), "--jinja",
                                 "-c", "8192", "-np", "1", "--image-max-tokens", str(a.bildtokens)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        server, model = f"http://127.0.0.1:{port}", a.modell or os.path.basename(a.gguf)
        for _ in range(600):
            try:
                if urllib.request.urlopen(server + "/health", timeout=2).status == 200:
                    break
            except Exception:
                time.sleep(0.5)
    if not server:
        sys.exit("--server oder --llama-server angeben")
    ok_n, secs = 0, []
    try:
        for id_, im, q, expect in items:
            answer, s = ask(server, a.key, model, im, q)
            ok = all(e.lower() in answer.lower() for e in expect)
            ok_n += ok; secs.append(s)
            print(f"{id_:12} {'✓' if ok else '✗'} {s:5.1f} s  {answer.strip()[:70]!r}", flush=True)
    finally:
        if proc:
            proc.terminate()
    secs.sort()
    print(f"\n{a.teil}: {ok_n}/{len(items)} richtig · Median {secs[len(secs) // 2]:.1f} s je Bild · langsamstes {secs[-1]:.1f} s (Ziel < 15 s)")


if __name__ == "__main__":
    main()
