// MeshAlive formal presentation (16:9). White background, black/grey text, one navy accent.
// Run: node build_deck.js
const path = require("path");
const pptxgen = require("pptxgenjs");

const M = (f) => path.resolve(__dirname, "../../website/assets/mock", f);
const OUT = path.resolve(__dirname, process.argv[2] || "../MeshAlive_Presentation.pptx");

const C = { accent: "1F3864", ink: "1A1A1A", body: "3F3F3F", muted: "7F7F7F", line: "D9D9D9", fill: "F3F4F6", white: "FFFFFF" };
const FONT = "Calibri";
const HEAD = "Calibri";
const PHONE_AR = 1136 / 1806;
const BROWSER_AR = 1800 / 1246;

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.333 x 7.5 in
pres.title = "MeshAlive – Offline Disaster-Response Network";
pres.author = "MeshAlive team";
pres.subject = "SevaFirst Innovation Challenge 2026 · Theme 05 Surakshit Bharat";

let n = 0;
function slide(section, title) {
  const s = pres.addSlide();
  n += 1;
  s.background = { color: C.white };
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: 13.333, h: 0.09, fill: { color: C.accent }, line: { type: "none" } });
  if (section) s.addText(section.toUpperCase(), { x: 0.6, y: 0.38, w: 9, h: 0.3, fontFace: FONT, fontSize: 11, bold: true, color: C.accent, charSpacing: 2, margin: 0 });
  if (title) s.addText(title, { x: 0.6, y: 0.68, w: 12.1, h: 0.7, fontFace: HEAD, fontSize: 28, bold: true, color: C.ink, margin: 0, valign: "top" });
  s.addShape(pres.shapes.LINE, { x: 0.6, y: 1.45, w: 12.13, h: 0, line: { color: C.line, width: 1 } });
  s.addShape(pres.shapes.LINE, { x: 0.6, y: 6.98, w: 12.13, h: 0, line: { color: C.line, width: 0.75 } });
  s.addText("MeshAlive  |  SevaFirst Innovation Challenge 2026", { x: 0.6, y: 7.05, w: 7, h: 0.28, fontFace: FONT, fontSize: 10, color: C.muted, margin: 0 });
  s.slideNumber = { x: 12.13, y: 7.05, w: 0.6, h: 0.28, fontFace: FONT, fontSize: 10, color: C.muted, align: "right" };
  return s;
}

function bullets(s, items, x, y, w, h, size = 15, gap = 8) {
  const arr = items.map((it) => {
    const t = typeof it === "string" ? { text: it } : it;
    return { text: t.text, options: { bullet: { indent: 18 }, breakLine: true, paraSpaceAfter: gap, bold: !!t.bold } };
  });
  s.addText(arr, { x, y, w, h, fontFace: FONT, fontSize: size, color: C.body, valign: "top", margin: 0 });
}

function sub(s, text, x, y, w) {
  s.addText(text, { x, y, w, h: 0.38, fontFace: HEAD, fontSize: 16, bold: true, color: C.ink, margin: 0 });
  s.addShape(pres.shapes.LINE, { x, y: y + 0.42, w: 0.5, h: 0, line: { color: C.accent, width: 2 } });
}

function para(s, text, x, y, w, h, size = 15, color = C.body, extra = {}) {
  s.addText(text, { x, y, w, h, fontFace: FONT, fontSize: size, color, valign: "top", margin: 0, ...extra });
}

function box(s, x, y, w, h, fill = C.fill) {
  s.addShape(pres.shapes.RECTANGLE, { x, y, w, h, fill: { color: fill }, line: { color: C.line, width: 0.75 } });
}

function table(s, rows, x, y, w, colW, size = 12.5, highlightCol = -1, h = 0) {
  const data = rows.map((r, ri) => r.map((cell, ci) => ({
    text: cell,
    options: {
      bold: ri === 0 || ci === 0 || ci === highlightCol,
      color: ri === 0 ? C.white : C.ink,
      fill: { color: ri === 0 ? C.accent : (ci === highlightCol ? "E8ECF4" : (ri % 2 ? C.white : "FAFAFA")) },
    },
  })));
  s.addTable(data, { x, y, w, colW, fontFace: FONT, fontSize: size, color: C.body, border: { type: "solid", pt: 0.75, color: C.line }, margin: [4, 8, 4, 8], valign: "middle", ...(h ? { rowH: h / rows.length } : {}) });
}

function phone(s, file, x, y, h, caption) {
  s.addImage({ path: M(file), x, y, h, w: h * PHONE_AR, altText: caption || file });
  if (caption) s.addText(caption, { x: x - 0.2, y: y + h + 0.02, w: h * PHONE_AR + 0.4, h: 0.3, fontFace: FONT, fontSize: 11, color: C.muted, align: "center", margin: 0 });
}

// 1 ─ Title
{
  const s = pres.addSlide();
  n += 1;
  s.background = { color: C.white };
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: 0.28, h: 7.5, fill: { color: C.accent }, line: { type: "none" } });
  s.addText("SEVAFIRST INNOVATION CHALLENGE 2026", { x: 0.9, y: 0.9, w: 7, h: 0.3, fontFace: FONT, fontSize: 12, bold: true, color: C.accent, charSpacing: 2, margin: 0 });
  s.addText("MeshAlive", { x: 0.9, y: 1.5, w: 7, h: 1.0, fontFace: HEAD, fontSize: 54, bold: true, color: C.ink, margin: 0 });
  s.addText("An Offline Disaster-Response Network", { x: 0.9, y: 2.5, w: 7, h: 0.6, fontFace: HEAD, fontSize: 26, color: C.body, margin: 0 });
  para(s, "Carrying SOS messages to the district control room, and help back to people, when mobile towers fail.", 0.9, 3.25, 6.3, 0.9, 16, C.muted);
  table(s, [
    ["Submission details", ""],
    ["Theme", "05 · Surakshit Bharat"],
    ["Track", "Track A · Product or Technology"],
    ["Stage", "Working prototype"],
    ["Website and demo", "meshalive.benzo.co.in"],
    ["Date", "November 2026"],
  ], 0.9, 4.3, 6.0, [2.0, 4.0], 12.5);
  phone(s, "phone-situation.png", 8.4, 0.75, 6.0);
}

// 2 ─ Agenda
{
  const s = slide("Contents", "Agenda");
  const items = ["Executive summary", "The problem and who it affects", "Existing approaches and their gaps", "Proposed solution and key features",
    "Prototype: mobile app and district control room", "Technical architecture, routing and security", "Evidence and results", "Impact, cost and sustainability",
    "Implementation roadmap and risks", "Adoption, partners and support required"];
  items.forEach((t, i) => {
    const col = i < 5 ? 0 : 1;
    const y = 1.9 + (i % 5) * 0.92;
    const x = 0.6 + col * 6.2;
    s.addText(String(i + 1).padStart(2, "0"), { x, y, w: 0.7, h: 0.5, fontFace: HEAD, fontSize: 22, bold: true, color: C.accent, margin: 0 });
    s.addText(t, { x: x + 0.8, y: y + 0.05, w: 5.0, h: 0.45, fontFace: FONT, fontSize: 17, color: C.ink, margin: 0 });
    s.addShape(pres.shapes.LINE, { x, y: y + 0.68, w: 5.7, h: 0, line: { color: C.line, width: 0.75 } });
  });
}

// 3 ─ Executive summary
{
  const s = slide("Overview", "Executive summary");
  para(s, "During cyclones and floods, Indian districts lose mobile coverage for hours or days, exactly when people most need help. MeshAlive keeps emergency communication working without towers, internet or new hardware.", 0.6, 1.75, 7.4, 1.1, 16, C.ink);
  bullets(s, [
    "A one-tap SOS carries GPS location and a situation report, scored 0–100 for urgency.",
    "Nearby phones relay it over Bluetooth and Wi-Fi using battery-aware routing.",
    "Rescue boats and teams carry messages out of areas with no route.",
    "The first phone with signal uploads everything to a live district dashboard.",
    "Dispatch and evacuation messages travel back through the same mesh, digitally signed.",
  ], 0.6, 3.0, 7.4, 3.6, 15, 9);
  box(s, 8.5, 1.75, 4.23, 4.95);
  sub(s, "Status at a glance", 8.8, 1.95, 3.7);
  const kv = [["Prototype", "Working Android app and dashboard"], ["Languages", "Tamil and English"], ["Testing", "49 automated tests passing"], ["Simulation", "100% delivery, 200 phones"], ["Hardware cost", "₹0 for citizens and rescuers"], ["Ask", "District pilot partnership"]];
  kv.forEach(([k, v], i) => {
    s.addText(k, { x: 8.8, y: 2.65 + i * 0.66, w: 3.7, h: 0.25, fontFace: FONT, fontSize: 11, color: C.muted, margin: 0 });
    s.addText(v, { x: 8.8, y: 2.9 + i * 0.66, w: 3.7, h: 0.3, fontFace: FONT, fontSize: 14, bold: true, color: C.ink, margin: 0 });
  });
}

// 4 ─ Problem
{
  const s = slide("The problem", "When the towers go down, rescue goes blind");
  para(s, "Every cyclone and flood season, coastal and low-lying districts lose mobile coverage. Towers lose power, backhaul links are cut and the remaining network is overloaded. This happened in the 2015 Chennai floods and again during Cyclone Michaung in 2023.", 0.6, 1.7, 12.1, 0.95, 15.5);
  const cols = [["People cannot call for help", "Trapped families, the elderly, patients and people with disabilities cannot call, text or share their location."],
    ["Control rooms work blind", "There is no view of who needs help, where they are, how many people are with them, or which case is most urgent."],
    ["Rescue capacity is misdirected", "Boats and teams search by sight and word of mouth, so they reach the easiest-to-see rather than the most critical."]];
  cols.forEach(([h, b], i) => {
    const x = 0.6 + i * 4.1;
    box(s, x, 2.85, 3.9, 2.3);
    s.addText(h, { x: x + 0.25, y: 3.05, w: 3.4, h: 0.45, fontFace: HEAD, fontSize: 16, bold: true, color: C.ink, margin: 0 });
    para(s, b, x + 0.25, 3.6, 3.4, 1.4, 13.5);
  });
  s.addShape(pres.shapes.RECTANGLE, { x: 0.6, y: 5.45, w: 0.08, h: 1.2, fill: { color: C.accent }, line: { type: "none" } });
  s.addText([{ text: "Problem statement", options: { bold: true, color: C.accent, fontSize: 12, breakLine: true } },
    { text: "When cyclones and floods take down mobile towers and power, people in danger cannot call for help, and district control rooms cannot see who needs rescue, where they are, or how urgent it is.", options: { color: C.ink, fontSize: 15 } }],
  { x: 0.85, y: 5.45, w: 11.8, h: 1.2, fontFace: FONT, valign: "middle", margin: 0 });
}

// 5 ─ Who is affected
{
  const s = slide("The problem", "Who is affected, and where");
  sub(s, "People", 0.6, 1.75, 5.8);
  bullets(s, ["Families in flood- and cyclone-prone areas, especially the elderly, children, people with disabilities and patients",
    "Fishing communities and low-lying coastal settlements",
    "Rescue teams: NDRF, SDRF, fire and rescue services, boat operators and ambulance crews",
    "District Emergency Operations Centres and Disaster Management Authorities"], 0.6, 2.4, 5.8, 3.3, 16, 10);
  sub(s, "Places", 6.9, 1.75, 5.8);
  bullets(s, ["Coastal districts of Tamil Nadu, such as Chennai, Cuddalore, Nagapattinam and Thoothukudi",
    "India’s cyclone-exposed east and west coasts",
    "River-flood regions: the Brahmaputra and Ganga basins, and Kerala",
    "Cities where flooding cuts power to mobile towers"], 6.9, 2.4, 5.8, 3.3, 16, 10);
  box(s, 0.6, 5.75, 12.13, 0.95);
  para(s, "Why it matters: in the first hours of a disaster, rescue capacity is scarce. Location and urgency data decide who is reached first.", 0.85, 5.95, 11.6, 0.6, 14.5, C.ink, { valign: "middle" });
}

// 6 ─ Existing approaches
{
  const s = slide("Context", "Existing approaches and their gaps");
  table(s, [
    ["Approach", "What it does", "Gap during a disaster"],
    ["Mobile calls and SMS", "Standard emergency communication", "Fails when towers lose power or backhaul"],
    ["Offline chat apps (e.g. Bridgefy, Briar)", "Phone-to-phone messaging", "Do not reach rescuers, no prioritisation, no reply channel"],
    ["LoRa radios (e.g. Meshtastic, goTenna)", "Long-range text over radio", "Extra hardware of ₹2,500+ per person; low adoption"],
    ["Satellite phones", "Works without local networks", "Expensive; held by few officials, not by citizens"],
    ["Broadcast alerts (cell broadcast, sirens)", "One-way warnings", "Cannot receive SOS or confirm who is safe"],
  ], 0.6, 1.8, 12.13, [3.4, 3.8, 4.93], 15, -1, 3.75);
  para(s, "The gap: no low-cost, two-way channel connects people in danger to the district control room when the network is down.", 0.6, 5.85, 12.1, 0.7, 15.5, C.ink, { bold: true });
}

// 7 ─ Solution overview
{
  const s = slide("Proposed solution", "A complete rescue loop on phones people already own");
  para(s, "MeshAlive is an Android app and a browser-based district dashboard. Together they form a two-way emergency channel that works without towers, internet or extra hardware.", 0.6, 1.7, 12.1, 0.8, 15.5);
  const steps = [["Ask for help", "One-tap SOS with GPS and a situation report, scored 0–100"], ["Phones relay", "Bluetooth and Wi-Fi mesh, battery-aware, most urgent first"],
    ["Rescuers carry", "Boats and teams carry SOS out of areas with no route"], ["Control room sees", "First phone with signal uploads to a live dashboard"],
    ["Help returns", "Dispatch and evacuation alerts travel back, signed"]];
  steps.forEach(([h, b], i) => {
    const x = 0.6 + i * 2.47;
    s.addShape(pres.shapes.OVAL, { x: x + 0.85, y: 2.75, w: 0.6, h: 0.6, fill: { color: C.white }, line: { color: C.accent, width: 2 } });
    s.addText(String(i + 1), { x: x + 0.85, y: 2.75, w: 0.6, h: 0.6, fontFace: HEAD, fontSize: 18, bold: true, color: C.accent, align: "center", valign: "middle", margin: 0 });
    if (i < 4) s.addShape(pres.shapes.LINE, { x: x + 1.55, y: 3.05, w: 2.27, h: 0, line: { color: C.line, width: 1.5, endArrowType: "triangle" } });
    s.addText(h, { x, y: 3.55, w: 2.3, h: 0.4, fontFace: HEAD, fontSize: 15, bold: true, color: C.ink, align: "center", margin: 0 });
    para(s, b, x + 0.05, 4.0, 2.2, 1.1, 12.5, C.body, { align: "center" });
  });
  box(s, 0.6, 5.45, 12.13, 1.2);
  bullets(s, ["No towers, no internet and no SIM needed for the mesh", "No extra hardware: uses citizens’ and rescuers’ existing phones", "Works in Tamil and English, with large, simple controls"], 0.85, 5.6, 11.6, 1.0, 13.5, 3);
}

// 8 ─ Key features
{
  const s = slide("Proposed solution", "Key features");
  sub(s, "For people in danger", 0.6, 1.75, 5.8);
  bullets(s, ["30-second guided setup: language, household and role",
    "One-tap SOS with a 3-second countdown and Cancel to prevent false alarms",
    "Situation report: people, trapped, injured, elderly, children, disabled and water level, turned into an urgency score",
    "“I’m safe” check-ins, so rescuers can skip safe households",
    "“Rescue team on the way” confirmation, even with no signal",
    "Full SOS flow in Tamil and English"], 0.6, 2.4, 5.8, 4.4, 15.5, 10);
  sub(s, "For rescuers and officials", 6.9, 1.75, 5.8);
  bullets(s, ["Rescue-carrier mode for boats, NDRF/SDRF teams and ambulances",
    "Any phone that regains signal becomes a gateway automatically",
    "Live district dashboard: map, urgency, people count and route",
    "Dispatch team, mark rescued and evacuation broadcast, digitally signed",
    "District coverage planner: installs needed per ward",
    "Field-trial evidence recorder with exportable reports"], 6.9, 2.4, 5.8, 4.4, 15.5, 10);
}

// 9 ─ Prototype: app
{
  const s = slide("Prototype", "Mobile app: working Android prototype");
  const shots = [["phone-home.png", "Home: mesh status, one-tap SOS"], ["phone-situation.png", "Situation report: urgency 81"], ["phone-countdown.png", "3-second countdown"], ["phone-tamil.png", "Complete flow in Tamil"]];
  shots.forEach(([f, c], i) => phone(s, f, 0.85 + i * 3.1, 1.65, 4.45, c));
  para(s, "Built with Kotlin and Jetpack Compose. All screens are captured from the running app.", 0.6, 6.6, 12.1, 0.3, 12, C.muted);
}

// 10 ─ Prototype: control room
{
  const s = slide("Prototype", "District control room dashboard");
  const w = 7.2;
  s.addImage({ path: M("browser-dashboard.png"), x: 0.5, y: 1.65, w, h: w / BROWSER_AR, altText: "District control-room dashboard" });
  sub(s, "Operator actions", 8.5, 1.75, 4.2);
  bullets(s, [{ text: "Live map sorted by urgency", bold: true }, "Critical cases first, with people count and route",
    { text: "Dispatch team", bold: true }, "Reply travels back; the family’s phone confirms receipt",
    { text: "Mark rescued", bold: true }, "Closes the case and informs the household",
    { text: "Evacuation broadcast", bold: true }, "Any language, to every phone in the mesh"], 8.5, 2.4, 4.2, 4.3, 15, 4);
  para(s, "Runs in any browser on an existing control-room PC. Live demo: meshalive.benzo.co.in/dashboard", 0.6, 6.6, 12.1, 0.3, 12, C.muted);
}

// 11 ─ Architecture
{
  const s = slide("Technology", "System architecture");
  const nodes = [["Citizen phone", "SOS + situation report"], ["Relay phones", "Bluetooth / Wi-Fi mesh"], ["Rescue carrier", "Store-carry-forward"], ["Gateway phone", "First phone with signal"], ["District dashboard", "Live map and commands"]];
  nodes.forEach(([h, b], i) => {
    const x = 0.6 + i * 2.5;
    s.addShape(pres.shapes.RECTANGLE, { x, y: 1.95, w: 2.1, h: 1.15, fill: { color: i === 4 ? C.accent : C.white }, line: { color: C.accent, width: 1.5 } });
    s.addText([{ text: h, options: { bold: true, fontSize: 14, color: i === 4 ? C.white : C.ink, breakLine: true } }, { text: b, options: { fontSize: 11.5, color: i === 4 ? "DCE3F0" : C.muted } }],
      { x, y: 1.95, w: 2.1, h: 1.15, fontFace: FONT, align: "center", valign: "middle", margin: 2 });
    if (i < 4) s.addShape(pres.shapes.LINE, { x: x + 2.12, y: 2.52, w: 0.36, h: 0, line: { color: C.ink, width: 1.25, endArrowType: "triangle" } });
  });
  s.addShape(pres.shapes.LINE, { x: 1.65, y: 3.5, w: 10.0, h: 0, line: { color: C.muted, width: 1.25, dashType: "dash", beginArrowType: "triangle" } });
  para(s, "Return path: signed dispatch and evacuation commands travel back through the gateway and the mesh to the citizen’s phone", 1.65, 3.6, 10.0, 0.4, 12, C.muted, { align: "center" });
  table(s, [
    ["Layer", "Implementation"],
    ["Mobile app", "Kotlin, Jetpack Compose, Room database, Android 8.0 or later"],
    ["Peer-to-peer link", "Google Nearby Connections (Bluetooth and Wi-Fi), no internet required"],
    ["Cloud relay", "Lightweight publish/subscribe relay (prototype: ntfy), self-hostable"],
    ["Dashboard", "Browser app (HTML/JavaScript, Leaflet maps), no installation"],
  ], 0.6, 4.25, 12.13, [3.0, 9.13], 14, -1, 2.45);
}

// 12 ─ Routing and reliability
{
  const s = slide("Technology", "Routing and reliability");
  table(s, [
    ["Mechanism", "How it works", "Benefit"],
    ["Network map", "Each phone shares battery, neighbours and internet status every few seconds; link quality measured by round-trip time", "Every phone knows the live mesh"],
    ["Battery-aware routing", "Dijkstra with edge cost = link quality + battery penalty + hop cost; a virtual super-node joins all gateways", "Avoids phones about to die; one run, O((V+E) log V)"],
    ["Urgency priority queue", "Messages sent in order of triage score", "Most critical SOS go first"],
    ["Store-and-forward", "Messages kept and retried when no route exists", "Nothing lost while out of range"],
    ["Duplicate filter and TTL", "Seen-message cache and hop limit", "No loops or broadcast storms"],
    ["Delivery receipts", "Acknowledgement returned with hop count", "Sender knows the SOS arrived"],
    ["Rescue carriers", "Custody transfer to boat and team phones", "Reaches areas with no route at all"],
  ], 0.6, 1.75, 12.13, [2.6, 6.0, 3.53], 14, -1, 4.95);
}

// 13 ─ Security and privacy
{
  const s = slide("Technology", "Security and privacy");
  sub(s, "Controls in the prototype", 0.6, 1.75, 5.8);
  bullets(s, ["AES-GCM encryption of message content on the phone",
    "Control-room commands signed with HMAC-SHA256",
    "Phones reject unsigned, altered or expired commands (6-hour limit)",
    "No account or sign-up; no personal data beyond what the SOS needs",
    "Location shared only inside an SOS sent by the user"], 0.6, 2.4, 5.8, 4.3, 16, 12);
  sub(s, "Threats addressed", 6.9, 1.75, 5.8);
  table(s, [
    ["Threat", "Mitigation"],
    ["Fake evacuation order", "Signature check on every command"],
    ["Replay of old orders", "Timestamp and expiry window"],
    ["Message flooding", "Duplicate filter and hop limit"],
    ["Accidental SOS", "3-second countdown with Cancel"],
  ], 6.9, 2.4, 5.83, [2.6, 3.23], 14.5, -1, 3.0);
}

// 14 ─ Evidence
{
  const s = slide("Evidence", "Results: 200-phone simulation");
  para(s, "Measured with the app’s own routing engine: 200 phones, 20 SOS messages, the same network for every method.", 0.6, 1.7, 12.1, 0.5, 14.5);
  table(s, [
    ["Measure", "Flooding", "Shortest hop", "MeshAlive"],
    ["SOS delivered", "85%", "100%", "100%"],
    ["Radio transmissions", "14,533", "88", "99"],
    ["Average hops", "5.47", "4.40", "4.95"],
    ["Battery left, weakest phone", "0.0%", "3.2%", "6.3%"],
  ], 0.6, 2.35, 6.9, [2.8, 1.3, 1.4, 1.4], 14.5, 3, 2.85);
  s.addChart(pres.charts.BAR, [{ name: "Transmissions", labels: ["Flooding", "Shortest hop", "MeshAlive"], values: [14533, 88, 99] }], {
    x: 7.9, y: 2.2, w: 4.85, h: 3.0, barDir: "col", chartColors: ["A6A6A6", "A6A6A6", C.accent], catAxisLabelFontSize: 11, valAxisLabelFontSize: 10,
    catAxisLabelColor: C.body, valAxisLabelColor: C.muted, valGridLine: { color: "E5E5E5", size: 0.5 }, showValue: true, dataLabelFontSize: 10, dataLabelColor: C.ink,
    showTitle: true, title: "Radio transmissions for 20 SOS", titleFontSize: 12, titleColor: C.ink, showLegend: false,
  });
  box(s, 0.6, 5.45, 12.13, 1.2);
  bullets(s, ["Every SOS delivered, with 147 times fewer transmissions than flooding",
    "Twice the battery left on the weakest phone compared with shortest-hop routing, so the mesh stays alive longer",
    "49 automated tests cover routing, forwarding, triage, command signatures, the planner and evidence reports"], 0.85, 5.55, 11.6, 1.05, 13, 2);
}

// 15 ─ Innovation
{
  const s = slide("Innovation", "What is new");
  table(s, [
    ["Capability", "Chat apps", "LoRa radios", "MeshAlive"],
    ["Reaches the district control room", "No", "No", "Yes, live dashboard"],
    ["Reply back to the person", "No", "No", "Dispatch and evacuation alerts"],
    ["Prioritises urgent cases", "No", "No", "Urgency score 0–100"],
    ["No phone in range", "Message waits", "Message waits", "Rescue carriers carry it out"],
    ["Battery management", "Flooding", "Flooding", "Battery-aware routing"],
    ["Protection against fake alerts", "Not addressed", "Not addressed", "Signed commands"],
    ["Extra hardware", "None", "₹2,500+ per person", "None"],
    ["Indian languages", "English", "English", "Tamil and English"],
  ], 0.6, 1.75, 12.13, [3.6, 2.4, 2.6, 3.53], 15, 3, 4.95);
}

// 16 ─ Impact
{
  const s = slide("Impact", "Beneficiaries and expected impact");
  sub(s, "Who benefits", 0.6, 1.75, 6.2);
  bullets(s, ["People trapped or stranded: their SOS reaches rescuers without towers, and they know help is coming",
    "Vulnerable groups: prioritised automatically through the situation report",
    "Rescue teams: exact location, people count and urgency, instead of searching blind",
    "Control rooms: a live, prioritised picture, and a channel to reach people without signal",
    "Administration: “I’m safe” check-ins reduce wasted searches"], 0.6, 2.4, 6.2, 4.4, 15, 11);
  phone(s, "phone-planner.png", 7.15, 1.7, 4.6);
  box(s, 10.2, 1.75, 2.53, 4.6);
  s.addText("Coverage model", { x: 10.35, y: 1.95, w: 2.25, h: 0.35, fontFace: HEAD, fontSize: 14, bold: true, color: C.ink, margin: 0 });
  s.addText("~3,700", { x: 10.35, y: 2.4, w: 2.25, h: 0.7, fontFace: HEAD, fontSize: 32, bold: true, color: C.accent, margin: 0 });
  para(s, "of 4,000 people can reach the control room in a 1 km² ward with 1,000 app users (about 4 people per phone).", 10.35, 3.15, 2.25, 1.9, 12);
  para(s, "Planner model; to be validated in the pilot.", 10.35, 5.35, 2.25, 0.8, 11, C.muted);
}

// 17 ─ Cost and sustainability
{
  const s = slide("Impact", "Cost and sustainability");
  table(s, [
    ["Stakeholder", "Hardware cost", "Notes"],
    ["Citizens", "₹0", "Free app on their own phone; no SIM, data or account needed for the mesh"],
    ["Rescue carriers", "₹0", "Uses phones that crews already carry"],
    ["District control room", "₹0", "Runs in any browser on an existing PC"],
    ["Running cost", "Low", "Small self-hosted relay server and app maintenance"],
    ["Rollout", "Programme cost", "Pre-monsoon awareness and installation drives through the DDMA"],
  ], 0.6, 1.75, 12.13, [2.8, 2.0, 7.33], 15, -1, 3.0);
  sub(s, "Sustainability", 0.6, 5.0, 12);
  bullets(s, ["Funded through district disaster-preparedness budgets; no per-user licence", "Open, auditable code that state IT departments can host and maintain"], 0.6, 5.6, 12.1, 1.1, 14, 4);
}

// 18 ─ Roadmap
{
  const s = slide("Implementation", "Implementation roadmap");
  const ph = [["Phase 1", "Field pilot", "3 months", ["One flood-prone ward with the DDMA", "NDRF/SDRF boats as rescue carriers", "Dashboard in the district EOC", "Measure delivery rate and SOS-to-dispatch time"]],
    ["Phase 2", "District rollout", "Pre-monsoon", ["Install drives via schools, colleges, panchayats and SHGs", "Planner sets the install target per ward", "Training for control-room operators"]],
    ["Phase 3", "State and national", "Scale-up", ["Distribution through the SDMA", "Dashboard feeds into state EOCs", "Alignment with the Common Alerting Protocol", "More Indian languages; iOS version"]]];
  ph.forEach(([k, t, d, items], i) => {
    const x = 0.6 + i * 4.1;
    s.addShape(pres.shapes.RECTANGLE, { x, y: 1.75, w: 3.9, h: 0.75, fill: { color: i === 0 ? C.accent : C.white }, line: { color: C.accent, width: 1.25 } });
    s.addText([{ text: `${k}  ·  ${t}`, options: { bold: true, fontSize: 15, color: i === 0 ? C.white : C.ink } }, { text: `   ${d}`, options: { fontSize: 11.5, color: i === 0 ? "DCE3F0" : C.muted } }],
      { x: x + 0.2, y: 1.75, w: 3.6, h: 0.75, fontFace: FONT, valign: "middle", margin: 0 });
    if (i < 2) s.addShape(pres.shapes.LINE, { x: x + 3.92, y: 2.12, w: 0.16, h: 0, line: { color: C.accent, width: 1.25, endArrowType: "triangle" } });
    box(s, x, 2.6, 3.9, 3.3, C.white);
    bullets(s, items, x + 0.2, 2.8, 3.5, 3.0, 13.5, 6);
  });
  para(s, "Success measures: SOS delivery rate, time from SOS to dispatch, households confirmed safe, and installs per ward.", 0.6, 6.15, 12.1, 0.5, 13.5, C.ink);
}

// 19 ─ Risks
{
  const s = slide("Implementation", "Risks and mitigations");
  table(s, [
    ["Risk", "Impact", "Mitigation"],
    ["Too few phones with the app in an area", "Messages cannot hop far", "Planner sets install targets; rescue carriers bridge gaps; pre-monsoon install drives"],
    ["Battery drain on relaying phones", "Mesh shrinks over time", "Battery-aware routing; low-battery phones avoided"],
    ["Fake or malicious alerts", "Panic, misdirected rescue", "Signed commands; expiry window; duplicate filtering"],
    ["Accidental or prank SOS", "Wasted rescue effort", "Countdown with Cancel; urgency triage; “I’m safe” check-ins"],
    ["Dependence on a public relay", "Availability and data control", "Self-hosted relay on government infrastructure for deployment"],
    ["Android-only today", "Excludes iPhone users", "iOS version planned for Phase 3"],
  ], 0.6, 1.75, 12.13, [3.4, 2.6, 6.13], 15, -1, 4.95);
}

// 20 ─ Adoption and support
{
  const s = slide("Way forward", "Adoption, partners and support required");
  sub(s, "Who could adopt it", 0.6, 1.75, 5.8);
  bullets(s, ["District Disaster Management Authorities and EOCs (primary users)", "State Disaster Management Authorities, such as TNSDMA",
    "NDMA, for guidelines and Common Alerting Protocol integration", "NDRF, SDRF and fire and rescue services, as rescue carriers",
    "Urban local bodies, panchayats, NGOs, NSS and NCC units"], 0.6, 2.4, 5.8, 4.4, 16, 14);
  sub(s, "Support we are seeking", 6.9, 1.75, 5.8);
  bullets(s, ["A pilot partnership with a district administration and its DDMA", "Collaboration with NDRF/SDRF and boat operators to test carrier mode",
    "Mentorship in disaster-management operations and public-sector deployment", "Technical review: security, Common Alerting Protocol, self-hosting",
    "Pilot funding for a relay server, test devices and training drives"], 6.9, 2.4, 5.8, 4.4, 16, 14);
}

// 21 ─ Conclusion
{
  const s = slide("Conclusion", "Thank you");
  para(s, "MeshAlive turns the phones people already own into a rescue network, so that when the mobile network fails, help does not.", 0.6, 1.8, 7.6, 1.2, 20, C.ink);
  table(s, [
    ["Resource", "Link"],
    ["Website and live demo", "meshalive.benzo.co.in"],
    ["Control-room dashboard", "meshalive.benzo.co.in/dashboard"],
    ["Android app (APK)", "meshalive.benzo.co.in/download/MeshAlive.apk"],
    ["Demo video", "meshalive.benzo.co.in/#demo"],
    ["Source code", "github.com/yogeshwardev/MESHALIVE-"],
  ], 0.6, 3.3, 7.6, [2.6, 5.0], 14.5, -1, 3.0);
  phone(s, "phone-home.png", 9.3, 1.7, 4.8);
}

pres.writeFile({ fileName: OUT }).then((f) => console.log("saved", f, "slides:", n));
