# Teste rápido sem celular (valida receptor PC)
# Terminal 1: python main.py --print-only
# Terminal 2: python send_test.py --host 127.0.0.1
import argparse, json, socket, time, math
ap = argparse.ArgumentParser()
ap.add_argument("--host", default="127.0.0.1")
ap.add_argument("--port", type=int, default=9999)
a = ap.parse_args()
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
t0 = time.time()
print(f"Enviando p/ {a.host}:{a.port} (Ctrl+C p/ parar)")
i = 0
while True:
    lx = math.sin(time.time() - t0)
    pkt = {"v":1,"t":int(time.time()*1000),"lx":lx,"ly":0.0,"rx":0.0,"ry":0.0,"lt":0.0,"rt":0.0,"btn":(1 if i % 60 < 30 else 0)}
    s.sendto(json.dumps(pkt).encode(), (a.host, a.port))
    i += 1
    time.sleep(1/60)
