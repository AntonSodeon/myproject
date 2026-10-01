const { join, sleep, consoleCmd, waitWindow, title, itemName } = require('./lib');
(async () => {
  const admin = await join('Tester2'); consoleCmd('op Tester2');
  const a = await join('Tester1'); await sleep(800);
  admin.chat('/hat grant Tester1 hat_90'); await sleep(500);
  admin.chat('/hat grant Tester1 hat_200'); await sleep(800);
  console.log('admin chat', admin.chatLog.slice(-2));
  const pick = async (num) => {
    a.chat('/hat'); const w = await waitWindow(a, w => /Мои шляпы/.test(title(w)));
    const slot = w.slots.findIndex(s => s && itemName(s) && itemName(s).includes('#' + num));
    await a.clickWindow(slot, 0, 0); await sleep(600);
    const act = await waitWindow(a, w => /Шляпа/.test(title(w)));
    await a.clickWindow(15, 0, 0); await sleep(800);
  };
  await pick(90);
  console.log('head after equip 90:', itemName(a.inventory.slots[5]));
  consoleCmd('item modify entity Tester1 armor.head {"function":"minecraft:set_enchantments","enchantments":{"minecraft:thorns":3},"add":true}');
  await sleep(800);
  await pick(200);
  console.log('head after equip 200:', itemName(a.inventory.slots[5]));
  if (a.currentWindow) a.closeWindow(a.currentWindow);
  consoleCmd('give Tester1 carved_pumpkin[custom_data={PublicBukkitValues:{"ptrap:hat-id":"hat_150"}},custom_model_data={floats:[150f]},enchantments={sharpness:5},custom_name="Шляпа #150"]');
  await sleep(1000);
  console.log('inv:', a.inventory.items().map(i => i.name + ':' + itemName(i)));
  a.quit(); admin.quit(); await sleep(1500); process.exit(0);
})().catch(e => { console.error('CRASH', e); process.exit(1); });
