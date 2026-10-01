const { join, sleep, consoleCmd, check, results, waitWindow, title, itemName, chatHas, inventoryItems } = require('./lib');
const { execFileSync } = require('child_process');
const db = name => JSON.parse(execFileSync('python3', ['db.py', '../server/plugins/QWHatCase/data.db', name]).toString());

(async () => {
  const a = await join('Tester1');
  const b = await join('Tester2');
  consoleCmd('hatcases key give Tester1 basic 6');
  consoleCmd('hatcases key give Tester2 basic 3');
  consoleCmd('hatcases tokens give Tester1 500');
  await sleep(1500);
  check('keys given (Tester1=6, Tester2=3)', db('Tester1').keys.basic === 6 && db('Tester2').keys.basic === 3, [db('Tester1').keys, db('Tester2').keys]);
  check('player notified about keys', a.chatLog.some(m => /ключ/.test(m)));

  // --- главное меню
  a.chat('/hats');
  let w = await waitWindow(a, w => /Шляпки/.test(title(w)));
  check('main menu opens', !!w, w && title(w));
  const mainNames = w ? w.slots.slice(0, 27).map(itemName) : [];
  check('main menu has 5 sections', ['Кейсы', 'Мои шляпки', 'Каталог за жетоны', 'Снять шляпку', 'Ресурс-пак'].every(s => mainNames.some(n => n && n.includes(s))), mainNames.filter(n => n && n.trim()));

  // --- попытки вытащить предметы: обычный клик, shift, цифра, Q, двойной клик
  const before = inventoryItems(a).length;
  for (const [mode, button] of [[0, 0], [1, 0], [2, 0], [2, 3], [4, 1], [6, 0]]) {
    try { await a.clickWindow(4, button, mode); } catch (e) { }
    await sleep(300);
  }
  await sleep(500);
  const afterInv = inventoryItems(a);
  check('no GUI item extracted (click/shift/number/drop/double)', afterInv.length === before && !a.inventory.cursor ?.name, afterInv);

  // --- меню кейсов: клик по «Кейсы»
  await a.clickWindow(10, 0, 0); await sleep(700);
  w = await waitWindow(a, w => /Кейсы/.test(title(w)));
  check('cases menu opens from main menu', !!w);
  const iconName = w && itemName(w.slots[10]);
  const openName = w && itemName(w.slots[19]);
  check('view and open buttons are different items', w && w.slots[10] && w.slots[19] && w.slots[10].name !== w.slots[19].name, [iconName, w?.slots[10]?.name, openName, w?.slots[19]?.name]);

  // --- содержимое кейса
  await a.clickWindow(10, 0, 0); await sleep(700);
  w = await waitWindow(a, w => /Содержимое/.test(title(w)));
  check('case content opens', !!w, w && title(w));
  const lore = w && w.slots[0] && JSON.stringify(w.slots[0].components || w.slots[0].nbt || '');
  check('reward shows chance %', !!lore && /Шанс/.test(lore) && /%/.test(lore));
  check('content has pages (229 rewards > 45)', w && !!w.slots[50]);
  a.closeWindow(w); await sleep(300);

  // --- открытие: двойной быстрый запрос
  let mark = a.chatLog.length;
  a.chat('/cases open basic'); a.chat('/cases open basic');
  w = await waitWindow(a, w => /Открытие/.test(title(w)), 4000);
  check('animation window opens', !!w, w && title(w));
  await sleep(300);
  check('second rapid open refused (one active opening)', chatHas(a, /Дождитесь окончания/, mark), a.chatLog.slice(mark));
  check('key charged before animation ends (1 spent)', db('Tester1').keys.basic === 5, db('Tester1').keys);
  check('result saved before animation', db('Tester1').openings === 1);
  // клики во время анимации ничего не дают
  try { await a.clickWindow(13, 0, 0); } catch (e) { }
  try { await a.clickWindow(13, 0, 1); } catch (e) { }
  await sleep(300);
  check('nothing taken during animation', inventoryItems(a).length === before);
  // закрываем окно посреди анимации
  mark = a.chatLog.length;
  a.closeWindow(w);
  await sleep(1000);
  check('closing GUI mid-animation shows result in chat', chatHas(a, /получили|подарил/, mark), a.chatLog.slice(mark));
  let s1 = db('Tester1');
  check('reward kept after closing GUI', s1.hats.length >= 1 && s1.unshown === 0, s1);

  // --- одновременное открытие двумя игроками
  mark = a.chatLog.length; const markB = b.chatLog.length;
  a.chat('/cases open basic'); b.chat('/cases open basic');
  const wa = await waitWindow(a, w => /Открытие/.test(title(w)), 4000);
  const wb = await waitWindow(b, w => /Открытие/.test(title(w)), 4000);
  check('two players open simultaneously', !!wa && !!wb);
  const ra = await waitWindow(a, w => /Ваша награда/.test(title(w)), 9000);
  const rb = await waitWindow(b, w => /Ваша награда/.test(title(w)), 9000);
  check('animation finishes into result screen (both)', !!ra && !!rb);
  const resA = ra && itemName(ra.slots[13]);
  const resB = rb && itemName(rb.slots[13]);
  const dA = db('Tester1'), dB = db('Tester2');
  check('results not mixed: each player has own opening', dA.openings === 2 && dB.openings === 1 && dA.keys.basic === 4 && dB.keys.basic === 2, { resA, resB, a: dA.keys, b: dB.keys });
  // кнопка «Открыть ещё» быстро трижды
  if (ra) {
    mark = a.chatLog.length;
    await a.clickWindow(15, 0, 0); a.clickWindow(15, 0, 0).catch(() => { }); a.clickWindow(15, 0, 0).catch(() => { });
    await sleep(1200);
    const d = db('Tester1');
    check('triple click "open again" starts exactly one opening', d.openings === 3 && d.keys.basic === 3, d);
    // выход во время анимации
    a.quit(); await sleep(1500);
    const d2 = db('Tester1');
    check('quit mid-animation: result saved, not yet shown', d2.openings === 3 && d2.unshown === 1, d2);
  }
  b.closeWindow(rb); await sleep(300);
  const a2 = await join('Tester1');
  await sleep(1500);
  check('on rejoin player is notified of unseen result', a2.chatLog.some(m => /не успели увидеть/.test(m)), a2.chatLog);
  check('pending marked shown', db('Tester1').unshown === 0);

  // --- надевание шляпы: настоящий шлем переносится в инвентарь
  const hat = db('Tester1').hats[0];
  consoleCmd('give Tester1 minecraft:diamond_helmet 1'); await sleep(800);
  const helm = a2.inventory.items().find(i => i.name === 'diamond_helmet');
  await a2.equip(helm, 'head'); await sleep(500);
  check('real helmet worn', a2.inventory.slots[5]?.name === 'diamond_helmet');
  consoleCmd('clear Tester1 minecraft:dirt'); // нет эффекта, просто синхронизация
  a2.chat('/hats equip ' + hat); await sleep(800);
  check('hat equipped into head slot', a2.inventory.slots[5] && a2.inventory.slots[5].name === 'carved_pumpkin', a2.inventory.slots[5]?.name);
  check('real helmet moved to inventory (not lost)', a2.inventory.items().some(i => i.name === 'diamond_helmet'));
  check('selected hat saved', db('Tester1').selected === hat);
  // попытки снять шляпу
  const invBefore = inventoryItems(a2).map(x => x.name).sort().join();
  for (const [mode, button] of [[0, 0], [1, 0], [2, 0], [4, 0], [4, 1]]) {
    try { await a2.clickWindow(5, button, mode); } catch (e) { }
    await sleep(300);
  }
  // ПКМ шлемом при надетой шляпе
  const helm2 = a2.inventory.items().find(i => i.name === 'diamond_helmet');
  await a2.equip(helm2, 'hand'); await sleep(200); a2.activateItem(); await sleep(600);
  check('hat cannot be removed via clicks/shift/number/drop/right-click helmet', a2.inventory.slots[5]?.name === 'carved_pumpkin'
    && !a2.inventory.items().some(i => i.name === 'carved_pumpkin') && !a2.inventory.cursor, { head: a2.inventory.slots[5]?.name, items: inventoryItems(a2) });
  check('helmet not duplicated', a2.inventory.items().filter(i => i.name === 'diamond_helmet').length === 1);
  // смерть
  consoleCmd('kill Tester1'); await sleep(1000);
  try { a2.respawn(); } catch (e) { }
  await sleep(2000);
  const drops = Object.values(a2.entities).filter(e => e.name === 'item' && e.metadata && JSON.stringify(e.metadata).includes('carved_pumpkin'));
  check('hat restored after death/respawn', a2.inventory.slots[5]?.name === 'carved_pumpkin', a2.inventory.slots[5]?.name);
  check('hat not dropped on death', drops.length === 0, drops.length);
  // снять
  a2.chat('/hats unequip'); await sleep(600);
  check('unequip clears head slot', !a2.inventory.slots[5]);
  check('unequip saved', db('Tester1').selected === null);
  // надеть только открытую
  a2.chat('/hats equip halo_shiny'); await sleep(600);
  check('cannot equip hat not owned', !a2.inventory.slots[5] && a2.chatLog.some(m => /нет в вашей коллекции/.test(m)));

  // --- покупка: быстрые повторные клики «Купить»
  a2.chat('/hats shop'); let sw = await waitWindow(a2, w => /Каталог/.test(title(w)));
  const tokensBefore = db('Tester1').tokens;
  let slot = -1;
  for (let i = 0; i < 45; i++) { const it = sw.slots[i]; if (it && !db('Tester1').hats.length) break; }
  // первая не купленная шляпа дешевле баланса
  const owned = new Set(db('Tester1').hats);
  for (let i = 0; i < 45; i++) { if (sw.slots[i]) { slot = i; break; } }
  await a2.clickWindow(slot, 0, 0); await sleep(600);
  const cw = await waitWindow(a2, w => /Подтверждение/.test(title(w)));
  check('purchase confirmation opens', !!cw);
  a2.clickWindow(11, 0, 0).catch(() => { }); a2.clickWindow(11, 0, 0).catch(() => { }); a2.clickWindow(11, 0, 0).catch(() => { });
  await sleep(1200);
  const after = db('Tester1');
  check('purchase charged once, hat added once', after.tokens === tokensBefore - 50 && after.hats.length === owned.size + 1, { before: tokensBefore, after: after.tokens, hats: after.hats.length });
  a2.chat('/hats shop'); sw = await waitWindow(a2, w => /Каталог/.test(title(w)));
  await a2.clickWindow(slot, 0, 0); await sleep(600);
  check('already-owned hat is not sold again', !(await waitWindow(a2, w => /Подтверждение/.test(title(w)), 800)) && db('Tester1').tokens === after.tokens);
  a2.closeWindow(a2.currentWindow);

  // --- статистика коллекции в меню
  a2.chat('/hats collection'); const colw = await waitWindow(a2, w => /Мои шляпки/.test(title(w)));
  const colNames = colw ? colw.slots.slice(0, 45).filter(Boolean).length : 0;
  check('collection shows owned hats', colNames === db('Tester1').hats.length, colNames);
  await sleep(400); await a2.clickWindow(48, 0, 0);
  let allCount = 0;
  for (let i = 0; i < 20 && allCount !== 45; i++) { await sleep(150); allCount = a2.currentWindow.slots.slice(0, 45).filter(Boolean).length; }
  check('collection "all" mode shows locked hats (paged)', allCount === 45, allCount);
  a2.closeWindow(a2.currentWindow);
  // финальное состояние для проверки после перезапуска: надеть шляпу
  a2.chat('/hats equip ' + hat); await sleep(600);
  console.log('STATE', JSON.stringify(db('Tester1')));
  const failed = results.filter(r => !r.ok).length;
  console.log(`SUMMARY ${results.length - failed}/${results.length} passed`);
  a2.quit(); b.quit();
  await sleep(500);
  process.exit(0);
})().catch(e => { console.error('CRASH', e); process.exit(1); });
