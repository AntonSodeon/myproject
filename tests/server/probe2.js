const { join, sleep, consoleCmd, waitWindow, title } = require('./lib');
const { cmd, count } = require('./wlib');
const Vec3 = require('vec3');
(async () => {
  const A = new Vec3(4, -60, 0);
  const t1 = await join('Tester1');
  consoleCmd('hatcases key give Tester1 basic 2'); consoleCmd('tp Tester1 4.5 -60 3.5'); await sleep(1500);
  await t1.lookAt(A.offset(0.5, 0.5, 0.5), true); await t1.activateBlock(t1.blockAt(A));
  await waitWindow(t1, w => /Содержимое/.test(title(w)), 3000);
  const s0 = Date.now(); await t1.clickWindow(53, 0, 0);
  for (let i = 0; i < 14; i++) {
    const pos = (await cmd(`data get entity @e[tag=qwhatcase_label,limit=1,sort=nearest,x=4,y=-60,z=0] Pos`, 400)).trim().split('\n').pop().replace(/.*data: /, '');
    const n = await count('@e[tag=qwhatcase_anim]');
    console.log(((Date.now() - s0) / 1000).toFixed(1), 'label', pos, 'anim entities', n);
    if (n === 0 && i > 2) break;
  }
  t1.quit(); await sleep(300); process.exit(0);
})();
