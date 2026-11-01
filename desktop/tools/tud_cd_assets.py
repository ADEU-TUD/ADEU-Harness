#!/usr/bin/env python3
"""
Prepares the assets of the TU Dresden corporate design package (CD-Portal, ZIH login) for the app (AP-24):

  python3 desktop/tools/tud_cd_assets.py <unpacked CD folder> <logo folder (TUD-Logo_svg_RGB)>

Writes into desktop/src/main/resources/tud/:
  logos/<variant>.svg   the official logo SVGs (horizontal, vertikal, kurz, Bildmarke; blau, weiss, schwarz; de, en)
  icons.json.gz         the TUD icon sets, cut out of their overview sheets: per set the icons as SVG path data with
                        their bounding box (the sheets have no names, icons are numbered per set)
Only reads the SVG files; nothing of the package is executed.
"""
import gzip, json, math, os, re, sys, shutil

NUM = r'[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?'


def tokens(d):
    return re.findall(r'[MmLlHhVvCcSsQqTtAaZz]|' + NUM, d)


def path_points(d):
    """All end and control points of SVG path data (absolute), for a bounding box."""
    t = tokens(d)
    pts, i, cmd = [], 0, None
    x = y = sx = sy = 0.0
    def num():
        nonlocal i
        v = float(t[i]); i += 1; return v
    while i < len(t):
        if re.match(r'[A-Za-z]', t[i]):
            cmd = t[i]; i += 1
            if cmd in 'Zz':
                x, y = sx, sy; continue
        c = cmd
        rel = c.islower(); C = c.upper()
        if C == 'M':
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if rel else (nx, ny); sx, sy = x, y; pts.append((x, y))
            cmd = 'l' if rel else 'L'
        elif C == 'L' or C == 'T':
            nx, ny = num(), num(); x, y = (x + nx, y + ny) if rel else (nx, ny); pts.append((x, y))
        elif C == 'H':
            nx = num(); x = x + nx if rel else nx; pts.append((x, y))
        elif C == 'V':
            ny = num(); y = y + ny if rel else ny; pts.append((x, y))
        elif C == 'C':
            vals = [num() for _ in range(6)]
            for k in range(0, 6, 2):
                px, py = (x + vals[k], y + vals[k + 1]) if rel else (vals[k], vals[k + 1]); pts.append((px, py))
            x, y = pts[-1]
        elif C in 'SQ':
            vals = [num() for _ in range(4)]
            for k in range(0, 4, 2):
                px, py = (x + vals[k], y + vals[k + 1]) if rel else (vals[k], vals[k + 1]); pts.append((px, py))
            x, y = pts[-1]
        elif C == 'A':
            rx, ry, rot, la, sw, nx, ny = [num() for _ in range(7)]
            x, y = (x + nx, y + ny) if rel else (nx, ny); pts.append((x, y))
        else:
            i += 1
    return pts


def shapes(svg):
    """Filled shapes of an SVG outside <defs>/<clipPath>/<text>, as path data; classes with fill:none are skipped."""
    styles = dict(re.findall(r'\.(cls-\d+|st\d+)\s*\{([^}]*)\}', svg))
    # Grouped selectors like .cls-3,.cls-4{...}
    for sel, body in re.findall(r'((?:\.[\w-]+\s*,\s*)+\.[\w-]+)\s*\{([^}]*)\}', svg):
        for c in re.findall(r'\.([\w-]+)', sel):
            styles[c] = styles.get(c, '') + ';' + body
    body = re.sub(r'<defs>.*?</defs>|<clipPath.*?</clipPath>|<text.*?</text>', '', svg, flags=re.S)
    out = []
    for tag, attrs in re.findall(r'<(path|polygon|polyline|rect|circle)\b([^>]*)>', body):
        cls = re.search(r'class="([^"]+)"', attrs)
        style = ';'.join(styles.get(c, '') for c in (cls.group(1).split() if cls else []))
        style += ';' + (re.search(r'style="([^"]*)"', attrs).group(1) if 'style="' in attrs else '')
        if re.search(r'fill:\s*none', style) or 'transform=' in attrs:
            continue
        if tag == 'path':
            d = re.search(r'\sd="([^"]+)"', attrs).group(1)
        elif tag in ('polygon', 'polyline'):
            p = [float(v) for v in re.findall(NUM, re.search(r'points="([^"]+)"', attrs).group(1))]
            d = 'M' + ' '.join('%g,%g' % (p[k], p[k + 1]) for k in range(0, len(p) - 1, 2)) + 'Z'
        elif tag == 'rect':
            g = lambda n, dv=0.0: float(re.search(n + r'="(' + NUM + ')"', attrs).group(1)) if re.search(n + r'="', attrs) else dv
            x, y, w, h = g('x'), g('y'), g('width'), g('height')
            d = 'M%g,%gh%gv%gh%gZ' % (x, y, w, h, -w)
        else:
            g = lambda n: float(re.search(n + r'="(' + NUM + ')"', attrs).group(1))
            cx, cy, r = g('cx'), g('cy'), g('r')
            d = 'M%g,%ga%g,%g 0 1,0 %g,0a%g,%g 0 1,0 %g,0Z' % (cx - r, cy, r, r, 2 * r, r, r, -2 * r)
        pts = path_points(d)
        if not pts:
            continue
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        out.append({'d': d, 'box': [min(xs), min(ys), max(xs), max(ys)]})
    return out


def icons_of(svg_path):
    svg = open(svg_path, encoding='utf-8').read()
    vb = [float(v) for v in re.search(r'viewBox="([^"]+)"', svg).group(1).split()]
    w, h = vb[2], vb[3]
    items = [s for s in shapes(svg) if s['box'][1] > 90 and s['box'][3] < h - 40]  # without the title bar and the footer
    # Elements belong together when their boxes (grown by 18 units) overlap: union-find.
    parent = list(range(len(items)))
    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]; a = parent[a]
        return a
    g = 18
    for a in range(len(items)):
        A = items[a]['box']
        for b in range(a + 1, len(items)):
            B = items[b]['box']
            if A[0] - g < B[2] and B[0] - g < A[2] and A[1] - g < B[3] and B[1] - g < A[3]:
                parent[find(a)] = find(b)
    groups = {}
    for k, it in enumerate(items):
        groups.setdefault(find(k), []).append(it)
    icons = []
    for els in groups.values():
        x0 = min(e['box'][0] for e in els); y0 = min(e['box'][1] for e in els)
        x1 = max(e['box'][2] for e in els); y1 = max(e['box'][3] for e in els)
        if (x1 - x0) < 25 or (y1 - y0) < 25:
            continue
        icons.append({'d': ' '.join(e['d'] for e in els), 'box': [round(x0, 2), round(y0, 2), round(x1 - x0, 2), round(y1 - y0, 2)]})
    # Reading order: rows (by the middle of the icon), then columns.
    icons.sort(key=lambda i: (round((i['box'][1] + i['box'][3] / 2) / 120), i['box'][0]))
    return icons


SETS = [
    ('Forschung', 'Forschung+Lehre/TUD-Iconset_Forschung.svg'),
    ('Lehre', 'Forschung+Lehre/TUD-Iconset_Lehre.svg'),
    ('Personen', 'Personen/TUD-Iconset_Personen.svg'),
    ('Kommunikation & Organisation', 'Kommunikation/TUD-Iconset_Kommunikation-Organisation.svg'),
    ('Gebäude', 'Gebäude/TUD-Iconset_Gebäude.svg'),
    ('Räume', 'Gebäude/ TUD-Iconset_Räume.svg'),
    ('Symbole', 'Symbole+Zeichen/TUD-Iconset_Symbole.svg'),
    ('Zeichen', 'Symbole+Zeichen/TUD-Iconset_Zeichen.svg'),
    ('Pfeile', 'Pfeile/TUD-Iconset_Pfeile.svg'),
    ('Umwelt', 'Außenraum+Umwelt/TUD-Iconset_Umwelt.svg'),
    ('Energie', 'Energie/TUD-Iconset_Energie.svg'),
    ('Mobilität', 'Transport+Mobilität/TUD-Iconset_Mobilität.svg'),
    ('Essen & Getränke', 'Food/TUD-Iconset_Essen+Getraenke.svg'),
    ('Gesundheit & Sicherheit', 'Gesundheit+Hygiene+Sicherheit/TUD-Iconset_Gesundheit.svg'),
    ('Barrierefreiheit', 'Barrierefrei-Kennung/TUD-Iconset_Barrierefrei.svg'),
    ('Freizeit', 'Freizeit+Feste/TUD-Iconset_Freizeit.svg'),
    ('Weihnachten & Winter', 'Freizeit+Feste/TUD-Iconset_Weihnachten-Winter.svg'),
    ('Benefits', 'Benefits/TUD-Iconset_Arbeitnehmer-Benefits.svg'),
    ('Social Media', 'SocialMedia/TUD-Iconset_Social Media.svg'),
]

LOGOS = {
    'horizontal-blau-de': 'Blau/TUD_Logo_RGB_horizontal_blau_de.svg', 'horizontal-blau-en': 'Blau/TUD_Logo_RGB_horizontal_blau_en.svg',
    'vertikal-blau-de': 'Blau/TUD_Logo_RGB_vertikal_blau_de.svg', 'vertikal-blau-en': 'Blau/TUD_Logo_RGB_vertikal_blau_en.svg',
    'kurz-blau': 'Blau/TUD_Logo_RGB_kurz_blau.svg', 'bildmarke-blau': 'Blau/TUD_Bildmarke_RGB_blau.svg',
    'horizontal-weiss-de': 'Weiss/TUD_Logo_RGB_horizontal_weiß_de.svg', 'horizontal-weiss-en': 'Weiss/TUD_Logo_RGB_horizontal_weiß_en.svg',
    'vertikal-weiss-de': 'Weiss/TUD_Logo_RGB_vertikal_weiß_de.svg', 'vertikal-weiss-en': 'Weiss/TUD_Logo_RGB_vertikal_weiß_en.svg',
    'kurz-weiss': 'Weiss/TUD_Logo_RGB_kurz_weiß.svg', 'bildmarke-weiss': 'Weiss/TUD_Bildmarke_RGB_weiß.svg',
    'horizontal-schwarz-de': 'Schwarz/TUD_Logo__RGBhorizontal_schwarz_de.svg', 'horizontal-schwarz-en': 'Schwarz/TUD_Logo_RGB_horizontal_schwarz_en.svg',
    'vertikal-schwarz-de': 'Schwarz/TUD_Logo_RGB_vertikal_schwarz_de.svg', 'vertikal-schwarz-en': 'Schwarz/TUD_Logo_RGB_vertikal_schwarz_en.svg',
    'kurz-schwarz': 'Schwarz/TUD_Logo_RGB_kurz_schwarz.svg', 'bildmarke-schwarz': 'Schwarz/TUD_Bildmarke_RGB_schwarz.svg',
}


def main():
    cd, logo_dir = sys.argv[1], sys.argv[2]
    out = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'tud')
    os.makedirs(os.path.join(out, 'logos'), exist_ok=True)
    for name, rel in LOGOS.items():
        src = os.path.join(logo_dir, rel)
        if not os.path.isfile(src):  # the zip stores umlauts as #U00df
            src = os.path.join(logo_dir, rel.replace('ß', '#U00df'))
        shutil.copyfile(src, os.path.join(out, 'logos', name + '.svg'))
        pts = [p for s in shapes(open(src, encoding='utf-8').read()) for p in [(s['box'][0], s['box'][1]), (s['box'][2], s['box'][3])]]
        x0, y0 = min(p[0] for p in pts), min(p[1] for p in pts); x1, y1 = max(p[0] for p in pts), max(p[1] for p in pts)
        print('logo %-22s bounds %.2f %.2f %.2f %.2f ratio %.4f' % (name, x0, y0, x1 - x0, y1 - y0, (x1 - x0) / (y1 - y0)))
    sets = []
    for name, rel in SETS:
        icons = icons_of(os.path.join(cd, 'Icon-Themensets', rel))
        print('%-30s %3d icons' % (name, len(icons)))
        sets.append({'name': name, 'icons': icons})
    with gzip.open(os.path.join(out, 'icons.json.gz'), 'wt', encoding='utf-8') as f:
        json.dump({'source': 'TU Dresden, Corporate Design 2025 (Icon-Themensets)', 'sets': sets}, f, separators=(',', ':'))


if __name__ == '__main__':
    main()
