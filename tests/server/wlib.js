const fs = require('fs'); const path = require('path');
const { SERVER, consoleCmd, sleep } = require('./lib');
const { execFileSync } = require('child_process');
const logFile = path.join(SERVER, 'server.log');
const strip = s => s.replace(/\u001b\[[0-9;]*m/g, '');
const logLen = () => fs.readFileSync(logFile, 'utf8').length;
const logSince = n => strip(fs.readFileSync(logFile, 'utf8').slice(n));
async function cmd(c, wait = 600) { const l = logLen(); consoleCmd(c); await sleep(wait); return logSince(l); }
async function count(selector) {
  const out = await cmd(`execute if entity ${selector}`, 500);
  const m = out.match(/Test passed[.,] [Cc]ount: (\d+)/); if (m) return +m[1]; if (/Test failed/.test(out)) return 0; return -1;
}
/** Все ItemDisplay анимации: позиция, CMD модели, масштаб. */
async function displays(extra = '') {
  const out = await cmd(`execute as @e[type=item_display,tag=qwhatcase_anim${extra}] run data get entity @s`, 900);
  return out.split('\n').filter(l => l.includes('has the following entity data')).map(l => {
    const pos = l.match(/Pos: \[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/);
    const cmd = l.match(/custom_model_data": \{floats: \[([\d.]+)f\]/);
    const scale = l.match(/scale: \[([\d.E-]+)f/);
    const op = l.match(/"qwhatcase:operation": "([^"]+)"/);
    const point = l.match(/"qwhatcase:point": "([^"]+)"/);
    return { x: pos && +pos[1], y: pos && +pos[2], z: pos && +pos[3], cmd: cmd && Math.round(+cmd[1]), scale: scale && +scale[1],
      op: op && op[1], point: point && point[1] };
  });
}
async function status() {
  const out = await cmd('hatcases status', 500);
  const m = out.match(/Анимаций в мире: (\d+), занятых точек: (\d+), надписей: (\d+)\/(\d+), временных сущностей: (\d+), задач плагина: (\d+)/);
  return m ? { animations: +m[1], locks: +m[2], labels: +m[3], points: +m[4], entities: +m[5], tasks: +m[6] } : null;
}
const sql = (q) => execFileSync('python3', ['-c', `import sqlite3,json;c=sqlite3.connect('${SERVER}/plugins/QWHatCase/data.db');print(json.dumps(c.execute(${JSON.stringify(q)}).fetchall()))`]).toString();
const rows = q => JSON.parse(sql(q));
const hatCmd = id => +execFileSync('python3', ['-c', `import yaml;print(yaml.safe_load(open('${SERVER}/plugins/QWHatCase/hats.yml'))['hats']['${id}']['custom-model-data'])`]).toString();
module.exports = { cmd, count, displays, status, rows, hatCmd, logLen, logSince, strip };
