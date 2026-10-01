const { join, sleep, consoleCmd, waitWindow, title } = require('./lib');
const { status, cmd } = require('./wlib');
const Vec3 = require('vec3');
(async () => {
  const A = new Vec3(4, -60, 0);
  const t1 = await join('Tester1');
  consoleCmd('hatcases key give Tester1 basic 2');
  consoleCmd(`tp Tester1 4.5 -60 3.5`); await sleep(1000);
  await t1.lookAt(A.offset(0.5, 0.5, 0.5), true); await t1.activateBlock(t1.blockAt(A));
  await waitWindow(t1, w => /Содержимое/.test(title(w)), 3000);
  const s0 = Date.now(); await t1.clickWindow(53, 0, 0);
  for (let i = 0; i < 30; i++) { const st = await status(); console.log(((Date.now() - s0) / 1000).toFixed(1), JSON.stringify(st)); if (st.animations === 0 && i > 2) break; }
  t1.quit(); await sleep(300); process.exit(0);
})();
