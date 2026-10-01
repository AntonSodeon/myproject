const { join, sleep, consoleCmd, check, results, waitWindow, title, itemName, SERVER } = require('./lib');
const { cmd, count, displays, status, rows, hatCmd, logLen, logSince } = require('./wlib');
const fs = require('fs'); const path = require('path');
const Vec3 = require('vec3');
const cfgDir = path.join(SERVER, 'plugins/QWHatCase');
const A = new Vec3(4, -60, 0), B = new Vec3(-4, -60, 0), D = new Vec3(0, -60, -8), C = new Vec3(2000, -60, 2000);
const keyOf = (name, type = 'basic') => { const r = rows(`select k.amount from keys k join players p on p.uuid=k.uuid where p.name_lower='${name.toLowerCase()}' and k.key_type='${type}'`); return r.length ? r[0][0] : 0; };
const tokensOf = name => rows(`select tokens from players where name_lower='${name.toLowerCase()}'`)[0][0];
const openingsOf = name => rows(`select o.op_id, o.hat_id, o.outcome, o.point, o.keys_spent, o.tokens_awarded, o.shown from openings o join players p on p.uuid=o.uuid where p.name_lower='${name.toLowerCase()}' order by o.created_at`);
function listenSounds(bot) {
  bot.sounds = [];
  const push = (name, pos) => bot.sounds.push({ name: String(name).replace('minecraft:', ''), t: Date.now(), pos });
  bot.on('soundEffectHeard', (name, pos) => push(name, pos));
  bot.on('hardcodedSoundEffectHeard', (id, cat, pos) => push(bot.registry.sounds[id]?.name ?? id, pos));
  bot.particles = [];
  bot._client.on('world_particles', p => bot.particles.push({ t: Date.now(), amount: p.amount ?? p.particleCount ?? 1, x: p.x, z: p.z }));
}
async function bindAt(admin, pos, caseId) {
  consoleCmd(`tp ${admin.username} ${pos.x + 0.5} ${pos.y} ${pos.z + 3.5}`); await sleep(900);
  await admin.lookAt(pos.offset(0.5, 0.5, 0.5), true); await sleep(300);
  admin.chat(`/hatcases point add ${caseId}`); await sleep(1000);
}
async function openMenu(bot, pos) {
  consoleCmd(`tp ${bot.username} ${pos.x + 0.5} ${pos.y} ${pos.z + 3.5}`); await sleep(900);
  const block = bot.blockAt(pos);
  await bot.lookAt(pos.offset(0.5, 0.5, 0.5), true);
  await bot.activateBlock(block);
  return waitWindow(bot, w => /Содержимое/.test(title(w)), 3000);
}
const near = (d, pos, r = 3.5) => Math.abs(d.x - (pos.x + 0.5)) < r && Math.abs(d.z - (pos.z + 0.5)) < r;

(async () => {
  consoleCmd('gamerule spawnChunkRadius 0');
  const admin = await join('Admin9'); consoleCmd('op Admin9');
  const t1 = await join('Tester1'); const t2 = await join('Tester2');
  consoleCmd('tp Admin9 0 -60 3'); consoleCmd('tp Tester1 0 -60 3'); consoleCmd('tp Tester2 0 -60 3'); await sleep(1500);
  for (const p of [A, B, D]) consoleCmd(`setblock ${p.x} ${p.y} ${p.z} minecraft:chest`);
  await sleep(800);
  [admin, t1, t2].forEach(listenSounds);
  consoleCmd('hatcases key give Tester1 basic 6'); consoleCmd('hatcases key give Tester2 basic 3'); await sleep(800);

  // 1. Название над блоком
  await bindAt(admin, A, 'basic'); await bindAt(admin, B, 'basic');
  await sleep(500);
  check('1. labels appear after binding (2 points → 2 TextDisplay)', await count('@e[tag=qwhatcase_label]') === 2, await count('@e[tag=qwhatcase_label]'));
  let out = await cmd(`data get entity @e[tag=qwhatcase_label,limit=1,sort=nearest,x=${A.x},y=${A.y},z=${A.z}]`);
  check('1. label text = case name + hint, billboard center', /Обычный кейс/.test(out) && /ПКМ — открыть кейс/.test(out) && /billboard: "center"/.test(out), out.slice(0, 300));
  const lpos = out.match(/Pos: \[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/);
  check('1. label centred above block', lpos && +lpos[1] === A.x + 0.5 && +lpos[3] === A.z + 0.5 && Math.abs(+lpos[2] - (A.y + 1.45)) < 0.01, lpos && lpos.slice(1));
  const base = await status();

  // 2. ПКМ — меню именно этого кейса
  let w = await openMenu(t1, A);
  check('2. RMB opens menu of bound case', !!w && /Обычный кейс/.test(title(w)), w && title(w));
  // 3/6/7. Открыть (тройной клик)
  const keys0 = keyOf('Tester1');
  const t1start = Date.now(); t1.sounds = []; t2.sounds = []; admin.sounds = [];
  t1.clickWindow(53, 0, 0).catch(() => { }); t1.clickWindow(53, 0, 0).catch(() => { }); t1.clickWindow(53, 0, 0).catch(() => { });
  await sleep(700);
  check('3. menu closed after save', !t1.currentWindow || !/Содержимое/.test(title(t1.currentWindow)));
  const spin = await displays();
  check('3. animation runs above the RIGHT block (A)', spin.length === 7 && spin.every(d => near(d, A)) && spin.every(d => Math.abs(d.y - (A.y + 1.9)) < 0.05), spin.map(d => [d.x, d.y, d.z]));
  check('6. exactly 1 key spent', keyOf('Tester1') === keys0 - 1, [keys0, keyOf('Tester1')]);
  check('7. triple click → one opening', openingsOf('Tester1').length === 1);
  const op1 = openingsOf('Tester1')[0];
  check('6. opening stores point', op1[3] === `world;${A.x};${A.y};${A.z}`, op1);
  // 4. Модели из содержимого кейса
  const basicCmds = new Set(rows(`select 1`).length ? [] : []);
  const yaml = require('child_process').execFileSync('python3', ['-c', `import yaml,json;c=yaml.safe_load(open('${cfgDir}/hats.yml'))['hats'];print(json.dumps({k:v['custom-model-data'] for k,v in c.items() if v['rarity'] in ('common','uncommon','rare')}))`]).toString();
  const allowed = new Set(Object.values(JSON.parse(yaml)));
  await sleep(1500);
  const spin2 = await displays();
  const seen = spin2.filter(d => d.cmd != null && d.scale > 0.01).map(d => d.cmd);
  check('4. real hat models from this case in the strip', seen.length >= 3 && seen.every(c => allowed.has(c)), seen);
  check('4. entities tagged with point and operation id', spin2.every(d => d.point === `world;${A.x};${A.y};${A.z}` && d.op === op1[0]));
  // 5. результат = сохранённая награда (замер относительно старта этой анимации)
  while (Date.now() - t1start < 6900) await sleep(50);
  const resA = (await displays()).filter(d => near(d, A));
  const winner = resA.sort((a, b) => b.scale - a.scale)[0];
  check('5. final model = saved reward, enlarged', winner && winner.cmd === hatCmd(op1[1]) && winner.scale > 1.0
    && resA.filter(d => d !== winner).every(d => d.scale < 0.05), { winner, saved: op1[1], cmd: hatCmd(op1[1]) });
  out = await cmd(`data get entity @e[type=text_display,tag=qwhatcase_anim,limit=1,sort=nearest,x=${A.x},y=${A.y},z=${A.z}] text`);
  check('5. result caption shows hat & rarity', /Вы получили|Выпала/.test(out) && /Редкость|Уже есть/.test(out), out.slice(0, 200));
  check('5. player got chat result', t1.chatLog.some(m => /Вы получили:|Выпала:/.test(m)), t1.chatLog.slice(-4));
  out = await cmd(`data get entity @e[tag=qwhatcase_label,limit=1,sort=nearest,x=${A.x},y=${A.y},z=${A.z}] Pos`);
  check('label raised during animation', /-57\.6/.test(out), out.trim().split('\n').pop());
  // 13. всё убрано
  while (Date.now() - t1start < 10200) await sleep(50);
  const st = await status();
  check('13. no temporary entities, locks or extra tasks after finish', (await count('@e[tag=qwhatcase_anim]')) === 0 && st.animations === 0 && st.locks === 0 && st.tasks === base.tasks, { st, base });
  out = await cmd(`data get entity @e[tag=qwhatcase_label,limit=1,sort=nearest,x=${A.x},y=${A.y},z=${A.z}] Pos`);
  check('label returned to original height', /-58\.55/.test(out), out.trim().split('\n').pop());
  // 15. звуки
  const names = t1.sounds.filter(s => Date.now() - s.t < 15000).map(s => s.name);
  const ticks = names.filter(n => n === 'custom.mystery_crate.scroll').length;
  check('15. sounds: start, ~30 ticks, slowdown, final', names.includes('custom.mystery_crate.open') && ticks >= 25 && ticks <= 32
    && names.includes('block.note_block.bell') && (names.some(n => /mystery_crate.unlock|currency.tokens/.test(n))), { ticks, uniq: [...new Set(names)] });
  const tickTimes = t1.sounds.filter(s => s.name === 'custom.mystery_crate.scroll').map(s => s.t);
  const gaps = tickTimes.slice(1).map((t, i) => t - tickTimes[i]);
  check('15. tick interval grows (slowdown)', gaps.length > 5 && gaps.slice(-3).reduce((a, b) => a + b) > 3 * gaps.slice(0, 3).reduce((a, b) => a + b), gaps);
  check('15. particles near the case', t1.particles.length > 0, t1.particles.length);
  // 9/10. второй игрок на занятой точке; другая точка независима
  w = await openMenu(t1, A); await t1.clickWindow(53, 0, 0); const t1second = Date.now(); await sleep(600);
  // 9. второй игрок на той же точке
  w = await openMenu(t2, A);
  check('9. second player can still view contents', !!w);
  const k2 = keyOf('Tester2');
  let mark = t2.chatLog.length;
  await t2.clickWindow(53, 0, 0); await sleep(700);
  check('9. second player blocked: message, no key spent', t2.chatLog.slice(mark).some(m => /открывает другой игрок/.test(m)) && keyOf('Tester2') === k2, t2.chatLog.slice(mark));
  const seenByT2 = Object.values(t2.entities).filter(e => e.name === 'item_display' && e.position.distanceTo(A) < 4).length;
  check('9. second player sees the animation entities', seenByT2 >= 5, seenByT2);
  if (t2.currentWindow) t2.closeWindow(t2.currentWindow);
  // 10. другая точка независимо
  w = await openMenu(t2, B);
  await t2.clickWindow(53, 0, 0); await sleep(900);
  const both = await displays();
  check('10. another point runs simultaneously', both.filter(d => near(d, A)).length === 7 && both.filter(d => near(d, B)).length === 7 && keyOf('Tester2') === k2 - 1);
  while (Date.now() - t1second < 10000) await sleep(100);
  await sleep(4000);
  // 11. команда — GUI
  t1.sounds = [];
  t1.chat('/cases open basic'); w = await waitWindow(t1, w => /Открытие/.test(title(w)), 3000);
  await sleep(500);
  check('11. /cases open uses GUI animation, no world entities', !!w && (await count('@e[tag=qwhatcase_anim]')) === 0);
  t1.closeWindow(t1.currentWindow); await sleep(500);
  // 8. дубликат
  let cases = fs.readFileSync(path.join(cfgDir, 'cases.yml'), 'utf8');
  const original = cases;
  cases += `\n  dup:\n    name: '&7Тест дубликата'\n    key: dup\n    world-animation: {spin-duration-ms: 1500, result-duration-ms: 800}\n    rewards:\n      - hat: halo_shiny\n        weight: 1\n`;
  fs.writeFileSync(path.join(cfgDir, 'cases.yml'), cases);
  await cmd('hatcases reload'); await cmd('hatcases key give Tester1 dup 2');
  await bindAt(admin, D, 'dup');
  out = await cmd(`data get entity @e[tag=qwhatcase_label,limit=1,sort=nearest,x=${D.x},y=${D.y},z=${D.z}] text`);
  check('label shows name of the bound case (dup)', /Тест дубликата/.test(out));
  const tok0 = tokensOf('Tester1');
  w = await openMenu(t1, D); await t1.clickWindow(53, 0, 0); await sleep(3000);
  w = await openMenu(t1, D); await t1.clickWindow(53, 0, 0); await sleep(1700);
  out = await cmd(`data get entity @e[type=text_display,tag=qwhatcase_anim,limit=1,sort=nearest,x=${D.x},y=${D.y},z=${D.z}] text`);
  await sleep(1500);
  const dupOps = openingsOf('Tester1').filter(o => o[3] === `world;${D.x};${D.y};${D.z}`);
  check('8. duplicate compensated exactly once (legendary 150 tokens)', dupOps.length === 2 && dupOps[1][2] === 'DUPLICATE' && tokensOf('Tester1') === tok0 + 150, { dupOps, tok0, now: tokensOf('Tester1') });
  check('8. duplicate caption & chat', /Уже есть в коллекции/.test(out) && t1.chatLog.some(m => /Компенсация: 150/.test(m)), out.slice(0, 200));
  // 12. выход во время анимации
  w = await openMenu(t1, A); await t1.clickWindow(53, 0, 0); await sleep(1500);
  t1.quit(); await sleep(1000);
  let ops = openingsOf('Tester1'); let last = ops[ops.length - 1];
  check('12. quit mid-animation: reward saved, not shown yet', last[3] === `world;${A.x};${A.y};${A.z}` && last[6] === 0);
  await sleep(8000);
  check('12. animation finished & cleaned after owner left', (await count('@e[tag=qwhatcase_anim]')) === 0 && (await status()).locks === 0);
  const t1b = await join('Tester1'); listenSounds(t1b); await sleep(1200);
  check('12. rejoin → pending result shown once', t1b.chatLog.some(m => /не успели увидеть/.test(m)) && openingsOf('Tester1').every(o => o[6] === 1));
  // удаление привязки во время анимации
  w = await openMenu(t1b, B); await t1b.clickWindow(53, 0, 0); await sleep(1500);
  mark = t1b.chatLog.length;
  consoleCmd(`tp Admin9 ${B.x + 0.5} ${B.y} ${B.z + 3.5}`); await sleep(800);
  await admin.lookAt(B.offset(0.5, 0.5, 0.5), true); admin.chat('/hatcases point remove'); await sleep(1200);
  check('binding removed mid-animation → safe stop, entities & label removed, result in chat',
    (await count('@e[tag=qwhatcase_anim]')) === 0 && (await count('@e[tag=qwhatcase_label]')) === 2 && t1b.chatLog.slice(mark).some(m => /Вы получили:|Выпала:/.test(m)), t1b.chatLog.slice(mark));
  // предпросмотр
  const opsBefore = rows('select count(*) from openings')[0][0];
  consoleCmd(`tp Admin9 ${A.x + 0.5} ${A.y} ${A.z + 3.5}`); await sleep(800);
  await admin.lookAt(A.offset(0.5, 0.5, 0.5), true);
  admin.chat('/hatcases preview'); await sleep(800);
  const pv = await displays();
  mark = admin.chatLog.length;
  admin.chat('/hatcases preview'); await sleep(600);
  check('preview runs above the point (no keys, no reward)', pv.length === 7 && pv.every(d => near(d, A) && d.op.startsWith('preview-')));
  check('preview respects point busy', admin.chatLog.slice(mark).some(m => /открывает другой игрок/.test(m)));
  await sleep(9500);
  check('preview leaves nothing and changes no data', (await count('@e[tag=qwhatcase_anim]')) === 0 && rows('select count(*) from openings')[0][0] === opsBefore);
  // 15. применение настроек
  let cfg = fs.readFileSync(path.join(cfgDir, 'config.yml'), 'utf8');
  const cfgOriginal = cfg;
  cfg = cfg.replace('spin-duration-ms: 6000', 'spin-duration-ms: 2000').replace('result-duration-ms: 3000', 'result-duration-ms: 1000')
    .replace("tick:      {enabled: true", "tick:      {enabled: false")
    .replace("start:  {enabled: true, type: ENCHANT, count: 40", "start:  {enabled: true, type: ENCHANT, count: 7");
  fs.writeFileSync(path.join(cfgDir, 'config.yml'), cfg);
  await cmd('hatcases reload');
  t1b.sounds = []; t1b.particles = [];
  w = await openMenu(t1b, A);
  const s0 = Date.now(); await t1b.clickWindow(53, 0, 0);
  let gone = 0;
  await sleep(1200);
  const trace = [];
  for (let i = 0; i < 60; i++) { const n = await count('@e[type=item_display,tag=qwhatcase_anim]'); trace.push([Date.now() - s0, n]); if (n === 0) { gone = Date.now() - s0; break; } }
  console.log('TRACE', JSON.stringify(trace));
  const n2 = t1b.sounds.map(s => s.name);
  const startBurst = t1b.particles.filter(p => p.t - s0 < 600);
  check('15. durations applied (2s + 1s ≈ 3s)', gone > 2500 && gone < 5500, gone);
  check('15. disabled tick sound not played', !n2.includes('custom.mystery_crate.scroll') && n2.includes('custom.mystery_crate.open'), [...new Set(n2)]);
  check('15. start particle count applied (7)', startBurst.some(p => p.amount === 7), startBurst.map(p => p.amount));
  fs.writeFileSync(path.join(cfgDir, 'config.yml'), cfgOriginal); fs.writeFileSync(path.join(cfgDir, 'cases.yml'), original);
  await cmd('hatcases reload');
  // 14. загрузка чанка — одна надпись
  consoleCmd(`tp Admin9 ${C.x + 0.5} ${C.y} ${C.z + 3.5}`); await sleep(2500);
  consoleCmd(`setblock ${C.x} ${C.y} ${C.z} minecraft:chest`); await sleep(500);
  await bindAt(admin, C, 'basic');
  const labelC = async () => count(`@e[tag=qwhatcase_label,x=${C.x},y=${C.y},z=${C.z},distance=..3]`);
  check('14. far point label spawned', await labelC() === 1);
  for (let round = 1; round <= 2; round++) {
    consoleCmd('tp Admin9 0 -60 3'); await sleep(14000);
    out = await cmd(`execute if loaded ${C.x} ${C.y} ${C.z}`, 500);
    const unloaded = /Test failed/.test(out);
    const whileUnloaded = await labelC();
    consoleCmd(`tp Admin9 ${C.x + 0.5} ${C.y} ${C.z + 3.5}`); await sleep(3000);
    check(`14. chunk unload/reload #${round}: label restored exactly once`, unloaded && whileUnloaded === 0 && await labelC() === 1, { unloaded, whileUnloaded });
  }
  console.log('STATUS', JSON.stringify(await status()));
  [admin, t1b, t2].forEach(b => b.quit());
  const f = results.filter(r => !r.ok).length;
  console.log(`SUMMARY ${results.length - f}/${results.length} passed`); await sleep(500); process.exit(0);
})().catch(e => { console.error('CRASH', e); process.exit(1); });
