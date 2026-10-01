import sqlite3, sys, json
con = sqlite3.connect(sys.argv[1] if len(sys.argv) > 1 else '../server/plugins/QWHatCase/data.db')
name = sys.argv[2]
row = con.execute("select uuid, tokens, selected_hat from players where name_lower=?", (name.lower(),)).fetchone()
if not row:
    print(json.dumps(None)); sys.exit()
uuid = row[0]
keys = dict(con.execute("select key_type, amount from keys where uuid=?", (uuid,)).fetchall())
hats = [r[0] for r in con.execute("select hat_id from collection where uuid=? order by obtained_at", (uuid,))]
ops = con.execute("select count(*), sum(keys_spent), sum(tokens_awarded), sum(case when outcome='DUPLICATE' then 1 else 0 end), sum(case when shown=0 then 1 else 0 end) from openings where uuid=?", (uuid,)).fetchone()
print(json.dumps({"uuid": uuid, "tokens": row[1], "selected": row[2], "keys": keys, "hats": hats,
                  "openings": ops[0], "keysSpent": ops[1] or 0, "tokensAwarded": ops[2] or 0, "dups": ops[3] or 0, "unshown": ops[4] or 0}))
