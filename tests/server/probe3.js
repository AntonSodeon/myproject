const { join, sleep, consoleCmd, waitWindow, title, check, results } = require('./lib');
const { cmd } = require('./wlib');
const Vec3 = require('vec3');
async function sample() {
  const out = await cmd('execute as @e[type=item_display,tag=qwhatcase_anim] run data get entity @s', 700);
  const m = {};
  for (const l of out.split('\n').filter(l => l.includes('entity data'))) {
    const u = l.match(/UUID: \[I; ([-\d, ]+)\]/); const y = l.match(/Pos: \[[-\d.]+d, ([-\d.]+)d/); const sc = l.match(/scale: \[([\d.E-]+)f/);
    if (u) m[u[1]] = { y: +y[1], scale: +sc[1] };
  }
  return m;
}
(async () => {
  const A = new Vec3(4, -60, 0);
  const t1 = await join('Tester1');
  consoleCmd('hatcases key give Tester1 basic 1'); consoleCmd('tp Tester1 4.5 -60 3.5'); await sleep(1500);
  await t1.lookAt(A.offset(0.5, 0.5, 0.5), true); await t1.activateBlock(t1.blockAt(A));
  await waitWindow(t1, w => /Содержимое/.test(title(w)), 3000);
  const s0 = Date.now(); await t1.clickWindow(53, 0, 0);
  while (Date.now() - s0 < 4200) await sleep(50);
  const a = await sample(); const b = await sample();
  const pairs = Object.keys(a).filter(k => b[k] && a[k].scale > 0.1 && b[k].scale > 0.1 && Math.abs(a[k].y - b[k].y) < 0.6);
  const moves = pairs.map(k => +(b[k].y - a[k].y).toFixed(3));
  check('models move top → bottom (same entity, y decreases)', moves.length >= 3 && moves.every(d => d < 0), moves);
  t1.quit(); await sleep(6000);
  const f = results.filter(r => !r.ok).length; console.log(`SUMMARY ${results.length - f}/${results.length}`); process.exit(0);
})();
