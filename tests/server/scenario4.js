const { join, sleep, consoleCmd, check, results, waitWindow, title, itemName, SERVER } = require('./lib');
const { execFileSync } = require('child_process');
const fs = require('fs'); const path = require('path');
const db = n => JSON.parse(execFileSync('python3', ['db2.py', n]).toString());
const comps = it => JSON.stringify(it?.components || []);
const logSince = n => fs.readFileSync(path.join(SERVER, 'server.log'), 'utf8').slice(n).replace(/\u001b\[[0-9;]*m/g, '');
const logLen = () => fs.readFileSync(path.join(SERVER, 'server.log'), 'utf8').length;
(async () => {
  // 1. Импорт предметов PTrap из инвентаря при входе (как importOldInventoryHats)
  let a = await join('Tester1'); await sleep(1500);
  let d = db('Tester1');
  check('legacy inventory item imported into collection (hat_160 → serpant_helmet_shiny)', d.hats.includes('serpant_helmet_shiny'), d);
  check('legacy item enchant kept (unbreaking 3)', d.enchants.serpant_helmet_shiny?.['minecraft:unbreaking'] === 3, d.enchants);
  check('legacy item removed from inventory', !a.inventory.items().some(i => i.name === 'carved_pumpkin'), a.inventory.items().map(i => i.name));
  // 2. Миграция players.yml с чарами
  let l = logLen();
  consoleCmd('hatcases migrate'); await sleep(2000); consoleCmd('hatcases migrate confirm'); await sleep(3000);
  check('migration ran', /Миграция выполнена/.test(logSince(l)), logSince(l).split('\n').filter(x => /Миграция|Предпросмотр/.test(x)));
  a.quit(); await sleep(1000); a = await join('Tester1'); await sleep(1500);
  d = db('Tester1');
  check('migrated hats present (halo_shiny, cookie? hat_150, hat_200)', ['halo_shiny', 'warden_knight_helmet_shiny', 'fisherman_hat'].every(h => d.hats.includes(h)), d.hats);
  check('PTrap anvil enchant migrated: halo_shiny thorns 3', d.enchants.halo_shiny?.['minecraft:thorns'] === 3, d.enchants);
  check('PTrap imported item enchant migrated: sharpness 5', d.enchants.warden_knight_helmet_shiny?.['minecraft:sharpness'] === 5, d.enchants);
  check('base Protection IV not stored as personal enchant', !JSON.stringify(d.enchants).includes('protection'));
  // 3. /hat без аргументов — коллекция (как в PTrap)
  a.chat('/hat'); let w = await waitWindow(a, w => /Мои шляпки/.test(title(w)));
  check('/hat opens collection like PTrap', !!w, w && title(w));
  // ПКМ по шляпе → действия
  const idx = w.slots.findIndex((s, i) => i < 45 && s && /Нимб/.test(itemName(s) || ''));
  await a.clickWindow(idx, 1, 0); await sleep(600);
  w = await waitWindow(a, w => /Шляпа:/.test(title(w)));
  check('RMB opens hat actions (enchant/equip/back)', !!w && itemName(w.slots[11]) === 'Наложить чар' && itemName(w.slots[15]) === 'Надеть', w && [itemName(w.slots[11]), itemName(w.slots[15])]);
  await a.clickWindow(15, 0, 0); await sleep(800);
  const head = a.inventory.slots[5];
  check('equipped hat is CARVED_PUMPKIN like PTrap', head?.name === 'carved_pumpkin', head?.name);
  check('hat has Protection IV (+ personal Thorns III)', /protection/.test(comps(head)) && /thorns/.test(comps(head)), comps(head).slice(0, 400));
  check('hat has +3 armor and +2 toughness attributes', /armor/.test(comps(head)) && /armor_toughness/.test(comps(head)) && /"amount":3/.test(comps(head)) && /"amount":2/.test(comps(head)), comps(head).slice(0, 600));
  // 4. Чары книгой
  consoleCmd('give Tester1 enchanted_book[stored_enchantments={fire_protection:4,respiration:3}]'); await sleep(800);
  a.chat('/hat'); w = await waitWindow(a, w => /Мои шляпки/.test(title(w)));
  const idx2 = w.slots.findIndex((s, i) => i < 45 && s && /Нимб/.test(itemName(s) || ''));
  await a.clickWindow(idx2, 1, 0); await sleep(500);
  await waitWindow(a, w => /Шляпа:/.test(title(w)));
  await a.clickWindow(11, 0, 0); await sleep(500);
  w = await waitWindow(a, w => /Наковальня/.test(title(w)));
  check('enchant menu opens', !!w);
  const bookSlot = a.currentWindow.slots.findIndex((s, i) => i >= 27 && s && s.name === 'enchanted_book');
  // двойной быстрый клик по книге
  a.clickWindow(bookSlot, 0, 0).catch(() => { }); a.clickWindow(bookSlot, 0, 0).catch(() => { });
  await sleep(1500);
  d = db('Tester1');
  check('book enchants saved to hat', d.enchants.halo_shiny?.['minecraft:fire_protection'] === 4 && d.enchants.halo_shiny?.['minecraft:respiration'] === 3, d.enchants.halo_shiny);
  check('book consumed exactly once', !a.inventory.items().some(i => i.name === 'enchanted_book'), a.inventory.items().map(i => i.name));
  check('worn hat refreshed with new enchants', /fire_protection/.test(comps(a.inventory.slots[5])), comps(a.inventory.slots[5]).slice(0, 300));
  check('enchant message', a.chatLog.some(m => /Чары наложены/.test(m)));
  if (a.currentWindow) a.closeWindow(a.currentWindow);
  // не книга
  consoleCmd('give Tester1 stone 1'); await sleep(500);
  a.chat('/hat'); w = await waitWindow(a, w => /Мои шляпки/.test(title(w)));
  await a.clickWindow(w.slots.findIndex((s, i) => i < 45 && s && /Нимб/.test(itemName(s) || '')), 1, 0); await sleep(400);
  await a.clickWindow(11, 0, 0); await sleep(500);
  const stoneSlot = a.currentWindow.slots.findIndex((s, i) => i >= 27 && s && s.name === 'stone');
  await a.clickWindow(stoneSlot, 0, 0); await sleep(600);
  check('non-book rejected and kept', a.chatLog.some(m => /нужна зачарованная книга/.test(m)) && a.inventory.items().some(i => i.name === 'stone'));
  a.closeWindow(a.currentWindow);
  // 5. Магазин PTrap
  a.chat('/hat menu'); w = await waitWindow(a, w => /Магазин шляп/.test(title(w)));
  check('/hat menu opens PTrap shop', !!w);
  check('shop shows price 75 ₽', w && /75 ₽/.test(comps(w.slots[0])), comps(w?.slots[0]).slice(0, 200));
  await a.clickWindow(0, 0, 0); await sleep(500);
  w = await waitWindow(a, w => /Покупка шляпы/.test(title(w)));
  check('detail menu opens', !!w);
  let mark = a.chatLog.length;
  await a.clickWindow(11, 0, 0); await sleep(600);
  check('Telegram link sent', a.chatLog.slice(mark).some(m => /написать @AntonSodeon/.test(m)), a.chatLog.slice(mark));
  // 6. Админ-меню выдачи
  const adm = await join('Tester2'); consoleCmd('op Tester2'); await sleep(800);
  adm.chat('/hat grant'); w = await waitWindow(adm, w => /Выбор игрока/.test(title(w)));
  check('/hat grant opens player picker', !!w);
  await adm.clickWindow(47, 0, 0); await sleep(500);
  adm.chat('Tester1'); await sleep(1000);
  w = await waitWindow(adm, w => /Выбор игрока/.test(title(w)));
  const heads = w ? w.slots.slice(0, 45).filter(Boolean).map(itemName) : [];
  check('chat search filters players', heads.length === 1 && heads[0] === 'Tester1', heads);
  await adm.clickWindow(0, 0, 0); await sleep(500);
  w = await waitWindow(adm, w => /Шляпы игрока/.test(title(w)));
  check('player actions menu', !!w);
  await adm.clickWindow(11, 0, 0); await sleep(500);
  w = await waitWindow(adm, w => /Выдать/.test(title(w)));
  const target = w.slots[44]; const tname = itemName(target);
  await adm.clickWindow(44, 0, 0); await sleep(1200);
  d = db('Tester1');
  check('admin GUI grants hat', a.chatLog.some(m => /добавлена шляпка/.test(m)), tname);
  // удалить одну
  w = await waitWindow(adm, w => /Шляпы игрока/.test(title(w)));
  await adm.clickWindow(15, 0, 0); await sleep(1000);
  w = await waitWindow(adm, w => /Удалить шляпы/.test(title(w)));
  const before = db('Tester1').hats.length;
  await adm.clickWindow(0, 0, 0); await sleep(1200);
  check('admin GUI removes one hat', db('Tester1').hats.length === before - 1);
  // /hat grant <player> <старый id>
  adm.closeWindow(adm.currentWindow);
  adm.chat('/hat grant Tester1 hat_300'); await sleep(1000);
  check('/hat grant player hat_300 (old id) works', db('Tester1').hats.includes('hat_hat_shiny'), db('Tester1').hats);
  // удалить все
  adm.chat('/hat grant'); w = await waitWindow(adm, w => /Выбор игрока/.test(title(w)));
  const t1 = w.slots.findIndex((s, i) => i < 45 && s && itemName(s) === 'Tester1');
  await adm.clickWindow(t1, 0, 0); await sleep(500);
  await adm.clickWindow(15, 0, 0); await sleep(1000);
  await waitWindow(adm, w => /Удалить шляпы/.test(title(w)));
  await adm.clickWindow(49, 0, 0); await sleep(500);
  await waitWindow(adm, w => /Подтвердите/.test(title(w)));
  await adm.clickWindow(11, 0, 0); await sleep(1200);
  d = db('Tester1');
  check('delete all with confirmation', d.hats.length === 0 && Object.keys(d.enchants).length === 0 && d.selected === null, d);
  check('worn hat removed after delete all', !a.inventory.slots[5], a.inventory.slots[5]?.name);
  check('player notified about cleared collection', a.chatLog.some(m => /коллекция шляп была очищена/.test(m)));
  // ПКМ тыквой при надетой шляпе (старое сообщение)
  a.quit(); adm.quit();
  const f = results.filter(r => !r.ok).length;
  console.log(`SUMMARY ${results.length - f}/${results.length} passed`); await sleep(500); process.exit(0);
})().catch(e => { console.error('CRASH', e); process.exit(1); });
