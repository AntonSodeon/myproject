#!/bin/bash
cd "$(dirname "$0")/../server"
stop() { echo stop > in; while pgrep -x java -a | grep -q paper.jar; do sleep 1; done; }
start() { : > server.log; (setsid ./start.sh >/dev/null 2>&1 &); sleep 3; until grep -q "Done (" server.log; do sleep 1; done; }
stop; rm -rf plugins/QWHatCase world world_nether world_the_end; start
cd ../bot; timeout 200 node scenario1.js 2>&1 | grep -E "PASS|FAIL|SUMMARY|CRASH" | cut -c1-170 > run1.txt
cd ../server; stop; start
cd ../bot; timeout 240 node scenario2.js 2>&1 | grep -E "PASS|FAIL|SUMMARY|CRASH" | cut -c1-170 > run2.txt
echo DONE
