const { join, sleep, consoleCmd, check, results, waitWindow, title, itemName, chatHas, inventoryItems, SERVER } = require('./lib');
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const db = name => JSON.parse(execFileSync('python3', ['db.py', '../server/plugins/QWHatCase/data.db', name]).toString());
const cfgDir = path.join(SERVER, 'plugins/QWHatCase');
const logLen = () => fs.readFileSync(path.join(SERVER, 'server.log'), 'utf8').length;
const logSince = n => fs.readFileSync(path.join(SERVER, 'server.log'), 'utf8').slice(n).replace(/\u001b\[[0-9;]*m/g, '');

(async () => {
  // --- после перезапуска сервера
  let a = await join('Tester1');
  await sleep(1000);
  const s = db('Tester1');
  check('after restart: selected hat restored on head', a.inventory.slots[5]?.name === 'paper' && s.selected && s.selected === s.hats[0], [itemName(a.inventory.slots[5]), s.selected]);
  check('after restart: balances & collection kept', s.tokens === 450 && s.keys.basic === 3 && s.hats.length === 4, s);

  // --- выход во время анимации (повтор исправленного сценария)
  a.chat('/cases open basic');
  await waitWindow(a, w => /Открытие/.test(title(w)), 4000);
  await sleep(800);
  a.quit(); await sleep(1500);
  check('quit mid-animation: saved, not shown', db('Tester1').unshown === 1 && db('Tester1').keys.basic === 2, db('Tester1'));
  a = await join('Tester1'); await sleep(1500);
  check('rejoin: unseen result is shown', a.chatLog.some(m => /не успели увидеть/.test(m)) && db('Tester1').unshown === 0, a.chatLog.slice(-4));

  // --- закрытие окна во время анимации (исправленный путь, игрок онлайн)
  let mark = a.chatLog.length;
  a.chat('/cases open basic');
  let w = await waitWindow(a, w => /Открытие/.test(title(w)), 4000);
  await sleep(500); a.closeWindow(w); await sleep(800);
  check('close mid-animation still reveals in chat', chatHas(a, /получили/, mark) && db('Tester1').unshown === 0);

  // --- дубликат и компенсация: кейс с одной наградой через reload
  const dupHat = db('Tester1').hats[0];
  const rar = execFileSync('python3', ['-c', `import yaml;print(yaml.safe_load(open('${cfgDir}/hats.yml'))['hats']['${dupHat}']['rarity'])`]).toString().trim();
  const comp = {common: 5, uncommon: 10, rare: 25, epic: 60, legendary: 150}[rar] * 2 + 1;
  let cases = fs.readFileSync(path.join(cfgDir, 'cases.yml'), 'utf8');
  const original = cases;
  cases += `\n  dup:\n    name: '&7Тест дубликата'\n    key: dup\n    animation: {enabled: false}\n    duplicate: {multiplier: 2.0, bonus: 1}\n    rewards:\n      - hat: ${dupHat}\n        weight: 1\n`;
  fs.writeFileSync(path.join(cfgDir, 'cases.yml'), cases);
  let l = logLen();
  consoleCmd('hatcases reload'); await sleep(1500);
  check('reload applies new case', /кейсов 4/.test(logSince(l)), logSince(l).slice(0, 300));
  consoleCmd('hatcases key give Tester1 dup 2'); await sleep(800);
  const t0 = db('Tester1').tokens; const dup0 = db('Tester1').dups;
  mark = a.chatLog.length;
  a.chat('/cases open dup'); await sleep(1500);
  // pine_cap — common, компенсация 5 → 5*2+1 = 11
  const d1 = db('Tester1');
  check('duplicate: compensation once = rarity×2+1 tokens', d1.tokens === t0 + comp && d1.dups === dup0 + 1, { t0, now: d1.tokens, comp, rar });
  const rw = await waitWindow(a, w => /Ваша награда/.test(title(w)), 2000);
  const lore = rw && JSON.stringify(rw.slots[13]?.components || '');
  check('no-animation mode shows result screen with "Уже есть в коллекции", tokens, balance', !!rw && /Уже есть в коллекции/.test(lore) && lore.includes('+' + comp) && lore.includes(String(d1.tokens)));
  check('chat shows duplicate tokens and balance', chatHas(a, new RegExp('\\+' + comp + ' .*' + d1.tokens), mark), a.chatLog.slice(mark));
  if (rw) a.closeWindow(rw);

  // --- ошибочная конфигурация не заменяет рабочую
  fs.writeFileSync(path.join(cfgDir, 'cases.yml'), 'cases:\n  broken: [\n');
  l = logLen();
  consoleCmd('hatcases reload'); await sleep(1500);
  check('broken YAML rejected, old config kept', /НЕ применена/.test(logSince(l)), logSince(l).slice(0, 200));
  a.chat('/cases open dup'); await sleep(1200);
  check('old config still works after rejected reload', db('Tester1').dups === dup0 + 2);
  if (a.currentWindow) a.closeWindow(a.currentWindow);
  // кейс с ошибкой отключается, остальные работают
  fs.writeFileSync(path.join(cfgDir, 'cases.yml'), original + `\n  bad:\n    rewards:\n      - hat: does_not_exist\n        weight: 1\n`);
  l = logLen();
  consoleCmd('hatcases reload'); await sleep(1500);
  check('invalid case disabled with clear message, others loaded', /Кейс 'bad' отключён/.test(logSince(l)) && /кейсов 3/.test(logSince(l)), logSince(l).slice(0, 400));
  fs.writeFileSync(path.join(cfgDir, 'cases.yml'), original);
  consoleCmd('hatcases reload'); await sleep(1000);

  // --- точки кейсов
  const b = await join('Tester2');
  consoleCmd('op Tester2'); consoleCmd('gamemode survival Tester1');
  const p = a.entity.position.floored();
  const bx = p.x + 2, by = p.y, bz = p.z;
  consoleCmd(`setblock ${bx} ${by} ${bz} minecraft:chest`); consoleCmd(`tp Tester2 ${bx - 2} ${by} ${bz + 2}`); consoleCmd(`tp Tester1 ${bx + 2} ${by} ${bz}`); await sleep(1500);
  const bb = b.blockAt(new (require('vec3'))(bx, by, bz));
  await b.lookAt(bb.position.offset(0.5, 0.5, 0.5), true); await sleep(500);
  b.chat('/hatcases point add basic'); await sleep(1000);
  check('admin binds block to case', b.chatLog.some(m => /привязан к кейсу/.test(m)), b.chatLog.slice(-2));
  l = logLen();
  consoleCmd('hatcases point list'); await sleep(800);
  check('point list shows point', new RegExp(`${bx} ${by} ${bz}.*basic`).test(logSince(l)));
  const keysBefore = db('Tester1').keys.basic;
  const blockA = a.blockAt(new (require('vec3'))(bx, by, bz));
  await a.lookAt(blockA.position.offset(0.5, 0.5, 0.5), true);
  await a.activateBlock(blockA); 
  w = await waitWindow(a, w => /Содержимое/.test(title(w)), 3000);
  check('right-click on point opens case menu (not chest)', !!w, w && title(w));
  check('click on block does not charge key', db('Tester1').keys.basic === keysBefore);
  if (w) a.closeWindow(w);
  try { await a.dig(blockA, true); } catch (e) { }
  await sleep(1500);
  check('player cannot break bound block', a.blockAt(blockA.position).name === 'chest');
  await b.lookAt(bb.position.offset(0.5, 0.5, 0.5), true);
  b.chat('/hatcases point remove'); await sleep(1000);
  check('admin removes binding', b.chatLog.some(m => /Привязка блока.*удалена/.test(m)));

  // --- ресурс-пак: PLUGIN режим, отказ / принятие
  let cfg = fs.readFileSync(path.join(cfgDir, 'config.yml'), 'utf8');
  const cfgOriginal = cfg;
  cfg = cfg.replace("url: ''", "url: 'http://127.0.0.1:8765/pack.zip'").replace("sha1: ''", "sha1: '6c49d711de3e86c76da007e2fd7ac36ba34385fb'");
  fs.writeFileSync(path.join(cfgDir, 'config.yml'), cfg);
  consoleCmd('hatcases reload'); await sleep(1000);
  a.quit(); b.quit(); await sleep(1000);
  const decl = require('mineflayer').createBot({ host: '127.0.0.1', port: 25599, username: 'Tester1', version: '1.21.11', auth: 'offline' });
  decl.chatLog = []; decl.on('messagestr', m => decl.chatLog.push(m));
  let packOffered = false;
  decl._client.on('add_resource_pack', pk => { packOffered = true; decl._client.write('resource_pack_receive', { uuid: pk.uuid, result: 1 }); });
  await new Promise(r => decl.once('spawn', r)); await sleep(3000);
  check('pack offered on join', packOffered);
  check('decline handled with message + retry', decl.chatLog.some(m => /отказались/.test(m)) && decl.chatLog.some(m => /загрузить ресурс-пак снова/.test(m)), decl.chatLog);
  const k = db('Tester1').keys.basic;
  decl.chat('/cases open basic'); await sleep(1000);
  check('cases blocked without pack (no key spent)', decl.chatLog.some(m => /нужен ресурс-пак/.test(m)) && db('Tester1').keys.basic === k);
  decl.chat('/hats equip ' + db('Tester1').hats[1]); await sleep(600);
  check('collection still works without pack', decl.chatLog.some(m => /надета/.test(m)));
  decl.quit(); await sleep(1000);
  const acc = require('mineflayer').createBot({ host: '127.0.0.1', port: 25599, username: 'Tester1', version: '1.21.11', auth: 'offline' });
  acc.chatLog = []; acc.on('messagestr', m => acc.chatLog.push(m));
  acc._client.on('add_resource_pack', pk => { acc._client.write('resource_pack_receive', { uuid: pk.uuid, result: 3 }); setTimeout(() => acc._client.write('resource_pack_receive', { uuid: pk.uuid, result: 0 }), 500); });
  await new Promise(r => acc.once('spawn', r)); await sleep(3000);
  check('accepted pack → "загружен" state', acc.chatLog.some(m => /Ресурс-пак загружен/.test(m)), acc.chatLog);
  acc.chat('/cases open basic'); await sleep(800);
  check('cases allowed after pack loaded', db('Tester1').keys.basic === k - 1, db('Tester1').keys);
  await sleep(6500);
  fs.writeFileSync(path.join(cfgDir, 'config.yml'), cfgOriginal);
  consoleCmd('hatcases reload'); await sleep(800);
  l = logLen();
  consoleCmd('hatcases history Tester1'); await sleep(800);
  check('history shows openings with keys/outcome', /История открытий Tester1/.test(logSince(l)) && /ключей: 1/.test(logSince(l)), logSince(l).split('\n').slice(0, 4));
  acc.quit();
  const failed = results.filter(r => !r.ok).length;
  console.log(`SUMMARY ${results.length - failed}/${results.length} passed`);
  await sleep(500); process.exit(0);
})().catch(e => { console.error('CRASH', e); process.exit(1); });
