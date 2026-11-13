// Bilder der Entwürfe (1366 × 768 bei 150 %): node bilder.js [name:breite:höhe …] – braucht Playwright mit Chromium.
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const dir = __dirname + '/';
  for (const [f, w, h] of [['chat-start', 911, 512], ['chat-start-dunkel', 911, 512], ['seitenleiste', 911, 512], ['dateien', 911, 512]].concat(process.argv.slice(2).map(x => x.split(':')).map(([f,w,h]) => [f, +w, +h]))) {
    const p = await b.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1.5 });
    await p.goto('file://' + dir + f + '.html');
    await p.evaluate(() => document.fonts.ready);
    await p.waitForTimeout(600);
    await p.screenshot({ path: dir + f + '.png' });
    await p.close();
  }
  await b.close();
})();
