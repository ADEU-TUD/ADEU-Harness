#!/usr/bin/env python3
"""Eigene Modelle veröffentlichen (AP-117): Lora Namensschutz oder Lora Befehle als neue Fassung auf GitHub.

Nutzbar in jeder Claude-Sitzung in der Cloud und am PC (tools/pii/pc.ps1). Was passiert:

1. Ziele prüfen – aus dem Messbericht des Exports (``messung.json``, int8, Prüfteil). Verfehlt: nichts packen, nichts
   veröffentlichen, nur der Bericht (Rückgabewert 2).
2. Packen (``.onnx.gz``, gleiches Ergebnis bei gleicher Datei); über 95 MB in Teile < 95 MB (GitHub nimmt keine Datei über
   100 MB), SHA-256 je Teil und gesamt.
3. Auf einen eigenen Branch legen (Standard ``pii-modell`` bzw. ``befehle-modell``; nie gemergt): gepackte Datei,
   ``MODELLKARTE.md`` („von uns feinabgestimmt und verändert“), ``License.md``, Lizenz des Basismodells, Messbericht,
   Wortschatz, Etiketten, Schwelle, ``SHA256SUMS``, ``README.md`` – als neuer Commit, alte Fassungen bleiben an ihren
   Commits erreichbar.
4. Nach dem Push prüfen (Größe und SHA-256 der Dateien im gepushten Commit) und ``fassung.json`` schreiben (Commit,
   Größe, SHA-256, Teile mit Adressen, Schwelle, Messwerte, Ziele) – neben dem Messbericht im Repo
   (``tools/<pii|befehle>/modell/fassung.json``). Dazu den Eintrag für die Liste der Fassungen in der App
   (``PiiModelInfo.VERSIONS`` bzw. ``CommandModelInfo.VERSIONS``) ausgeben; den trägt derselbe Chat per PR ein.

Aufruf (im Ordner des Repos):

    python tools/modelle/veroeffentlichen.py befehle tools/befehle/modelle/<name>/onnx --bericht tools/befehle/modelle/<name>/bericht.json
    python tools/modelle/veroeffentlichen.py namensschutz tools/pii/modelle/gbert-base/onnx --bericht tools/pii/modelle/gbert-base/bericht.json

Optionen: ``--branch`` (z. B. ein Test-Branch), ``--fassung`` (Name, Standard „Nachtraining <Datum>“), ``--nur-pruefen``
(Ziele prüfen, nichts schreiben), ``--kein-push`` (Branch nur lokal), ``--teilgroesse`` (Bytes je Teil, zum Testen).
Nur Standardbibliothek; braucht git mit Schreibrecht auf das Repo (in der Cloud vorhanden, am PC: angemeldetes git).
"""
from __future__ import annotations

import argparse
import datetime
import gzip
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PART_LIMIT = 95_000_000  # Bytes je Teil: unter GitHubs 100 MB je Datei, mit Abstand (OwnModels.PART_LIMIT in der App)
RAW = "https://raw.githubusercontent.com/ADEU-TUD/Lora/{commit}/{path}"

MODELLE = {
    "namensschutz": {
        "name": "Lora Namensschutz",
        "datei": "lora-namensschutz-pytales.int8.onnx",
        "branch": "pii-modell",
        "werkzeuge": ROOT / "tools" / "pii",
        "info": "PiiModelInfo",
        "ressourcen": "/pii",
        # Ziel AP-67 (Prüfteil, Regeln + Modell wie geplant): ≥ 98 % gefunden, ≤ 2 % fälschlich ersetzt.
        "ziele": [("gefunden", ">=", 98.0), ("falsch_ersetzt", "<=", 2.0)],
    },
    "befehle": {
        "name": "Lora Befehle",
        "datei": "lora-befehle-pytales.int8.onnx",
        "branch": "befehle-modell",
        "werkzeuge": ROOT / "tools" / "befehle",
        "info": "CommandModelInfo",
        "ressourcen": "/befehle",
        # Ziele AP-71 (Prüfteil): ≥ 90 % getroffen, ≤ 2 % falsch ausgeführt, ≥ 95 % der freien Anfragen weitergegeben, < 30 ms.
        "ziele": [("treffer", ">=", 90.0), ("falsch_ausgefuehrt", "<=", 2.0), ("frei_weitergegeben", ">=", 95.0), ("median_ms", "<", 30.0)],
    },
}

#: Basismodelle: Lizenz (wie auf ihren Modellkarten).
LIZENZEN = {
    "deepset/gbert-base": "MIT",
    "deepset/gelectra-base": "MIT",
    "distilbert/distilbert-base-german-cased": "Apache-2.0",
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def werte(art: str, messung: dict) -> dict:
    """Die Messwerte der int8-Datei im Prüfteil, wie die Ziele sie nennen."""
    m = messung.get("int8", messung)
    if art == "namensschutz":
        gefunden, falsch = m["pruef_geplant"]
        return {"gefunden": gefunden, "falsch_ersetzt": falsch, "median_ms": (m.get("zeit") or {}).get("median_ms")}
    p = m["pruef"]
    return {
        "treffer": p["treffer"], "falsch_ausgefuehrt": p["falsch_ausgefuehrt"], "frei_weitergegeben": p["frei_weitergegeben"],
        "median_ms": (m.get("zeit") or {}).get("median_ms"),
    }


def ziele_pruefen(art: str, w: dict) -> list[tuple[str, str, float, float | None, bool]]:
    out = []
    for key, op, ziel in MODELLE[art]["ziele"]:
        v = w.get(key)
        ok = v is not None and ((op == ">=" and v >= ziel) or (op == "<=" and v <= ziel) or (op == "<" and v < ziel))
        out.append((key, op, ziel, v, ok))
    return out


def packen(src: Path, ziel: Path) -> None:
    """gzip ohne Zeitstempel und Namen: dieselbe Datei gibt dieselben Bytes."""
    with src.open("rb") as i, ziel.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, compresslevel=9, mtime=0) as o:
            shutil.copyfileobj(i, o, 1 << 20)


def teilen(gz: Path, groesse: int) -> list[Path]:
    """Die gepackte Datei in Teile ≤ [groesse] (``….gz.001`` …); eine kleine Datei bleibt eine Datei."""
    if gz.stat().st_size <= groesse:
        return [gz]
    teile = []
    with gz.open("rb") as f:
        n = 1
        while True:
            block = f.read(groesse)
            if not block:
                break
            p = gz.with_name(f"{gz.name}.{n:03d}")
            p.write_bytes(block)
            teile.append(p)
            n += 1
    return teile


def git(*args: str, cwd: Path = ROOT, check: bool = True) -> str:
    r = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True)
    if check and r.returncode != 0:
        sys.exit(f"git {' '.join(args)} fehlgeschlagen:\n{r.stderr.strip()}")
    return r.stdout.strip()


def readme(cfg: dict, fassung: str, gz_name: str, teile: list[Path], basis: str) -> str:
    if len(teile) == 1:
        dateien = f"- `{gz_name}` – {fassung}, ONNX int8; entpacken mit `gunzip`"
    else:
        dateien = (f"- `{gz_name}.001` … `{teile[-1].name}` – {fassung}, ONNX int8, in {len(teile)} Teilen (GitHub nimmt keine "
                   f"Datei über 100 MB); zusammensetzen und entpacken: `cat {gz_name}.0* | gunzip > {cfg['datei']}`")
    return f"""# {cfg['name']} – nur die Modelldateien

Dieser Branch wird **nicht** gemergt. Er hält die trainierte Modelldatei, die für das Repo zu groß ist. Code, Messkorpus
und Skripte liegen auf `main` unter `tools/`. Jede Fassung ist ein eigener Commit; die App lädt sie an genau diesem
Commit und prüft sie mit SHA-256 (AP-117). Ältere Fassungen bleiben an ihren Commits erreichbar.

{dateien}
- Basismodell `{basis}` ({LIZENZEN.get(basis, 'Lizenz siehe Modellkarte')}), von uns feinabgestimmt und verändert. Die Urheber des
  Basismodells haben {cfg['name']} weder geprüft noch gebilligt.
- Lizenz von {cfg['name']}: `License.md` (PyTales)
- `MODELLKARTE.md`, `messbericht.json`, `vocab.txt`, `labels.json`, `schwelle.json` – gehören dazu
- `SHA256SUMS` – Prüfsummen der Teile, der gepackten und der entpackten Datei

Veröffentlicht mit `tools/modelle/veroeffentlichen.py`.
"""


def main() -> int:
    ap = argparse.ArgumentParser(description="Eigene Modelle als neue Fassung auf GitHub veröffentlichen (AP-117)")
    ap.add_argument("art", choices=sorted(MODELLE))
    ap.add_argument("onnx", help="Ordner des Exports (…/onnx) mit der int8-Datei, vocab.txt, labels.json, schwelle.json, MODELLKARTE.md, messung.json")
    ap.add_argument("--bericht", help="bericht.json des Trainings (Basismodell, Epochen) – kommt in den Messbericht")
    ap.add_argument("--branch", help="Ziel-Branch (Standard: pii-modell bzw. befehle-modell)")
    ap.add_argument("--fassung", help="Name der Fassung (Standard: „Nachtraining <heute>“)")
    ap.add_argument("--nur-pruefen", action="store_true", help="nur die Ziele prüfen, nichts schreiben")
    ap.add_argument("--kein-push", action="store_true", help="Commit nur lokal (zum Ausprobieren)")
    ap.add_argument("--teilgroesse", type=int, default=PART_LIMIT, help=argparse.SUPPRESS)
    ap.add_argument("--fassung-json", help=argparse.SUPPRESS)  # Ziel von fassung.json (Tests)
    a = ap.parse_args()

    cfg = MODELLE[a.art]
    src = Path(a.onnx)
    modell = src / cfg["datei"]
    pflicht = [modell, src / "vocab.txt", src / "labels.json", src / "schwelle.json", src / "MODELLKARTE.md", src / "messung.json"]
    fehlt = [p.name for p in pflicht if not p.is_file()]
    if fehlt:
        sys.exit(f"Im Ordner {src} fehlt: {', '.join(fehlt)} (erst export.py laufen lassen)")
    messung = json.loads((src / "messung.json").read_text(encoding="utf-8"))
    bericht = json.loads(Path(a.bericht).read_text(encoding="utf-8")) if a.bericht else {}
    schwelle = json.loads((src / "schwelle.json").read_text(encoding="utf-8"))
    karte = (src / "MODELLKARTE.md").read_text(encoding="utf-8")
    basis = bericht.get("basis") or next((b for b in LIZENZEN if b in karte), "")

    # 1. Ziele
    w = werte(a.art, messung)
    pruefung = ziele_pruefen(a.art, w)
    print(f"{cfg['name']} – Prüfteil (int8):")
    for key, op, ziel, v, ok in pruefung:
        print(f"  {'✓' if ok else '✗'} {key}: {v} (Ziel {op} {ziel})")
    if not all(ok for *_, ok in pruefung):
        print("Ziele verfehlt – nichts veröffentlicht. Der Messbericht bleibt im Export-Ordner.")
        return 2
    if a.nur_pruefen:
        print("Ziele erreicht (nur geprüft, nichts geschrieben).")
        return 0
    if "feinabgestimmt und verändert" not in karte:
        sys.exit("MODELLKARTE.md sagt nicht, dass das Basismodell „von uns feinabgestimmt und verändert“ wurde – bitte ergänzen.")
    lizenz = next(iter(sorted(src.glob("LIZENZ-*.txt"))), None) or next(iter(sorted((cfg["werkzeuge"] / "modell").glob("LIZENZ-*.txt"))), None)
    if lizenz is None:
        sys.exit("Lizenz des Basismodells (LIZENZ-<basis>.txt) nicht gefunden.")

    branch = a.branch or cfg["branch"]
    heute = datetime.date.today()
    fassung = a.fassung or f"Nachtraining {heute:%d.%m.%Y}"

    with tempfile.TemporaryDirectory(prefix="fassung-") as tmp:
        tmp = Path(tmp)
        # 2. Packen und teilen
        gz = tmp / (cfg["datei"] + ".gz")
        print("Packen …")
        packen(modell, gz)
        teile = teilen(gz, a.teilgroesse)
        groesse, sha = modell.stat().st_size, sha256(modell)
        gz_groesse, gz_sha = gz.stat().st_size, sha256(gz)
        teil_info = [{"datei": p.name, "groesse": p.stat().st_size, "sha256": sha256(p)} for p in teile]
        print(f"  {cfg['datei']}: {groesse} Bytes · gepackt {gz_groesse} Bytes in {len(teile)} Teil(en)")

        # 3. Branch füllen
        wt = tmp / "branch"
        git("fetch", "-q", "origin", branch, check=False)
        exists = git("rev-parse", "--verify", "-q", f"origin/{branch}", check=False) != ""
        if exists:
            git("worktree", "add", "-q", "--detach", str(wt), f"origin/{branch}")
        else:
            git("worktree", "add", "-q", "--detach", str(wt))
            git("checkout", "-q", "--orphan", f"fassung-{heute:%Y%m%d}", cwd=wt)
            git("rm", "-rq", "--cached", ".", cwd=wt, check=False)
            for p in wt.iterdir():
                if p.name != ".git":
                    shutil.rmtree(p) if p.is_dir() else p.unlink()
        try:
            # The previous version's files go (they stay at their commit); everything of this version comes in.
            for p in wt.glob("*.onnx.gz*"):
                p.unlink()
            for p in teile:
                shutil.copy(p, wt / p.name)
            for name in ("vocab.txt", "labels.json", "schwelle.json", "MODELLKARTE.md"):
                shutil.copy(src / name, wt / name)
            shutil.copy(lizenz, wt / lizenz.name)
            shutil.copy(ROOT / "License.md", wt / "License.md")
            for extra in ("rueckfrage.json",):
                p = src / extra if (src / extra).is_file() else cfg["werkzeuge"] / "modell" / extra
                if p.is_file():
                    shutil.copy(p, wt / extra)
            (wt / "messbericht.json").write_text(json.dumps({"messung": messung, "training": bericht}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            sums = [f"{t['sha256']}  {t['datei']}" for t in teil_info if len(teil_info) > 1] + [f"{gz_sha}  {gz.name}", f"{sha}  {cfg['datei']}"]
            (wt / "SHA256SUMS").write_text("\n".join(sums) + "\n", encoding="utf-8")
            (wt / "README.md").write_text(readme(cfg, fassung, gz.name, teile, basis), encoding="utf-8")
            git("add", "-A", ".", cwd=wt)
            ident = [] if git("config", "user.email", check=False) else ["-c", "user.name=Lora Training", "-c", "user.email=lora-training@users.noreply.github.com"]
            git(*ident, "commit", "-q", "-m",
                f"{cfg['name']} ({fassung}): {gz.name}{' in ' + str(len(teile)) + ' Teilen' if len(teile) > 1 else ''}, Modellkarte, Lizenzen, Messbericht", cwd=wt)
            commit = git("rev-parse", "HEAD", cwd=wt)
            if not a.kein_push:
                print(f"Push nach {branch} …")
                for warte in (0, 2, 4, 8, 16):
                    if warte:
                        time.sleep(warte)
                    r = subprocess.run(["git", "push", "origin", f"HEAD:refs/heads/{branch}"], cwd=wt, capture_output=True, text=True)
                    if r.returncode == 0:
                        break
                else:
                    sys.exit(f"Push fehlgeschlagen:\n{r.stderr.strip()}")
                # 4. Prüfen: was im gepushten Commit liegt, ist byte-gleich.
                git("fetch", "-q", "origin", branch)
                if git("rev-parse", f"origin/{branch}") != commit:
                    sys.exit("Der Branch zeigt nach dem Push auf einen anderen Commit – bitte prüfen.")
            for t in teil_info:
                blob = subprocess.run(["git", "cat-file", "blob", f"{commit}:{t['datei']}"], cwd=ROOT, capture_output=True).stdout
                if len(blob) != t["groesse"] or hashlib.sha256(blob).hexdigest() != t["sha256"]:
                    sys.exit(f"{t['datei']} im Commit {commit} stimmt nicht – nichts eintragen.")
        finally:
            git("worktree", "remove", "--force", str(wt), check=False)
            if not exists:
                git("branch", "-D", f"fassung-{heute:%Y%m%d}", check=False)  # the orphan's local name; the commit is pushed

    for t in teil_info:
        t["url"] = RAW.format(commit=commit, path=t["datei"])
    online = None
    if not a.kein_push:
        try:  # raw.githubusercontent.com braucht nach einem Push manchmal ein paar Sekunden
            req = urllib.request.Request(teil_info[0]["url"], method="HEAD")
            with urllib.request.urlopen(req, timeout=30) as r:
                online = int(r.headers.get("Content-Length") or -1) == teil_info[0]["groesse"]
        except Exception as e:  # noqa: BLE001 – nur ein Hinweis
            print(f"Hinweis: {teil_info[0]['url']} noch nicht abrufbar ({e}); die App lädt erst mit dem nächsten Release.")

    info = {
        "modell": cfg["name"],
        "fassung": fassung,
        "datum": heute.isoformat(),
        "branch": branch,
        "commit": commit,
        "datei": cfg["datei"],
        "groesse": groesse,
        "sha256": sha,
        "gepackt": {"datei": gz.name, "groesse": gz_groesse, "sha256": gz_sha},
        "teile": teil_info,
        "schwelle": schwelle.get("schwelle"),
        "messwerte": w,
        "ziele": [{"wert": k, "ziel": f"{op} {z}", "gemessen": v, "erreicht": ok} for k, op, z, v, ok in pruefung],
        "basismodell": basis,
        "online_geprueft": online,
    }
    if a.art == "befehle":
        rf = cfg["werkzeuge"] / "modell" / "rueckfrage.json"
        if (src / "rueckfrage.json").is_file():
            rf = src / "rueckfrage.json"
        if rf.is_file():
            info["rueckfrage"] = json.loads(rf.read_text(encoding="utf-8")).get("untergrenze")
    ziel = Path(a.fassung_json) if a.fassung_json else cfg["werkzeuge"] / "modell" / "fassung.json"
    ziel.write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"fassung.json: {ziel}")
    print()
    print(f"Eintrag für {cfg['info']}.VERSIONS (oben einfügen, die bisherigen bleiben; COMMIT, URL, GZ_*, SIZE, SHA256 und")
    print("THRESHOLD auf die neue Fassung setzen; neue Etiketten bringen eine eigene labels-Datei in app/src/main/resources):")
    parts = ", ".join(f'ModelPart(OwnModels.rawUrl("{commit}", "{t["datei"]}"), {t["groesse"]}L, "{t["sha256"]}")' for t in teil_info)
    bound = f", {info['rueckfrage']}f" if info.get("rueckfrage") is not None else ""
    print(f'''    ModelVersion(
        "{fassung}", "{cfg['datei']}", {groesse}L, "{sha}", {info['schwelle']}f{bound},
        labels = "{cfg['ressourcen']}/labels.json", vocab = "{cfg['ressourcen']}/vocab.txt",
        download = ModelDownload("{commit}", listOf({parts}), {gz_groesse}L, "{gz_sha}"),
    ),''')
    return 0


if __name__ == "__main__":
    sys.exit(main())
