# Erzeugt die HTML-Entwürfe der Stilrichtung (APD-01): python3 erzeugen.py, danach node bilder.js baukasten:1120:1050
import pathlib

OUT = pathlib.Path(__file__).resolve().parent  # docs/design/entwuerfe


def sidebar(active_area=None, active_chat=None):
    areas = [
        ("create", "Erstellen", "auto_awesome_mosaic"),
        ("lit", "Literatur", "menu_book"),
        ("trans", "Transkription", "graphic_eq"),
        ("files", "Dateien", "folder"),
    ]
    rows = "\n".join(
        f'<div class="nav{" on" if a == active_area else ""}"><span class="tile t-{a}"><span class="i">{ic}</span></span>{label}</div>'
        for a, label, ic in areas
    )
    chats_today = ["Antrag Lehrinnovationsfonds gliedern", "Mail an Prüfungsamt formulieren"]
    chats_week = ["Folien Ringvorlesung KI", "Excel: Notenliste bereinigen", "Übersetzung Abstract Englisch"]

    def chat(t):
        on = " on" if t == active_chat else ""
        return f'<div class="chat{on}"><span class="name">{t}</span></div>'

    return f"""
  <aside class="side" aria-label="Seitenleiste">
    <div class="brand"><img src="lora-look.png" alt=""><b>Lora</b>
      <span class="iconbtn" title="Chats durchsuchen (Strg+Umschalt+F)"><span class="i">search</span></span>
      <span class="iconbtn" title="Seitenleiste ausblenden (Strg+B)"><span class="i">left_panel_close</span></span></div>
    <div class="newchat"><span class="i">edit_square</span><span class="l">Neuer Chat</span><kbd>Strg N</kbd></div>
    {rows}
    <div class="nav muted"><span class="tile"><span class="i">expand_more</span></span>Mehr</div>
    <div class="scroll">
      <div class="group"><span>Projekte</span><span class="i" style="font-size:16px">add</span></div>
      <div class="chat"><span class="dot" style="background:var(--a-lit)"></span><span class="name">Lehrinnovation 2027</span><span class="count">3</span></div>
      <div class="chat"><span class="dot" style="background:var(--a-create)"></span><span class="name">Promotion Kapitel 3</span><span class="count">2</span></div>
      <div class="group"><span>Heute</span></div>
      {"".join(chat(t) for t in chats_today)}
      <div class="group"><span>Letzte 7 Tage</span></div>
      {"".join(chat(t) for t in chats_week)}
    </div>
    <div class="me"><span class="avatar">A</span><span class="who">Alex<small>Einstellungen</small></span>
      <span class="iconbtn" title="Einstellungen (Strg+,)"><span class="i">settings</span></span></div>
  </aside>"""


def page(title, body, theme="light", extra_css=""):
    return f"""<!DOCTYPE html>
<html lang="de" data-theme="{theme}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=911">
<title>{title}</title>
<link rel="stylesheet" href="stil.css">
<style>{extra_css}</style>
</head>
<body>
{body}
</body>
</html>
"""


START_CSS = """
.start { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 0 32px 40px; gap: 14px; }
.start > * { width: 100%; max-width: 600px; }
.hello { display: flex; align-items: center; gap: 16px; margin-top: 4px; }
.hello img { height: 92px; }
.hello h1 { font-size: 26px; font-weight: 600; margin: 0; letter-spacing: -.01em; }
.hello p { margin: 2px 0 0; color: var(--muted); font-size: 15px; }
.chips { display: flex; gap: 6px; flex-wrap: nowrap; overflow: hidden; }
.chips .chip { padding: 0 10px 0 5px; gap: 5px; }
.chips .tile { width: 22px; height: 22px; border-radius: 6px; }
.resume { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.resume .card { padding: 10px 12px; display: flex; gap: 10px; align-items: flex-start; }
.resume b { font-weight: 600; font-size: 13px; line-height: 1.3; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.resume small { color: var(--muted); font-size: 12px; }
.group.inline { padding: 4px 2px 0; }
"""


def chat_start(theme="light"):
    body = f"""<div class="app">{sidebar()}
  <main class="main">
    <div class="topbar"><span class="title"></span>
      <span class="lorapill" title="Lora fragen (Strg+L)"><img src="lora-look.png" alt="">Frag Lora</span>
      <span class="iconbtn" title="Weitere Aktionen"><span class="i">more_vert</span></span></div>
    <div class="start">
      <div class="hello"><img src="lora-wave.png" alt="Lora winkt">
        <div><h1>Guten Abend, Alex</h1><p>Woran arbeitest du heute?</p></div></div>
      <div class="composer">
        <div class="ph">Frag etwas, häng eine Datei an oder tippe / für Befehle</div>
        <div class="row">
          <span class="iconbtn" title="Anhängen"><span class="i">add</span></span>
          <span class="model"><span class="i">school</span>DeepSeek V4.1 Flash<span class="i">expand_more</span></span>
          <span style="flex:1"></span>
          <span class="state" title="Pseudonymisierung an"><span class="i">shield</span></span>
          <span class="state" title="Gedächtnis: 4 Einträge"><span class="i">neurology</span></span>
          <span class="iconbtn" title="Diktieren"><span class="i">mic</span></span>
          <span class="send" title="Senden (Enter)"><span class="i">arrow_upward</span></span>
        </div>
      </div>
      <div class="chips">
        <span class="chip"><span class="tile t-chat"><span class="i">edit</span></span>Schreiben</span>
        <span class="chip"><span class="tile t-create"><span class="i">description</span></span>Dokument erstellen</span>
        <span class="chip"><span class="tile t-files"><span class="i">attach_file</span></span>Dateien auswerten</span>
        <span class="chip"><span class="tile t-lit"><span class="i">menu_book</span></span>Literatur finden</span>
      </div>
      <div>
        <div class="group inline"><span>Weiter, wo du warst</span></div>
        <div class="resume">
          <div class="card"><span class="tile t-chat"><span class="i">chat_bubble</span></span><div><b>Antrag Lehrinnovations­fonds gliedern</b><small>Chat · heute, 17:40</small></div></div>
          <div class="card"><span class="tile t-create"><span class="i">description</span></span><div><b>Antrag Lehrinnovation</b><small>Dokument · gestern</small></div></div>
          <div class="card"><span class="tile t-trans"><span class="i">graphic_eq</span></span><div><b>Interview 3 – Lehrende</b><small>Transkript · Mo., 10:12</small></div></div>
        </div>
      </div>
    </div>
  </main>
</div>"""
    return page("Entwurf · Chat-Startseite", body, theme, START_CSS)


CHAT_CSS = """
.thread { flex: 1; overflow: hidden; padding: 4px 28px 0; display: flex; flex-direction: column; gap: 12px; align-items: center; }
.thread > * { width: 100%; max-width: 620px; }
.q { align-self: flex-end; max-width: 440px; background: var(--accent-soft); color: var(--text); padding: 8px 14px; border-radius: 16px 16px 4px 16px; width: auto; margin-left: auto; }
.qwrap { display: flex; justify-content: flex-end; }
.a h3 { font-size: 15px; margin: 4px 0 4px; font-weight: 600; }
.a p { margin: 0 0 6px; line-height: 1.5; }
table { border-collapse: collapse; width: 100%; font-size: 13px; background: var(--sheet); border: 1px solid var(--line); border-radius: var(--r-m); overflow: hidden; }
th { text-align: left; font-weight: 600; background: var(--line-soft); }
th, td { padding: 6px 10px; border-bottom: 1px solid var(--line-soft); }
.foot { display: flex; align-items: center; gap: 2px; color: var(--muted); }
.foot .i { font-size: 18px; }
.foot .iconbtn { width: 28px; height: 28px; }
.modeltag { margin-left: auto; display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--muted); }
.mono { width: 18px; height: 18px; border-radius: 5px; background: var(--line-soft); color: var(--muted); font-size: 10px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; }
.suggest { display: flex; align-items: center; gap: 8px; padding: 6px 8px 6px 6px; border: 1px dashed color-mix(in srgb, var(--accent) 40%, var(--line)); border-radius: var(--r-m); background: color-mix(in srgb, var(--accent-soft) 45%, var(--sheet)); font-size: 13px; }
.suggest img { height: 28px; }
.suggest .btn { height: 28px; padding: 0 10px; font-size: 12px; }
.bottom { padding: 8px 28px 6px; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.bottom .composer { width: 100%; max-width: 620px; padding: 6px 8px 6px 14px; border-radius: 24px; }
.bottom .composer .row .ph { flex: 1; height: auto; }
.note { font-size: 11px; color: var(--faint); }
"""


def chat_view():
    body = f"""<div class="app">{sidebar(active_chat="Antrag Lehrinnovationsfonds gliedern")}
  <main class="main">
    <div class="topbar"><span class="title"><span class="crumb">Lehrinnovation 2027 › </span>Antrag Lehrinnovationsfonds gliedern <span class="i" style="font-size:18px;color:var(--muted)">expand_more</span></span>
      <span class="lorapill" title="Lora fragen (Strg+L)"><img src="lora-look.png" alt="">Frag Lora</span>
      <span class="iconbtn" title="Teilen"><span class="i">ios_share</span></span>
      <span class="iconbtn" title="Weitere Aktionen"><span class="i">more_vert</span></span></div>
    <div class="thread">
      <div class="qwrap"><div class="q">Hilf mir, den Antrag für den Lehrinnovationsfonds zu gliedern. Fokus: Statistik-Grundvorlesung, 400 Studierende.</div></div>
      <div class="a">
        <p>Gern – ein Vorschlag für die <b>Gliederung</b>, passend zur Grenze von fünf Seiten:</p>
        <h3>3. Zeitplan</h3>
        <table><tr><th>Phase</th><th>Zeitraum</th><th>Ergebnis</th></tr>
          <tr><td>Konzeption</td><td>01–03/2027</td><td>Lernziele, Storyboard</td></tr>
          <tr><td>Umsetzung</td><td>04–09/2027</td><td>12 Module in OPAL</td></tr>
          <tr><td>Evaluation</td><td>10–12/2027</td><td>Bericht, Kennzahlen</td></tr></table>
      </div>
      <div class="foot">
        <span class="iconbtn" title="Kopieren"><span class="i">content_copy</span></span>
        <span class="iconbtn" title="Teilen"><span class="i">ios_share</span></span>
        <span class="iconbtn" title="Vorlesen"><span class="i">volume_up</span></span>
        <span class="iconbtn" title="Gut"><span class="i">thumb_up</span></span>
        <span class="iconbtn" title="Schlecht"><span class="i">thumb_down</span></span>
        <span class="modeltag"><span class="mono">D</span>DeepSeek V4.1 Flash · TU-Server</span>
      </div>
      <div class="suggest"><img src="lora-read.png" alt="Lora">Lora schlägt vor:
        <span class="btn"><span class="i" style="color:var(--a-sheet)">table_view</span>Tabelle als Excel</span>
        <span class="btn"><span class="i" style="color:var(--a-create)">description</span>Antrag im TUD-Stil</span></div>
    </div>
    <div class="bottom">
      <div class="composer"><div class="row">
        <span class="iconbtn" title="Anhängen"><span class="i">add</span></span>
        <span class="ph">Antworten …</span>
        <span class="model"><span class="i">school</span>DeepSeek V4.1 Flash</span>
        <span class="state" title="Pseudonymisierung an"><span class="i">shield</span></span>
        <span class="iconbtn" title="Diktieren"><span class="i">mic</span></span>
        <span class="send" title="Senden (Enter)"><span class="i">arrow_upward</span></span>
      </div></div>
      <span class="note">KI kann Fehler machen. Wichtiges bitte prüfen.</span>
    </div>
  </main>
</div>"""
    return page("Entwurf · Seitenleiste mit Chat", body, "light", CHAT_CSS)


FILES_CSS = """
.head { display: flex; align-items: center; gap: 8px; padding: 14px 18px 2px 20px; }
.head h1 { font-size: 22px; font-weight: 600; margin: 0; letter-spacing: -.01em; }
.head .sp { flex: 1; }
.search { display: flex; align-items: center; gap: 6px; width: 190px; white-space: nowrap; height: 34px; border: 1px solid var(--line); border-radius: 17px; background: var(--sheet); padding: 0 10px; color: var(--faint); font-size: 13px; }
.search .i { font-size: 18px; }
.search kbd { margin-left: auto; }
.sub { padding: 0 20px; color: var(--muted); font-size: 13px; display: flex; align-items: center; gap: 4px; }
.sub .i { font-size: 16px; }
.tabs { display: flex; gap: 4px; padding: 10px 18px 6px; }
.tab { height: 30px; padding: 0 12px; border-radius: 15px; display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: var(--muted); white-space: nowrap; }
.tab .i { font-size: 17px; }
.tab.on { background: var(--sheet); color: var(--text); font-weight: 600; box-shadow: 0 0 0 1px var(--line); }
.recent { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; padding: 4px 20px 0; }
.fcard { display: flex; gap: 10px; align-items: center; padding: 8px; }
.thumb { width: 44px; height: 54px; border-radius: 5px; border: 1px solid var(--line); background: var(--sheet); flex: none; padding: 6px 5px; display: flex; flex-direction: column; gap: 3px; position: relative; overflow: hidden; }
.thumb i { display: block; height: 3px; border-radius: 2px; background: var(--line); }
.thumb i.h { background: currentColor; width: 70%; height: 4px; }
.thumb .badge { position: absolute; bottom: 3px; right: 3px; font: 700 7px var(--font); color: #fff; background: currentColor; border-radius: 2px; padding: 0 2px; }
.thumb .badge b { color: #fff; }
.fcard b { font-size: 13px; font-weight: 600; display: block; line-height: 1.25; }
.fcard small { color: var(--muted); font-size: 12px; }
.list { margin: 6px 20px 0; }
.lh, .lr { display: grid; grid-template-columns: 1fr 120px 64px 76px; align-items: center; gap: 8px; }
.lh { font-size: 11px; font-weight: 600; letter-spacing: .06em; text-transform: uppercase; color: var(--faint); padding: 6px 8px 4px; }
.lr { height: 40px; padding: 0 8px; border-radius: var(--r-s); font-size: 13px; }
.lr + .lr { border-top: 1px solid var(--line-soft); }
.lr .n { display: flex; align-items: center; gap: 10px; min-width: 0; }
.lr .n span:last-child { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.lr .m { color: var(--muted); }
.lr .acts { display: flex; justify-content: flex-end; color: var(--muted); opacity: 0; }
.lr.hover { background: var(--sheet); box-shadow: 0 0 0 1px var(--line); }
.lr.hover .acts { opacity: 1; }
.lr .acts .iconbtn { width: 26px; height: 26px; }
.lr .acts .i { font-size: 18px; }
"""


def files():
    rows = [
        ("files", "folder", "Lehre", "heute, 18:02", "4 Dateien"),
        ("files", "folder", "Forschung", "heute, 18:02", "2 Dateien"),
        ("pdf", "picture_as_pdf", "Ausschreibung Lehrinnovation.pdf", "heute, 17:58", "48 KB"),
        ("create", "description", "Einladung Kolloquium.md", "gestern, 09:14", "2 KB"),
        ("sheet", "table_view", "Notenliste WS26.csv", "Mo., 11:30", "1 KB"),
        ("tools", "article", "Protokoll Institutsrat.txt", "05.11.2026", "3 KB"),
    ]
    lr = "\n".join(
        f'<div class="lr{" hover" if i == 2 else ""}"><span class="n"><span class="tile t-{a}"><span class="i{" fill" if ic == "folder" else ""}">{ic}</span></span><span>{n}</span></span>'
        f'<span class="m">{d}</span><span class="m">{s}</span><span class="acts"><span class="iconbtn" title="Markieren"><span class="i">star</span></span>'
        f'<span class="iconbtn" title="In Chat senden"><span class="i">chat_add_on</span></span><span class="iconbtn" title="Mehr"><span class="i">more_vert</span></span></span></div>'
        for i, (a, ic, n, d, s) in enumerate(rows)
    )
    body = f"""<div class="app">{sidebar(active_area="files")}
  <main class="main">
    <div class="head"><h1>Dateien</h1><span class="sp"></span>
      <span class="search"><span class="i">search</span>Suchen<kbd>Strg F</kbd></span>
      <span class="iconbtn" title="Ansicht: Liste oder Raster"><span class="i">grid_view</span></span>
      <span class="btn primary"><span class="i">add</span>Neu</span></div>
    <div class="sub"><span class="i">folder_open</span>Dokumente › Lora · alles, was du hochlädst oder mit Lora erstellst</div>
    <div class="tabs">
      <span class="tab on"><span class="i">folder</span>Meine Dateien</span>
      <span class="tab"><span class="i">schedule</span>Zuletzt</span>
      <span class="tab"><span class="i">star</span>Markiert</span>
      <span class="tab"><span class="i">auto_awesome</span>Von Lora</span>
      <span class="tab"><span class="i">cloud</span>Orte</span>
      <span class="tab"><span class="i">delete</span>Papierkorb</span>
    </div>
    <div class="recent">
      <div class="card fcard"><span class="thumb" style="color:var(--a-pdf)"><i class="h"></i><i></i><i></i><i style="width:60%"></i><span class="badge">PDF</span></span><div><b>Ausschreibung Lehrinnovation</b><small>PDF · 3 Seiten · heute</small></div></div>
      <div class="card fcard"><span class="thumb" style="color:var(--a-create)"><i class="h"></i><i></i><i style="width:80%"></i><i></i><span class="badge">MD</span></span><div><b>Einladung Kolloquium</b><small>Dokument · gestern</small></div></div>
      <div class="card fcard"><span class="thumb" style="color:var(--a-sheet)"><i class="h" style="width:100%"></i><i></i><i></i><i></i><span class="badge">CSV</span></span><div><b>Notenliste WS26</b><small>Tabelle · Mo.</small></div></div>
    </div>
    <div class="list">
      <div class="lh"><span>Name</span><span>Geändert</span><span>Größe</span><span></span></div>
      {lr}
    </div>
  </main>
</div>"""
    return page("Entwurf · Dateien", body, "light", FILES_CSS)


BK_CSS = """
body { background: var(--page); }
.board { width: 1120px; padding: 28px 32px 32px; display: grid; grid-template-columns: 1fr 1fr; gap: 22px 28px; }
.board h2 { grid-column: 1 / -1; font-size: 24px; margin: 0; font-weight: 600; letter-spacing: -.01em; }
.board h2 small { display: block; font-size: 14px; color: var(--muted); font-weight: 400; margin-top: 2px; }
.sec { display: flex; flex-direction: column; gap: 10px; }
.sec > .group { padding: 0 2px; }
.row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.sw { display: flex; flex-direction: column; align-items: center; gap: 4px; font-size: 11px; color: var(--muted); width: 62px; text-align: center; }
.type div { display: flex; align-items: baseline; gap: 12px; }
.type small { width: 110px; color: var(--faint); font-size: 11px; }
.radius span { width: 70px; height: 46px; background: var(--sheet); border: 1px solid var(--line); display: inline-flex; align-items: center; justify-content: center; font-size: 12px; color: var(--muted); }
.space i { display: inline-block; height: 12px; background: var(--accent-soft); border: 1px solid color-mix(in srgb, var(--accent) 35%, transparent); border-radius: 3px; }
.space span { font-size: 11px; color: var(--faint); margin-right: 8px; }
.ph { background: var(--sheet); border: 1px solid var(--line); border-radius: var(--r-m); padding: 14px 16px; display: flex; align-items: center; gap: 10px; }
.ph h3 { margin: 0; font-size: 20px; font-weight: 600; }
.ph p { margin: 0; color: var(--muted); font-size: 13px; }
.lrow { display: flex; align-items: center; gap: 10px; height: 52px; padding: 0 12px; background: var(--sheet); border: 1px solid var(--line); border-radius: var(--r-m); }
.lrow b { font-weight: 600; display: block; font-size: 14px; }
.lrow small { color: var(--muted); font-size: 12px; }
.switch { width: 38px; height: 22px; border-radius: 11px; background: var(--accent); position: relative; flex: none; }
.switch::after { content: ""; position: absolute; right: 3px; top: 3px; width: 16px; height: 16px; border-radius: 50%; background: #fff; }
.setting { background: var(--sheet); border: 1px solid var(--line); border-radius: var(--r-m); }
.setting > div { display: flex; align-items: center; gap: 12px; padding: 12px 16px; }
.setting > div + div { border-top: 1px solid var(--line-soft); }
.setting b { display: block; font-weight: 600; }
.setting small { color: var(--muted); font-size: 12px; }
.banner { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-radius: var(--r-m); font-size: 13px; border: 1px solid; }
.banner .i { font-size: 20px; }
.banner .sp { flex: 1; }
.banner .btn { height: 28px; font-size: 12px; padding: 0 10px; }
.b-info { color: var(--text); background: color-mix(in srgb, var(--info) 8%, var(--sheet)); border-color: color-mix(in srgb, var(--info) 30%, var(--line)); }
.b-info .i { color: var(--info); }
.b-warn { color: var(--text); background: color-mix(in srgb, var(--warn) 9%, var(--sheet)); border-color: color-mix(in srgb, var(--warn) 30%, var(--line)); }
.b-warn .i { color: var(--warn); }
.b-err { color: var(--text); background: color-mix(in srgb, var(--error) 8%, var(--sheet)); border-color: color-mix(in srgb, var(--error) 30%, var(--line)); }
.b-err .i { color: var(--error); }
.empty { background: var(--sheet); border: 1px solid var(--line); border-radius: var(--r-m); padding: 20px; display: flex; flex-direction: column; align-items: center; text-align: center; gap: 6px; }
.empty img { height: 96px; margin-bottom: 4px; }
.empty b { font-size: 16px; font-weight: 600; }
.empty p { margin: 0 0 6px; color: var(--muted); font-size: 13px; max-width: 360px; }
"""


def baukasten():
    areas = [("chat", "chat_bubble", "Chat"), ("files", "folder", "Dateien"), ("lit", "menu_book", "Literatur"),
             ("trans", "graphic_eq", "Transkription"), ("create", "auto_awesome_mosaic", "Erstellen"), ("pdf", "picture_as_pdf", "PDF"),
             ("mail", "mail", "Mail"), ("sheet", "table_view", "Tabellen"), ("tools", "build", "Werkzeuge")]
    sw = "".join(f'<span class="sw"><span class="tile l t-{a}"><span class="i">{ic}</span></span>{n}</span>' for a, ic, n in areas)
    body = f"""<div class="board">
  <h2>Baukasten der Stilrichtung<small>Klar, ruhig – mit Lora als Farbe · Entwurf, Werte für APD-03 und APD-04</small></h2>
  <div class="sec"><div class="group"><span>Bereichsfarben · visuelle Anker</span></div><div class="row">{sw}</div>
    <div class="row"><span class="btn primary"><span class="i">add</span>Hauptaktion</span><span class="btn">Nebenaktion</span>
      <span style="color:var(--accent);font-weight:600;font-size:13px">Link im Text</span>
      <span class="hint">Akzent nur für Handlung und Auswahl · ein gefüllter Knopf je Bereich</span></div></div>
  <div class="sec type"><div class="group"><span>Schrift · eine Skala</span></div>
    <div><small>32 · Begrüßung</small><span style="font-size:32px;font-weight:600;letter-spacing:-.01em">Guten Abend</span></div>
    <div><small>24 · Seitentitel</small><span style="font-size:24px;font-weight:600">Geplante Aufgaben</span></div>
    <div><small>16 · Kartentitel</small><span style="font-size:16px;font-weight:600">Wochenbericht Lehrstuhl</span></div>
    <div><small>14 · Text</small><span>Fasst jeden Montag die Mails der Woche zusammen.</span></div>
    <div><small>12 · Hinweis</small><span class="hint">Nächster Lauf: Mo., 08:00</span></div>
    <div><small>11 · Abschnitt</small><span class="group" style="padding:0">Heute</span></div></div>
  <div class="sec"><div class="group"><span>Formen und Abstände</span></div>
    <div class="row radius"><span style="border-radius:var(--r-s)">S · 8</span><span style="border-radius:var(--r-m)">M · 12</span><span style="border-radius:var(--r-l)">L · 20</span>
      <span style="border-radius:17px;width:90px">Pille · Knöpfe</span></div>
    <div class="row space"><i style="width:4px"></i><span>4</span><i style="width:8px"></i><span>8</span><i style="width:12px"></i><span>12</span><i style="width:16px"></i><span>16</span><i style="width:24px"></i><span>24</span><i style="width:32px"></i><span>32</span><i style="width:48px"></i><span>48</span></div>
    <div class="group" style="padding-top:8px"><span>Hinweise · StatusBanner</span></div>
    <div class="banner b-info"><span class="i">info</span><span class="sp">Dieser PC zeigt keine Benachrichtigungen – Ergebnisse stehen hier.</span></div>
    <div class="banner b-warn"><span class="i">warning</span><span class="sp">Noch kein Postfach verbunden.</span><span class="btn">Postfach verbinden</span></div>
    <div class="banner b-err"><span class="i">error</span><span class="sp">Der Schlüssel wurde nicht angenommen.</span><span class="btn">Schlüssel prüfen</span></div></div>
  <div class="sec"><div class="group"><span>Seitenkopf · PageScaffold</span></div>
    <div class="ph"><div style="flex:1"><h3>Geplante Aufgaben</h3><p>Lora erledigt Aufgaben zur geplanten Zeit im Hintergrund.</p></div>
      <span class="iconbtn" title="Hilfe"><span class="i">help</span></span><span class="btn primary"><span class="i">add</span>Neue Aufgabe</span></div>
    <div class="group" style="padding-top:8px"><span>Listenzeile · Einstellungszeile</span></div>
    <div class="lrow"><span class="tile l t-mail"><span class="i">schedule</span></span><div style="flex:1"><b>Wochenbericht Lehrstuhl</b><small>Montags, 08:00 · nächster Lauf in 3 Tagen</small></div><span class="switch"></span></div>
    <div class="setting"><div><div style="flex:1"><b>Mit Windows starten</b><small>Lora wartet im Infobereich.</small></div><span class="switch"></span></div>
      <div><div style="flex:1"><b>Gedächtnis</b><small>Vorher fragen, bevor Lora sich etwas merkt.</small></div><span class="chip">Vorher fragen<span class="i">expand_more</span></span></div></div></div>
  <div class="sec" style="grid-column: 1 / -1"><div class="group"><span>Leerer Zustand · EmptyState</span></div>
    <div class="row" style="gap:16px;align-items:stretch">
      <div class="empty" style="flex:1"><img src="lora-read.png" alt=""><b>Noch keine Transkripte</b><p>Nimm ein Gespräch auf oder öffne eine Audio- oder Videodatei.</p>
        <div class="row" style="justify-content:center"><span class="btn primary"><span class="i">mic</span>Aufnehmen</span><span class="btn"><span class="i">folder_open</span>Datei öffnen</span></div></div>
      <div class="empty" style="flex:1"><img src="lora-look.png" alt=""><b>Hier landen deine Dateien</b><p>Lade etwas hoch, zieh Dateien hierher oder lass Lora ein Dokument schreiben.</p>
        <div class="row" style="justify-content:center"><span class="btn primary"><span class="i">upload</span>Hochladen</span><span class="btn"><span class="i">description</span>Neues Dokument</span></div></div>
      <div class="empty" style="flex:1"><img src="lora-cheer.png" alt="" style="height:96px"><b>Alles erledigt</b><p>Der Stapel ist durch: 42 von 42 Dateien bearbeitet.</p>
        <div class="row" style="justify-content:center"><span class="btn primary">Ergebnisse ansehen</span></div></div>
    </div></div>
</div>"""
    return page("Entwurf · Baukasten", body, "light", BK_CSS).replace('content="width=911"', 'content="width=1120"')


(OUT / "baukasten.html").write_text(baukasten())
(OUT / "chat-start.html").write_text(chat_start())
(OUT / "chat-start-dunkel.html").write_text(chat_start("dark"))
(OUT / "seitenleiste.html").write_text(chat_view())
(OUT / "dateien.html").write_text(files())
print("ok")
