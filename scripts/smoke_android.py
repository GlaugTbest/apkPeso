"""Exercise the signed release on an API 35 emulator using only ADB and the app UI."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

PACKAGE = "br.com.glaugtbest.gtheday"
ACTIVITY = PACKAGE + "/.MainActivity"
EVIDENCE = Path("evidence")
ADB_TIMEOUT = 30


def adb(*args, timeout=ADB_TIMEOUT):
    return subprocess.check_output(["adb", *args], stderr=subprocess.STDOUT, timeout=timeout).decode(
        "utf-8", "replace"
    ).strip()


def shell(*args):
    return adb("shell", *map(str, args))


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def wait_for(predicate, timeout, description):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        result = predicate()
        if result:
            return result
        time.sleep(0.45)
    raise AssertionError(description)


def dump():
    shell("uiautomator", "dump", "/sdcard/gtheday-window.xml")
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/gtheday-window.xml"))


def find(value, root=None):
    root = dump() if root is None else root
    for node in root.iter("node"):
        if value in (node.get("text"), node.get("content-desc")):
            return node
    return None


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def visible(value, timeout=15):
    return wait_for(lambda: [n] if (n := find(value)) is not None else None,
                    timeout, f"Elemento ausente: {value}")[0]


def tap(value, timeout=15):
    shell("input", "tap", *center(visible(value, timeout)))
    time.sleep(0.25)


def screenshot(name):
    with (EVIDENCE / (name + ".png")).open("wb") as output:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=output, check=True, timeout=20)


def step(message):
    print(message, flush=True)


def set_clock(iso_date):
    value = datetime.fromisoformat(iso_date).replace(tzinfo=timezone.utc)
    shell("date", "-u", value.strftime("%m%d%H%M%Y.%S"))
    current = int(shell("date", "+%s"))
    check(abs(current - value.timestamp()) < 6, f"Relógio do emulador não ajustado: {current}")
    time.sleep(1)


def swipe(x1, y1, x2, y2):
    shell("input", "swipe", x1, y1, x2, y2, 370)
    time.sleep(0.35)


def scroll_down():
    swipe(WIDTH // 2, HEIGHT * 78 // 100, WIDTH // 2, HEIGHT * 37 // 100)


def off_recents():
    shell("input", "keyevent", 187)
    time.sleep(1)
    swipe(WIDTH // 2, HEIGHT * 65 // 100, WIDTH // 2, HEIGHT * 13 // 100)
    time.sleep(0.7)
    recent = shell("dumpsys", "activity", "recents")
    check(not any("Recent #" in line and PACKAGE in line for line in recent.splitlines()),
          "O GtheDay ainda aparece em Recentes")
    shell("input", "keyevent", "KEYCODE_HOME")


def app_open():
    shell("am", "start", "-n", ACTIVITY)


def fields():
    return [node for node in dump().iter("node") if node.get("class") == "android.widget.EditText"]


def type_text(value):
    shell("input", "text", value.replace(" ", "%s"))


def enter_meal(title, phrase, screenshot_name=None):
    visible(title)
    visible("O que você comeu?")
    focused = [n for n in fields() if n.get("focused") == "true"]
    check(bool(focused), f"O editor de {title} não abriu com foco")
    type_text(phrase)
    if screenshot_name:
        check("mInputShown=true" in shell("dumpsys", "input_method") or
              "isInputViewShown=true" in shell("dumpsys", "input_method"),
              "Teclado indisponível no editor")
        screenshot(screenshot_name)
    tap("Salvar")


def active_reminder(title):
    data = shell("dumpsys", "notification", "--noredact")
    data = data.split("Notification List:", 1)[-1].split("Enqueued Notification List:", 1)[0]
    return [
        block for block in re.split(r"(?=NotificationRecord\()", data)
        if f"pkg={PACKAGE}" in block.partition("\n")[0] and
        (f"({title})" in block or f"android.title={title}" in block)
    ]


def expect_notice(title, seconds=45):
    active = wait_for(lambda: active_reminder(title), seconds, f"Aviso não recebido: {title}")
    check(len(active) == 1, f"Aviso duplicado: {title}")


def shade():
    shell("cmd", "statusbar", "expand-notifications")
    time.sleep(0.8)


def collapse_shade():
    shell("cmd", "statusbar", "collapse")
    time.sleep(0.4)


def alarms():
    report = shell("dumpsys", "alarm").split("Removal history:", 1)[0]
    return [line for line in report.splitlines()
            if line.strip().startswith("tag=") and PACKAGE + ".FIRE" in line]


def grant_permissions():
    shell("pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
    shell("appops", "set", PACKAGE, "SCHEDULE_EXACT_ALARM", "allow")


def reboot():
    adb("reboot")
    adb("wait-for-device", timeout=150)
    wait_for(lambda: shell("getprop", "sys.boot_completed") == "1", 130, "Android não reiniciou")
    shell("input", "keyevent", "KEYCODE_WAKEUP")
    shell("wm", "dismiss-keyguard")


def verify_build(apk):
    adb("root")
    adb("wait-for-device", timeout=45)
    shell("settings", "put", "global", "auto_time", "0")
    shell("settings", "put", "global", "auto_time_zone", "0")
    shell("cmd", "alarm", "set-timezone", "Etc/UTC")
    shell("settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
    set_clock("2026-11-12T08:00:00")
    check("Success" in adb("install", str(apk), timeout=120), "Falha ao instalar APK")
    step("Android " + shell("getprop", "ro.build.version.release") +
         " · API " + shell("getprop", "ro.build.version.sdk") +
         " · " + shell("wm", "size") + " · " + shell("wm", "density"))
    app_open()
    visible("Bebi água")

    tap("Configurações")
    visible("Notificações bloqueadas")
    tap("Permitir notificações")
    denied = wait_for(lambda: [n for n in dump().iter("node")
                              if n.get("resource-id", "").endswith(":id/permission_deny_button")],
                      20, "Permissão de notificações não apareceu")
    shell("input", "tap", *center(denied[0]))
    visible("Notificações bloqueadas")
    check(not alarms(), "Foram agendados avisos com permissão negada")
    screenshot("01-permissao-negada")
    shell("input", "keyevent", "KEYCODE_HOME")
    grant_permissions()
    app_open()
    visible("Notificações permitidas")
    screenshot("02-configuracoes")
    step("OK · instalação e permissão negada/permitida")

    tap("Hoje")
    tap("Bebi água")
    tap("Desfazer")
    tap("Bebi água")
    tap("Tomei")
    tap("Treinei")
    tap("20 min")
    visible("20 min registrados")
    tap("30 min")
    visible("30 min registrados")
    tap("Outro")
    field = wait_for(fields, 15, "Campo de duração não apareceu")[0]
    shell("input", "tap", *center(field))
    shell("input", "keyevent", "KEYCODE_MOVE_END")
    shell("input", "keyevent", "KEYCODE_DEL", "KEYCODE_DEL")
    type_text("37")
    tap("Salvar")
    visible("37 min registrados")
    screenshot("03-hoje")
    for title, phrase in [("Café da manhã", "Cafe e cuscuz"),
                          ("Almoço", "Arroz, bife e feijao"),
                          ("Lanche", "Banana")]:
        if find(title) is None:
            scroll_down()
        tap(title)
        enter_meal(title, phrase, "04-editor" if title == "Almoço" else None)
    tap("Semana")
    tap("Registrar peso e cintura")
    weight, waist = wait_for(fields, 15, "Campos de medidas não apareceram")
    shell("input", "tap", *center(weight))
    type_text("80,5")
    shell("input", "tap", *center(waist))
    type_text("85,2")
    tap("Salvar")
    visible("80,5 kg")
    visible("85,2 cm")
    visible("1 treino")
    visible("1 · 37 min")
    screenshot("05-semana")
    off_recents()
    app_open()
    visible("37 min registrados")
    step("OK · água, desfazer, creatina, treino, cardio, refeições, medidas e persistência")

    set_clock("2026-11-12T12:29:15")
    off_recents()
    time.sleep(50)
    check(not active_reminder("Almoço"), "Almoço já registrado gerou aviso")
    step("OK · almoço concluído cancelou o aviso")

    set_clock("2026-11-12T19:57:45")
    app_open()
    tap("Configurações")
    scroll_down()
    tap("Lembrete de Jantar")
    time.sleep(1)
    disabled_count = len(alarms())
    tap("Lembrete de Jantar")
    wait_for(lambda: len(alarms()) == disabled_count + 1, 10, "Jantar não reagendado uma única vez")
    off_recents()
    expect_notice("Jantar", seconds=145)
    shade()
    snooze = find("Lembrar em 30 min")
    if snooze is None:
        x, y = center(visible("Jantar"))
        swipe(x, y, x, y + HEIGHT // 7)
    tap("Lembrar em 30 min")
    collapse_shade()
    wait_for(lambda: not active_reminder("Jantar"), 8, "Jantar não sumiu após adiar")
    prefs = shell("cat", "/data/user/0/" + PACKAGE + "/shared_prefs/reminders.xml")
    target = re.search(r'name="snooze_at_8" value="(\d+)"', prefs)
    check(target is not None, "Adiamento não foi persistido")
    offset = int(target[1]) / 1000 - int(shell("date", "+%s"))
    check(1775 < offset <= 1801, f"Adiamento diferente de 30 minutos: {offset}")
    near_due = datetime.fromtimestamp(int(target[1]) / 1000 - 15, tz=timezone.utc)
    set_clock(near_due.strftime("%Y-%m-%dT%H:%M:%S"))
    expect_notice("Jantar", seconds=35)
    shade()
    screenshot("06-notificacao")
    tap("Jantar")
    enter_meal("Jantar", "Frango e arroz", "07-jantar-teclado")
    step("OK · aviso após retirar dos recentes, alternância sem duplicação, adiamento e atalho do jantar")

    set_clock("2026-11-13T09:55:30")
    app_open()
    visible("Bebi água")
    visible("0 de 4 concluídos")
    tap("Semana")
    tap("Quinta-feira, 12 de novembro")
    scroll_down()
    tap("Jantar")
    visible("Frango e arroz")
    shell("input", "keyevent", "KEYCODE_MOVE_END")
    type_text(" e salada")
    tap("Salvar")
    if find("Frango e arroz e salada") is None:
        scroll_down()
    visible("Frango e arroz e salada")
    off_recents()
    reboot()
    wait_for(lambda: len(alarms()) > 0, 35, "Nenhum lembrete reagendado após reiniciar")
    expect_notice("Água", seconds=295)
    screenshot("08-apos-reiniciar")
    step("OK · virada de dia, edição de ontem e aviso após reiniciar sem abrir o app")

    shell("am", "force-stop", PACKAGE)
    wait_for(lambda: not alarms(), 10, "Alarmes permaneceram após Forçar parada")
    app_open()
    wait_for(lambda: len(alarms()) > 0, 15, "Alarmes não voltaram ao reabrir o app")
    step("OK · Forçar parada remove agendamentos no emulador, reabertura restaura")


if __name__ == "__main__":
    EVIDENCE.mkdir(exist_ok=True)
    WIDTH, HEIGHT = map(int, re.findall(r"(\d+)x(\d+)", shell("wm", "size"))[-1])
    try:
        verify_build(Path(sys.argv[1]))
    except Exception:
        try:
            screenshot("falha")
            (EVIDENCE / "falha.xml").write_text(ET.tostring(dump(), encoding="unicode"), encoding="utf-8")
        except Exception as diagnostic:
            print(diagnostic, file=sys.stderr)
        raise
