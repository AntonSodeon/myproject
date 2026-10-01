const mineflayer = require('mineflayer');
const fs = require('fs');
const path = require('path');
const SERVER = path.resolve(__dirname, '../server');
const sleep = ms => new Promise(r => setTimeout(r, ms));
function consoleCmd(cmd) { fs.appendFileSync(path.join(SERVER, 'in'), cmd + '\n'); }
const results = [];
function check(name, ok, info) {
  results.push({ name, ok: !!ok, info });
  console.log((ok ? 'PASS ' : 'FAIL ') + name + (info !== undefined ? ' :: ' + JSON.stringify(info) : ''));
}
function strip(s) { return s.replace(/§./g, ''); }
async function join(name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25599, username: name, version: '1.21.11', auth: 'offline' });
  bot.chatLog = [];
  bot.on('messagestr', m => { bot.chatLog.push(m); });
  bot.on('kicked', r => console.log(name, 'kicked', JSON.stringify(r)));
  bot.on('error', e => console.log(name, 'error', e.message));
  await new Promise((res, rej) => { bot.once('spawn', res); bot.once('end', () => rej(new Error('ended ' + name))); });
  await sleep(1500);
  return bot;
}
async function waitWindow(bot, pred = () => true, timeout = 5000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    if (bot.currentWindow && pred(bot.currentWindow)) return bot.currentWindow;
    await sleep(50);
  }
  return null;
}
function title(win) {
  try { const t = typeof win.title === 'string' ? JSON.parse(win.title) : win.title; return flat(t); } catch (e) { return String(win.title); }
}
function flat(c) {
  if (c == null) return '';
  if (typeof c === 'string') return c;
  if (c.value !== undefined && c.type) return flat(c.value);
  let s = (c.text ?? c[''] ?? '');
  if (typeof s === 'object') s = flat(s);
  const extra = c.extra?.value?.value ?? c.extra;
  if (Array.isArray(extra)) s += extra.map(flat).join('');
  return s;
}
function itemName(it) {
  if (!it) return null;
  try { return it.customName ? strip(flat(typeof it.customName === 'string' ? JSON.parse(it.customName) : it.customName)) : it.name; } catch (e) { return it.name; }
}
function chatHas(bot, re, since = 0) { return bot.chatLog.slice(since).some(m => re.test(m)); }
function inventoryItems(bot) { return bot.inventory.slots.map((s, i) => s ? { i, name: s.name, n: itemName(s) } : null).filter(Boolean); }
module.exports = { join, sleep, consoleCmd, check, results, waitWindow, title, itemName, chatHas, inventoryItems, SERVER, strip };
