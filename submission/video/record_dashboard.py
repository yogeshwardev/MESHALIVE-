"""Records the control-room dashboard in demo mode: select SOS, dispatch a team, broadcast an evacuation alert."""
import sys
import time
from pathlib import Path

from playwright.sync_api import sync_playwright

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
URL = "http://localhost:8787/dashboard/?demo=1"

with sync_playwright() as p:
    b = p.chromium.launch(channel="chrome", headless=True)
    ctx = b.new_context(viewport={"width": 1600, "height": 1000}, device_scale_factor=1,
                        record_video_dir=str(OUT), record_video_size={"width": 1600, "height": 1000})
    page = ctx.new_page()
    page.on("dialog", lambda d: d.accept("Boat 7 on the way. Stay on the roof and wave a cloth."))
    t0 = time.time()
    page.goto(URL)
    page.wait_for_selector(".card")
    page.wait_for_timeout(7000)                       # map settles, overview
    page.locator(".card").first.click()               # most urgent SOS: map flies to it
    page.wait_for_timeout(5000)
    page.locator('.card button[data-act="dispatch"]').first.click()
    page.wait_for_timeout(6500)                       # "sending through the mesh" -> "victim's phone confirmed"
    page.select_option("#aLevel", "EVACUATE")
    page.locator("#aTitle").click()
    page.keyboard.type("Evacuate to Govt School, Velachery", delay=45)
    page.locator("#aText").click()
    page.keyboard.insert_text("நீர் மட்டம் உயர்கிறது. உடனே பள்ளிக்குச் செல்லவும். Water is rising, move to the school now.")
    page.wait_for_timeout(1500)
    page.locator("#aSend").click()
    page.wait_for_timeout(6000)
    print("recorded seconds:", round(time.time() - t0, 1))
    page.screenshot(path=str(OUT / "end.png"))
    ctx.close()
    b.close()
for f in OUT.glob("*.webm"):
    f.rename(OUT / "dashboard.webm")
print("ok")
