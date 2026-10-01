import sqlite3, sys, json
con = sqlite3.connect('../server/plugins/QWHatCase/data.db')
u = con.execute("select uuid, selected_hat from players where name_lower=?", (sys.argv[1].lower(),)).fetchone()
if not u: print('null'); sys.exit()
hats = [r[0] for r in con.execute("select hat_id from collection where uuid=?", (u[0],))]
ench = {}
for h, e, l in con.execute("select hat_id, enchant, level from hat_enchants where uuid=?", (u[0],)):
    ench.setdefault(h, {})[e] = l
print(json.dumps({"selected": u[1], "hats": hats, "enchants": ench}))
