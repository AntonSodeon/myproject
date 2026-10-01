# Проверка на живом сервере

Сценарии подключают ботов (mineflayer, протокол 1.21.11) к локальному Paper 1.21.11 и проверяют плагин так, как его видит игрок.

Ожидаемая раскладка (пути относительно этой папки): `../server/` — сервер с `paper.jar`, `start.sh`
(запуск с консолью через FIFO `in`), `server.properties` (`online-mode=false`, `server-port=25599`, плоский мир).
`fullrun.sh` очищает данные плагина, запускает `scenario1.js`, перезапускает сервер и запускает `scenario2.js`.
`scenario3.js` — ошибка загрузки ресурс-пака. Для сценариев с паком нужен HTTP-сервер с `pack.zip` на `127.0.0.1:8765`.

```
npm install
./fullrun.sh && cat run1.txt run2.txt
node scenario3.js
```
