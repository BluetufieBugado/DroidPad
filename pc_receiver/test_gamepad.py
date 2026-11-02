"""Teste isolado do driver — sem rede, sem celular.
Roda 10s mexendo o analógico e segurando A. Deixe o
https://gamepad-tester.com aberto e veja se reage.
Uso: python test_gamepad.py
"""
import time
import vgamepad as vg

gp = vg.VX360Gamepad()
print("[ok] controle virtual criado — mexa no site agora (10s)")
t0 = time.time()
i = 0
while time.time() - t0 < 10:
    import math
    x = math.sin(time.time() * 3)
    gp.left_joystick_float(x_value_float=x, y_value_float=0.0)
    if i % 20 < 10:
        gp.press_button(button=vg.XUSB_BUTTON.XUSB_GAMEPAD_A)
    else:
        gp.release_button(button=vg.XUSB_BUTTON.XUSB_GAMEPAD_A)
    gp.update()
    i += 1
    time.sleep(1/60)
gp.reset()
gp.update()
print("fim — se o site reagiu, driver+vgamepad OK e o problema é rede/pacote.")
