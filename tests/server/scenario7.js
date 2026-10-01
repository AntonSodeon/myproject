const { join, sleep, consoleCmd, check, results, waitWindow, title, SERVER } = require('./lib');
const { cmd, count, status, rows } = require('./wlib');
const { execSync } = require('child_process');
const Vec3 = require('vec3');
const A = new Vec3(4, -60, 0);
const phase = process.argv[2];
(async () => {
  if (phase === 'before') {
    const t1 = await join('Tester1');
    consoleCmd('hatcases key give Tester1 basic 1'); consoleCmd('tp Tester1 4.5 -60 3.5'); await sleep(1500);
    await t1.lookAt(A.offset(0.5, 0.5, 0.5), true); await t1.activateBlock(t1.blockAt(A));
    await waitWindow(t1, w => /Содержимое/.test(title(w)), 3000);
    await t1.clickWindow(53, 0, 0); await sleep(2000);
    console.log('ANIM_BEFORE_STOP', await count('@e[tag=qwhatcase_anim]'));
    console.log('OPS', JSON.stringify(rows("select count(*), sum(shown) from openings")));
    consoleCmd('stop');
    await sleep(500); process.exit(0);
  }
  const t1 = await join('Tester1'); await sleep(1500);
  consoleCmd('tp Tester1 4.5 -60 3.5'); await sleep(2500);
  check('restart mid-animation: no leftover animation entities', await count('@e[tag=qwhatcase_anim]') === 0);
  check('restart: each label exactly once near spawn (A, D)', await count('@e[tag=qwhatcase_label,distance=..20,x=0,y=-60,z=0]') === 2);
  check('restart: reward kept and shown as pending on join', t1.chatLog.some(m => /не успели увидеть/.test(m)) && rows('select count(*) from openings where shown=0')[0][0] === 0, t1.chatLog);
  const st = await status();
  check('restart: point not stuck busy', st.locks === 0 && st.animations === 0, st);
  consoleCmd('hatcases key give Tester1 basic 1'); await sleep(500);
  await t1.lookAt(A.offset(0.5, 0.5, 0.5), true); await t1.activateBlock(t1.blockAt(A));
  await waitWindow(t1, w => /Содержимое/.test(title(w)), 3000);
  await t1.clickWindow(53, 0, 0); await sleep(1000);
  check('restart: point can be opened again', await count('@e[type=item_display,tag=qwhatcase_anim]') === 7);
  await sleep(9000);
  check('restart: cleaned after normal finish', await count('@e[tag=qwhatcase_anim]') === 0);
  t1.quit(); const f = results.filter(r => !r.ok).length;
  console.log(`SUMMARY ${results.length - f}/${results.length} passed`); await sleep(400); process.exit(0);
})().catch(e => { console.error('CRASH', e); process.exit(1); });
