"""Builds the SevaFirst 'Submit Your Idea' answers as a Word document (and checks every word limit)."""
import re
import sys
from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt, RGBColor, Cm

HERE = Path(__file__).parent
OUT = HERE / "MeshAlive_Portal_Answers.docx"

NAVY = RGBColor(0x0B, 0x1F, 0x3A)
RED = RGBColor(0xE5, 0x39, 0x35)
MUTED = RGBColor(0x55, 0x5F, 0x6D)

IDENTITY = [
    ("Title of the solution",
     "MeshAlive – an offline disaster-response network that gets SOS messages to rescuers when mobile towers fail"),
    ("Theme", "05 · Surakshit Bharat (Security & resilience · Digital public infrastructure)"),
    ("Problem Statement",
     "When cyclones and floods take down mobile towers and power, people in danger cannot call for help, and district "
     "control rooms cannot see who needs rescue, where they are, or how urgent it is."),
    ("Track", "Track A – Product or Technology"),
]

# (field, guidance from the form, word limit, answer)
ANSWERS = [
    ("What problem are you solving?", "Describe the problem clearly – what it is, who faces it, and how often.", 500, """
Every cyclone and flood season, coastal and low-lying districts of Tamil Nadu and the rest of India lose mobile network coverage for hours or days. Towers lose power, backhaul links are cut and the network that remains is overloaded. This happened in the 2015 Chennai floods and again during Cyclone Michaung in 2023.

The people hit hardest are exactly those who most need to call for help: families trapped on rooftops, elderly people who cannot walk to a relief centre, patients who need medicines, and households cut off by rising water. Without a working network they cannot make a call, send an SMS or share their location.

At the same time the district control room works blind. It does not know who needs help, where they are, how many people are with them or how urgent each case is. Rescue boats and NDRF/SDRF teams search by sight and word of mouth, so the most critical cases are not always reached first.

Existing offline messaging apps are chat tools. They do not deliver an SOS to rescuers, they do not prioritise a trapped family over a chat message, they drain the batteries of the phones that relay messages for others (when power may be out for days), and they cannot carry the control room's reply back to the victim.

This repeats every year across India's cyclone- and flood-prone coastline and river basins, and the first hours after the network fails are the most critical for rescue.
"""),
    ("What is your proposed solution?", "Describe your solution – what it is, how it works, and what stage it is at.", 1000, """
MeshAlive is an Android app plus a browser-based district control-room dashboard. Together they keep emergency communication working when the mobile network has failed, using only the phones people already own.

1. One-tap SOS with a situation report. A person in danger presses SOS (with a 3-second countdown and Cancel to prevent false alarms). The message carries GPS location and a short situation report entered once in advance: number of people, trapped, injured, elderly, children, disabled, and water level (knee, waist, chest or roof). The phone turns this into an urgency score from 0 to 100, so a trapped family of four in chest-deep water is handled before a request for drinking water. The app works in Tamil and English.

2. Phone-to-phone relay with no towers. Nearby phones connect directly over Bluetooth and Wi-Fi (Google Nearby Connections) and pass the SOS hop by hop. Our routing engine uses a battery-aware shortest-path algorithm (Dijkstra) that avoids phones with low battery, so the network survives longer when power is out. Every relay sends the most urgent SOS first. Messages are encrypted on the phone.

3. Rescue carriers. If an SOS has no route, the victim's phone hands a copy to any nearby rescue boat, NDRF/SDRF team or ambulance phone switched to "rescue carrier" mode. The carrier physically carries the message out of the dead zone and delivers it as soon as it meets a phone with signal (store-carry-forward).

4. Automatic upload to the control room. The first phone that has internet, whether a volunteer gateway or any phone that regains signal, uploads every SOS it holds. The district dashboard shows each case live on a map, sorted by urgency, with people count, triage details and the exact route the message took.

5. Help goes back to the victim. From the dashboard an operator presses "Dispatch team" or broadcasts an evacuation alert in any language. The message travels back through the same offline mesh, and the victim's phone shows "Rescue team on the way" even without signal. The dashboard shows when the victim's phone has confirmed receipt. All control-room commands are digitally signed, so nobody can inject a fake evacuation order. People who are safe can tap "I'm safe", so the control room knows which households need no rescue.

6. Planning and evidence tools. An Impact screen records real field-trial results (deliveries, round-trip time, hops, phones met) and exports a report. A district planner uses the same routing engine to estimate what share of a population can reach the control room for a given area, number of app users, phones with signal and rescue boats, together with the cost.

Stage: a working prototype. The Android app, routing engine and control-room dashboard are built and running. They are covered by 49 automated tests and have been exercised in simulations of up to 1,000 phones. We are now ready for a field pilot with a district administration.
"""),
    ("Who is affected, and where does the problem occur?", "Name the people, places or communities who face this problem – and the scale of the issue.", 300, """
People affected:
• Families in flood- and cyclone-prone areas who lose mobile coverage during disasters, especially the elderly, children, people with disabilities and patients needing medicines.
• Fishing communities and residents of low-lying coastal settlements.
• Rescue teams (NDRF, SDRF, fire and rescue services, boat operators, ambulance crews) who must find victims without location information.
• District Emergency Operations Centres and District Disaster Management Authorities, which have to prioritise and dispatch with incomplete information.

Where:
• Coastal districts of Tamil Nadu (for example Chennai, Cuddalore, Nagapattinam and Thoothukudi) and the wider east and west coasts exposed to cyclones.
• River-flood regions such as the Brahmaputra and Ganga basins, Kerala and other districts that flood regularly.
• Urban areas where flooding cuts power to mobile towers.

Scale: the problem recurs every monsoon and cyclone season and can affect lakhs of people in a single event, across many districts at once. Any district that faces seasonal floods or cyclones can use the same solution.
"""),
    ("What is innovative or different about your solution?", "Explain how it differs from what already exists or from current ways of solving the problem.", 300, """
Existing offline apps such as Bridgefy and Briar are chat apps. LoRa radios such as Meshtastic and goTenna need a separate device for every person. MeshAlive is an end-to-end disaster-response system on ordinary phones:

• It reaches rescuers, not just other phones. Every SOS ends up on a live district dashboard, sorted by urgency.
• It carries help back. Dispatch messages and evacuation alerts travel back through the same offline mesh to phones with no signal, with a delivery receipt.
• It triages. A structured situation report becomes an urgency score that every relay uses to send the most critical SOS first.
• It protects the network. Battery-aware routing avoids nearly dead phones. In our simulation of 200 phones it used 147 times fewer radio transmissions than flooding and left the weakest phone with twice the battery of shortest-hop routing.
• Rescue carriers. Boats and NDRF teams physically carry SOS messages out of dead zones, which no consumer mesh app does.
• It is trustworthy. Control-room commands are digitally signed, so fake evacuation orders are rejected.
• It is inclusive. It works in Tamil and English with preset messages, and needs no account, SIM or extra hardware.
"""),
    ("How does your solution work?", "Walk through how it functions step by step – the process, technology, or model behind it.", 300, """
1. Setup (30 seconds): the user chooses Tamil or English, enters household details and a role: citizen, rescue carrier or control-room gateway.
2. Discovery: phones running MeshAlive find each other over Bluetooth and Wi-Fi and connect automatically, without towers or internet.
3. Network map: every few seconds each phone shares a small status message (battery level, neighbours, whether it has internet). Every phone builds a live map of the network and measures link quality.
4. Routing: the battery-aware Dijkstra algorithm computes the best next phone towards the nearest gateway, avoiding low-battery phones. A priority queue sends the most urgent SOS first.
5. No route? The SOS is stored and retried automatically. A copy is also handed to any rescue carrier in range, which delivers it once it reaches a gateway or signal.
6. Upload: a phone with internet uploads the SOS to the control-room dashboard and sends a delivery receipt back to the victim.
7. Response: the operator dispatches a team or broadcasts an alert. The signed command returns through the mesh, and the victim's phone shows "Rescue team on the way".

Technology: Android (Kotlin, Jetpack Compose), Google Nearby Connections, AES-GCM encryption, HMAC-signed commands, and a web dashboard with a live map.
"""),
    ("Additional notes on your stage", "Any additional context about where your solution currently stands.", 300, """
What is built and working:
• The Android app: SOS with a situation report, Tamil and English, guided first-run setup, phone-to-phone relay, rescue-carrier mode, auto-gateway, messages and chat, live network map with routing log, and a gateway inbox.
• A control-room dashboard: live map of SOS sorted by urgency, dispatch and evacuation broadcasts with digital signatures, "I'm safe" tracking, and delivery confirmation.
• A routing engine: flooding, BFS and battery-aware Dijkstra, with triage-based priority queues.
• Planning tools: a field-trial evidence recorder with report export, and a district coverage planner.
• 49 automated tests covering routing, forwarding, triage scoring, command signatures, coverage and evidence reports.

Tested so far: the app runs on Android phones and emulators. The algorithms have been tested in simulations of 50 to 1,000 phones, and the control-room dashboard has been tested with sample data.

Next step: a structured field pilot with 20–50 phones in one ward, measuring delivery rate and SOS-to-control-room time with the built-in evidence recorder.
"""),
    ("Who will benefit and what impact will it create?", "Describe the beneficiaries and the change your solution will bring (social, economic, environmental, or learning outcomes).", 300, """
Beneficiaries:
• People trapped or stranded during floods and cyclones, and their families. Their SOS reaches rescuers even when the network is down, and they learn that help is on the way.
• Vulnerable groups (elderly, children, people with disabilities, patients). The situation report puts their cases at the top of the queue.
• Rescue teams, who get exact locations, the number of people and the urgency, instead of searching blind.
• District control rooms, which get a live, prioritised picture of the whole area and a channel to reach people without signal.

Impact:
• Faster rescue in the critical first hours, by delivering location and urgency without towers.
• Lives saved by prioritising the most critical cases first.
• Better use of scarce boats and teams, because dispatch is guided by data, and "I'm safe" check-ins reduce wasted searches.
• Evacuation alerts reach people in dead zones.

Our district planner, which uses the app's own routing engine, estimates that in a 1 km² ward with 1,000 app users (about four people per phone), around 3,700 of 4,000 people could get an SOS to the control room. We will validate this in a field pilot.
"""),
    ("What is the estimated cost of your solution?", "Give a rough cost to build and/or run the solution. An estimate with your assumptions is fine.", 300, """
Cost to citizens: ₹0. The app is free and runs on phones people already own. It needs no SIM, mobile data, account or extra hardware to use the mesh.

Cost for rescue carriers: ₹0 in hardware. The phones that NDRF/SDRF crews, boat operators and ambulance drivers already carry are switched to carrier mode.

Cost for the control room: ₹0 in hardware. The dashboard runs in any web browser on an existing control-room computer.

Running costs (assumptions):
• Alert relay: the prototype uses a free public notification service. For a government deployment, a self-hosted server on existing state data-centre or cloud infrastructure has a small monthly hosting cost.
• App maintenance and updates by a small development team.
• Awareness and installation drives in each district, ideally run with the District Disaster Management Authority, schools and self-help groups before the monsoon season. This is the main rollout cost.

Who pays: the District or State Disaster Management Authority for rollout and awareness. Public disaster-preparedness funds can support district-wide adoption. Citizens pay nothing.
"""),
    ("How can the solution be implemented and scaled?", "Explain how it would be rolled out and how it could grow to reach more people or places.", 300, """
Phase 1 – Field pilot (3 months): one flood-prone ward with the District Disaster Management Authority. Install the app on volunteers' and residents' phones, equip local NDRF/SDRF boats and ambulances as rescue carriers, and run the dashboard in the district control room. Measure delivery rate and SOS-to-dispatch time with the built-in evidence recorder.

Phase 2 – District rollout: pre-monsoon awareness and installation drives through schools, colleges, panchayats, urban local bodies and self-help groups. Our coverage planner shows how many installs each ward needs before coverage rises sharply. That number becomes the adoption target.

Phase 3 – State and national scale: distribution through the State Disaster Management Authority and official app stores, and integration of the dashboard feed with state Emergency Operations Centres through the built-in webhook. Alerts can follow the national Common Alerting Protocol standard.

Why it scales: there is no hardware to buy, and the same app and dashboard work in any district. The mesh gets stronger as more people install it. Tamil and English are available now and more Indian languages can be added. The design is open, so it can become part of India's digital public infrastructure for disaster response.
"""),
    ("Describe your evidence", "Briefly describe what evidence you have gathered.", 300, """
Prototype: a working Android app (installable APK) and a working district control-room dashboard, with the source code publicly available on GitHub.

Testing:
• 49 automated tests covering routing algorithms, packet forwarding, duplicate suppression, TTL expiry, triage urgency scoring, the urgency priority queue, signed control-room commands, the coverage planner and field-evidence reports.
• Algorithm comparison on simulated networks of 200 phones. Battery-aware Dijkstra delivered 100% of SOS messages with 99 radio transmissions; flooding needed 14,533 transmissions, delivered 85% and drained the weakest phones to 0%. Dijkstra left the weakest phone with 6.3% battery, against 3.2% for shortest-hop routing.
• Scalability tests from 100 to 1,000 phones, where routing cost stays in milliseconds.
• On-device tests of SOS, store-and-forward delivery, gateway upload to the live channel, the Tamil interface and the dashboard workflow (dispatch, broadcast, delivery confirmation).

Field-trial readiness: the app includes an evidence recorder that logs every real delivery (success rate, round-trip time, hops) and exports a report for pilot review.
"""),
    ("Who could adopt or implement the solution?", "Name the organisations, institutions, government bodies, or partners who could take it forward.", 300, """
• District Disaster Management Authorities and District Emergency Operations Centres, as the primary users of the control-room dashboard.
• State Disaster Management Authorities, for example the Tamil Nadu State Disaster Management Authority, for state-wide rollout.
• The National Disaster Management Authority (NDMA), for national guidelines and integration with the Common Alerting Protocol based alert system.
• The National Disaster Response Force and State Disaster Response Forces, plus fire and rescue services, who would use rescue-carrier mode on their phones.
• Urban local bodies and panchayats, for ward-level awareness and installation drives.
• Ambulance services, fishing cooperatives and boat operators, as rescue carriers.
• NGOs and volunteer networks active in disaster relief, such as the Red Cross and civil defence volunteers.
• Telecom operators and state IT departments, for hosting and integration.
• Schools, colleges and NSS/NCC units, as installation and training volunteers.
"""),
    ("What support do you need to take the solution forward?", "State what you need – mentorship, funding, technical help, partnerships, access, or other support.", 300, """
• A pilot partnership with a district administration and its District Disaster Management Authority in a flood-prone district, including access to the district Emergency Operations Centre for a field trial.
• Collaboration with NDRF/SDRF and local rescue boat operators to field-test rescue-carrier mode.
• Mentorship in disaster-management operations, public-sector deployment and product scaling.
• Technical guidance on security review, integration with state alerting systems (Common Alerting Protocol) and self-hosting the alert relay on government infrastructure.
• Pilot funding for a self-hosted alert server, test devices for the pilot, and community awareness and training drives.
• Help with intellectual-property guidance and incubation support to sustain development.
• Access to Android test devices of different brands and price ranges, to make the app reliable across the phones people actually own.
"""),
]

STAGE = ("What stage is your solution at?", ["Idea", "Prototype", "Working Prototype", "Pilot", "Deployed"], "Working Prototype")
EVIDENCE = ("What evidence do you have?", ["Prototype", "Pilot", "Testing", "Survey", "Other"], {"Prototype", "Testing"})
ATTACH = [
    ("Concept document *", "MeshAlive_Concept_Document.pdf", "Required. PDF only, max 15 MB."),
    ("Other files / documents", "MeshAlive_Presentation.pdf (21 slides), MeshAlive_Presentation_6_Slides.pdf and MeshAlive_App_Screenshots.pdf",
     "Optional. PDF, Word, PowerPoint, Excel or images, up to 50 MB in total. (The APK cannot be uploaded here; it is linked on the website.)"),
    ("Video", "Upload MeshAlive_Prototype_Video.mp4 (17.5 MB, 2 min 43 s), or give the link https://meshalive.benzo.co.in/#demo", "Optional. Upload a video file (max 100 MB) or paste a link."),
    ("Website (mention in notes)", "https://meshalive.benzo.co.in", "Live demo of the control-room dashboard and app download."),
    ("Source code (mention in notes)", "https://github.com/yogeshwardev/MESHALIVE-", "Public repository."),
]


def words(t: str) -> int:
    return len(re.findall(r"\S+", t))


def shade(cell, hex_fill):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_fill)
    tcPr.append(shd)


def build():
    over = [(f, words(a), lim) for f, _, lim, a in ANSWERS if words(a) > lim]
    for f, n, lim in over:
        print(f"OVER LIMIT: {f}: {n}/{lim}")
    if over:
        sys.exit(1)

    doc = Document()
    sec = doc.sections[0]
    sec.left_margin = sec.right_margin = Cm(2.0)
    sec.top_margin = sec.bottom_margin = Cm(1.8)
    st = doc.styles["Normal"]
    st.font.name = "Calibri"
    st.font.size = Pt(11)
    st.element.rPr.rFonts.set(qn("w:eastAsia"), "Calibri")

    p = doc.add_paragraph()
    r = p.add_run("SEVA FIRST INNOVATION CHALLENGE · SUBMIT YOUR IDEA")
    r.font.size = Pt(9); r.font.bold = True; r.font.color.rgb = RED
    h = doc.add_paragraph()
    r = h.add_run("MeshAlive – portal answers")
    r.font.size = Pt(24); r.font.bold = True; r.font.color.rgb = NAVY

    how = doc.add_table(rows=1, cols=1)
    how.style = "Table Grid"
    c = how.rows[0].cells[0]
    shade(c, "FFF4E5")
    c.paragraphs[0].add_run("How to use this file").bold = True
    for line in [
        "Each field below uses the portal's exact label, in the portal's order.",
        "Copy only the text inside the grey box and paste it into the matching field.",
        "Every answer is already within the portal's word limit (shown next to each label).",
        "For drop-downs and tick-boxes, choose the option written in the box.",
        "Section 03 lists the files to upload (all included with this document).",
    ]:
        c.add_paragraph("• " + line)

    n = [0]
    total = len(IDENTITY) + len(ANSWERS) + 2

    def heading(text):
        p = doc.add_paragraph()
        p.paragraph_format.space_before = Pt(16)
        r = p.add_run(text)
        r.font.size = Pt(14); r.font.bold = True; r.font.color.rgb = RED

    def label(title, guide=None, count=None):
        n[0] += 1
        p = doc.add_paragraph()
        p.paragraph_format.space_before = Pt(12)
        p.paragraph_format.keep_with_next = True
        r = p.add_run(f"{n[0]}. {title} *")
        r.font.bold = True; r.font.size = Pt(12); r.font.color.rgb = NAVY
        if count:
            r = p.add_run(f"      {count}")
            r.font.size = Pt(9); r.font.color.rgb = MUTED
        if guide:
            g = doc.add_paragraph()
            g.paragraph_format.keep_with_next = True
            r = g.add_run(guide)
            r.italic = True; r.font.size = Pt(9); r.font.color.rgb = MUTED

    def box(text, bold=False):
        t = doc.add_table(rows=1, cols=1)
        t.style = "Table Grid"
        cell = t.rows[0].cells[0]
        shade(cell, "F2F4F7")
        paras = [x.strip() for x in text.strip().split("\n") if x.strip()]
        cell.paragraphs[0].text = paras[0]
        if bold:
            cell.paragraphs[0].runs[0].bold = True
        for x in paras[1:]:
            q = cell.add_paragraph(x)
            q.paragraph_format.space_before = Pt(4)

    heading("01 · SUBMISSION IDENTITY")
    guides = {
        "Title of the solution": None,
        "Theme": "Drop-down: select this theme.",
        "Problem Statement": "Type the problem statement your idea addresses.",
        "Track": "Drop-down: select this track.",
    }
    for k, v in IDENTITY:
        label(k, guides.get(k))
        box(v, bold=k in ("Theme", "Track"))

    heading("02 · PROBLEM AND SOLUTION")
    by_title = {a[0]: a for a in ANSWERS}

    def field(key):
        title, guide, limit, answer = by_title[key]
        label(title, guide, f"{words(answer)} / {limit} words")
        box(answer)

    def ticks(title, options, chosen, guide):
        label(title, guide)
        picked = [o for o in options if (o in chosen if isinstance(chosen, set) else o == chosen)]
        box("Tick: " + ", ".join(picked), bold=True)

    for k in ["What problem are you solving?", "What is your proposed solution?",
              "Who is affected, and where does the problem occur?", "What is innovative or different about your solution?",
              "How does your solution work?"]:
        field(k)
    ticks(STAGE[0], STAGE[1], STAGE[2], "Tick the current stage of your solution.")
    for k in ["Additional notes on your stage", "Who will benefit and what impact will it create?",
              "What is the estimated cost of your solution?", "How can the solution be implemented and scaled?"]:
        field(k)
    ticks(EVIDENCE[0], EVIDENCE[1], EVIDENCE[2], "Tick all that apply.")
    for k in ["Describe your evidence", "Who could adopt or implement the solution?",
              "What support do you need to take the solution forward?"]:
        field(k)

    heading("03 · EVIDENCE – FILES TO UPLOAD")
    t = doc.add_table(rows=1, cols=3)
    t.style = "Table Grid"
    hdr = t.rows[0].cells
    for i, h in enumerate(["Portal field", "What to upload / paste", "Notes"]):
        hdr[i].text = h
        hdr[i].paragraphs[0].runs[0].bold = True
        shade(hdr[i], "EEF2F8")
    for k, v, note in ATTACH:
        row = t.add_row().cells
        row[0].text = k; row[1].text = v; row[2].text = note
        row[0].paragraphs[0].runs[0].bold = True

    doc.save(OUT)
    for f, _, lim, a in ANSWERS:
        print(f"{words(a):4d}/{lim:<4d} {f}")
    print("fields:", n[0], "saved", OUT)


if __name__ == "__main__":
    build()
