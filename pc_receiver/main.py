"""
DroidPad PC Receiver — Windows
Escuta UDP 9999, atualiza controle virtual Xbox360 via ViGEm (vgamepad).
Sem ViGEm instalado: roda em --print-only para validar rede.

Uso:
  pip install -r requirements.txt   # precisa do ViGEmBus instalado no Windows
  python main.py                    # modo gamepad
  python main.py --print-only       # só mostra pacotes (sem driver)
  python main.py --port 9999
"""
import argparse
import json
import socket
import time

PORT_DEFAULT = 9999
DISCOVERY_PORT = 9998
DISCOVERY_MAGIC = b"PHONEPAD_DISCOVER"
TIMEOUT_ZERO = 0.5  # s sem pacote -> zera eixos

# Ordem btn: 0=A,1=B,2=X,3=Y,4=LB,5=RB,6=BACK,7=START,8=L3,9=R3,10=UP,...
BTN_NAMES = ["A","B","X","Y","LB","RB","BACK","START","L3","R3","UP","DOWN","LEFT","RIGHT"]

def clamp(v, lo, hi):
    return max(lo, min(hi, v))

def apply_gamepad(gp, data):
    """data: dict do protocolo v1 -> atualiza vgamepad VX360Gamepad"""
    lx = clamp(float(data.get("lx", 0)), -1, 1)
    ly = clamp(float(data.get("ly", 0)), -1, 1)
    rx = clamp(float(data.get("rx", 0)), -1, 1)
    ry = clamp(float(data.get("ry", 0)), -1, 1)
    lt = clamp(float(data.get("lt", 0)), 0, 1)
    rt = clamp(float(data.get("rt", 0)), 0, 1)
    btn = int(data.get("btn", 0))

    # Protocolo e XInput usam Y+ = cima, sem inversão.
    gp.left_joystick_float(x_value_float=lx, y_value_float=ly)
    gp.right_joystick_float(x_value_float=rx, y_value_float=ry)
    gp.left_trigger_float(value_float=lt)
    gp.right_trigger_float(value_float=rt)

    import vgamepad as vg
    mapping = [
        (0, vg.XUSB_BUTTON.XUSB_GAMEPAD_A),
        (1, vg.XUSB_BUTTON.XUSB_GAMEPAD_B),
        (2, vg.XUSB_BUTTON.XUSB_GAMEPAD_X),
        (3, vg.XUSB_BUTTON.XUSB_GAMEPAD_Y),
        (4, vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER),
        (5, vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER),
        (6, vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK),
        (7, vg.XUSB_BUTTON.XUSB_GAMEPAD_START),
        (8, vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB),
        (9, vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB),
    ]
    for bit, xbtn in mapping:
        if btn & (1 << bit):
            gp.press_button(button=xbtn)
        else:
            gp.release_button(button=xbtn)

    # D-pad é botão separado no XUSB
    gp.press_button(button=vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP) if btn & (1 << 10) else gp.release_button(button=vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP)
    gp.press_button(button=vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN) if btn & (1 << 11) else gp.release_button(button=vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN)
    gp.press_button(button=vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT) if btn & (1 << 12) else gp.release_button(button=vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT)
    gp.press_button(button=vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT) if btn & (1 << 13) else gp.release_button(button=vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT)

    gp.update()

def discovery_loop(name: str, port: int):
    """Responde broadcast PHONEPAD_DISCOVER na porta 9998 com JSON do receptor."""
    import threading
    def run():
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.bind(("0.0.0.0", DISCOVERY_PORT))
        except Exception as e:
            print(f"[aviso] discovery indisponível ({e})")
            return
        print(f"[ok] Discovery na porta {DISCOVERY_PORT} como '{name}'.")
        while True:
            try:
                raw, addr = s.recvfrom(256)
                if raw.strip() == DISCOVERY_MAGIC:
                    resp = json.dumps({"phonepad": 1, "name": name, "port": port, "type": "pc"}).encode()
                    s.sendto(resp, addr)
            except Exception:
                continue
    threading.Thread(target=run, daemon=True).start()

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=PORT_DEFAULT)
    ap.add_argument("--print-only", action="store_true", help="não usa ViGEm, só imprime")
    ap.add_argument("--name", default=socket.gethostname(), help="nome anunciado no discovery")
    ap.add_argument("--no-discovery", action="store_true", help="desliga responder discovery")
    args = ap.parse_args()

    if not args.no_discovery:
        discovery_loop(args.name, args.port)

    gp = None
    if not args.print_only:
        try:
            import vgamepad as vg
            gp = vg.VX360Gamepad()
            print("[ok] Controle virtual Xbox360 criado (ViGEm).")
            print("     Se der erro de driver, instale ViGEmBus e rode com --print-only.")
        except Exception as e:
            print(f"[aviso] vgamepad indisponível ({e}). Caindo para --print-only.")
            gp = None

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("0.0.0.0", args.port))
    sock.settimeout(0.2)
    print(f"[ok] Escutando UDP 0.0.0.0:{args.port} — conecte o celular nessa rede e aponte p/ o IP deste PC.")
    last = time.time()
    last_print = 0
    prev_pkt = 0.0
    while True:
        try:
            raw, addr = sock.recvfrom(2048)
            now_rx = time.time()
            dt_ms = (now_rx - prev_pkt) * 1000 if prev_pkt else 0
            prev_pkt = now_rx
            last = now_rx
            try:
                data = json.loads(raw.decode("utf-8").strip())
            except Exception:
                continue
            if gp is not None:
                apply_gamepad(gp, data)
            # log leve 5Hz (com idade do pacote p/ diagnosticar lag de rede)
            now = time.time()
            if now - last_print > 0.2:
                last_print = now
                pressed = [n for i, n in enumerate(BTN_NAMES) if int(data.get("btn", 0)) & (1 << i)]
                age_ms = now * 1000 - float(data.get("t", now * 1000))
                print(f"{addr[0]} lx={data.get('lx',0):+.2f} ly={data.get('ly',0):+.2f} "
                      f"rx={data.get('rx',0):+.2f} ry={data.get('ry',0):+.2f} btn={pressed} "
                      f"dt={dt_ms:.0f}ms age={age_ms:.0f}ms")
        except socket.timeout:
            if gp is not None and time.time() - last > TIMEOUT_ZERO:
                gp.reset()
                gp.update()
                last = time.time()
            continue
        except KeyboardInterrupt:
            print("\nSaindo.")
            break

if __name__ == "__main__":
    main()
