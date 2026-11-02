# DroidPad — Protocolo Wi-Fi v1 (MVP)

Objetivo: celular-controle envia estado do gamepad a 60Hz via UDP. Receptor (PC Windows) escuta e injeta/visualiza.

> Nota: o app se chama **DroidPad**, mas por compatibilidade os identificadores do protocolo continuam `phonepad` (`PHONEPAD_DISCOVER`, campo `"phonepad":1`).


## Rede
- Transporte: UDP, porta `9999` (dados). Discovery: UDP porta `9998`.
- Sender pode usar IP manual ou discovery (broadcast). Mesma rede Wi-Fi. Latência típica 5-20ms.

## Discovery (v2)
- Sender envia ASCII `PHONEPAD_DISCOVER` em broadcast `255.255.255.255:9998` (3x).
- Receptor (PC) responde em unicast:
```json
{"phonepad":1,"name":"DESKTOP-X","port":9999,"type":"pc"}
```
- `type`: `pc` ou `android`. Sender lista por IP e usa `port` p/ enviar dados.

## Pacote (JSON, 1 linha por datagrama)
```json
{"v":1,"t":123456,"lx":0.0,"ly":-1.0,"rx":0.5,"ry":0.0,"lt":0.0,"rt":1.0,"btn":0}
```
- `v`: versão protocolo (1)
- `t`: `System.currentTimeMillis()` do sender
- `lx,ly,rx,ry`: float `-1.0..1.0` (stick, Y+ = cima)
- `lt,rt`: float `0.0..1.0` (gatilhos)
- `btn`: bitmask int:
  - 0=A, 1=B, 2=X, 3=Y, 4=LB, 5=RB, 6=BACK, 7=START, 8=L3, 9=R3, 10=UP, 11=DOWN, 12=LEFT, 13=RIGHT

Exemplo: só botão A = `1<<0 = 1`. A+B = `1|2 = 3`.

## Taxa
- Sender: 60Hz fixo (a cada ~16ms) + envio imediato ao mudar botão.
- Receiver: timeout 500ms sem pacote = zera sticks (evita "travado").

## Próximos passos (fora do MVP)
- Binário compacto (struct 16 bytes) pra reduzir lag.
- TCP/WebSocket fallback se UDP bloqueado.
