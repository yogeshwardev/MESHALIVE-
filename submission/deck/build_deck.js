// MeshAlive pitch deck (16:9). Run: node build_deck.js
const path = require("path");
const pptxgen = require("pptxgenjs");

const M = (f) => path.resolve(__dirname, "../../website/assets/mock", f);
const OUT = path.resolve(__dirname, "../MeshAlive_Pitch_Deck.pptx");

const C = {
  bg: "000000", bg2: "0A0A0A", card: "0D0D0D", line: "262626",
  text: "EDEDED", muted: "9A9A9A", coral: "FF3B3B", teal: "EDEDED", violet: "BDBDBD",
  amber: "F5A524", green: "22C55E", blue: "8F8F8F",
};
const HEAD = "Arial";
const BODY = "Calibri";

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.333 x 7.5
pres.title = "MeshAlive – SevaFirst pitch";
pres.author = "MeshAlive team";

// phone mock PNGs are 976 x 1646 incl. shadow; browser mock aspect from file
const PHONE_AR = 1136 / 1806;
function phone(slide, file, x, y, h) {
  slide.addImage({ path: M(file), x, y, h, w: h * PHONE_AR, altText: file.replace(".png", "").replace("phone-", "MeshAlive app: ") });
}

function base(kicker, title, opts = {}) {
  const s = pres.addSlide();
  s.background = { color: opts.bg || C.bg };
  if (opts.glow === true) {
    s.addShape(pres.shapes.OVAL, { x: 9.6, y: -2.6, w: 6, h: 5, fill: { color: C.violet, transparency: 86 }, line: { type: "none" } });
    s.addShape(pres.shapes.OVAL, { x: -2.4, y: 5.2, w: 5.5, h: 4.5, fill: { color: C.teal, transparency: 90 }, line: { type: "none" } });
  }
  if (kicker) s.addText(kicker.toUpperCase(), { x: 0.6, y: 0.42, w: 9, h: 0.35, fontFace: BODY, fontSize: 12, bold: true, color: "8F8F8F", charSpacing: 4, margin: 0, isTextBox: true });
  if (title) s.addText(title, { x: 0.6, y: 0.78, w: 12.1, h: 0.95, fontFace: HEAD, fontSize: 32, bold: true, color: "FFFFFF", margin: 0, isTextBox: true, valign: "top" });
  s.addText("MeshAlive · SevaFirst Innovation Challenge", { x: 0.6, y: 7.05, w: 6, h: 0.3, fontFace: BODY, fontSize: 10, color: "5C5C5C", margin: 0, isTextBox: true });
  return s;
}

function card(s, x, y, w, h, title, body, accent = C.coral) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.15, fill: { color: C.card }, line: { color: C.line, width: 1 } });
  s.addShape(pres.shapes.OVAL, { x: x + 0.3, y: y + 0.32, w: 0.22, h: 0.22, fill: { color: accent }, line: { type: "none" } });
  s.addText(title, { x: x + 0.3, y: y + 0.66, w: w - 0.6, h: 0.5, fontFace: HEAD, fontSize: 17, bold: true, color: "FFFFFF", margin: 0, isTextBox: true, valign: "top" });
  s.addText(body, { x: x + 0.3, y: y + 1.16, w: w - 0.6, h: h - 1.3, fontFace: BODY, fontSize: 14.5, color: C.muted, margin: 0, isTextBox: true, valign: "top" });
}

// 1 ── Cover
{
  const s = base(null, null);
  s.addText("MeshAlive", { x: 0.6, y: 0.6, w: 6, h: 0.5, fontFace: HEAD, fontSize: 20, bold: true, color: C.coral, margin: 0, isTextBox: true });
  s.addText([
    { text: "Help reaches you,", options: { color: "FFFFFF", breakLine: true } },
    { text: "even when the network doesn’t.", options: { color: "8F8F8F" } },
  ], { x: 0.6, y: 1.5, w: 6.6, h: 2.4, fontFace: HEAD, fontSize: 40, bold: true, margin: 0, isTextBox: true, valign: "top" });
  s.addText("An offline disaster-response network. When cyclones and floods take down mobile towers, SOS messages travel phone to phone and through rescue boats to the district control room, and help comes back the same way.",
    { x: 0.6, y: 4.1, w: 6.2, h: 1.4, fontFace: BODY, fontSize: 16, color: C.muted, margin: 0, isTextBox: true, valign: "top" });
  const tags = ["Theme 05 · Surakshit Bharat", "Track A · Product", "Working prototype"];
  tags.forEach((t, i) => s.addText(t, { x: 0.6 + i * 2.25, y: 5.75, w: 2.1, h: 0.42, fontFace: BODY, fontSize: 12, color: C.text, align: "center", valign: "middle", margin: 0, isTextBox: true,
    shape: pres.shapes.ROUNDED_RECTANGLE, rectRadius: 0.21, fill: { color: C.card }, line: { color: C.line, width: 1 } }));
  s.addImage({ path: M("phones-trio.png"), x: 6.9, y: 0.7, w: 6.2, h: 6.2 * (1686 / 2196), altText: "MeshAlive app screens: SOS countdown, situation report, coverage planner" });
}

// 2 ── Problem
{
  const s = base("The problem", "When the towers go down, rescue goes blind");
  s.addText("Every cyclone and flood season, districts lose mobile coverage for hours or days, as in the 2015 Chennai floods and Cyclone Michaung in 2023. That is exactly when people most need help.",
    { x: 0.6, y: 1.75, w: 11.5, h: 0.9, fontFace: BODY, fontSize: 17, color: C.muted, margin: 0, isTextBox: true, valign: "top" });
  card(s, 0.6, 2.95, 3.9, 3.0, "People can’t call for help", "Trapped families, the elderly and patients can’t call, text or share their location.", C.coral);
  card(s, 4.72, 2.95, 3.9, 3.0, "Control rooms work blind", "No view of who needs help, where they are, how many people, or how urgent.", C.violet);
  card(s, 8.84, 2.95, 3.9, 3.0, "Chat apps aren’t enough", "They don’t reach rescuers or prioritise, and they drain the phones that relay for others.", C.amber);
}

// 3 ── Solution loop
{
  const s = base("The solution", "A complete rescue loop on phones people already own");
  const steps = [
    ["Ask for help", "One-tap SOS with GPS and a situation report, scored 0–100 for urgency.", C.coral],
    ["Phones relay", "Bluetooth and Wi-Fi mesh. Battery-aware routing; most urgent first.", "EDEDED"],
    ["Rescuers carry", "Boats and NDRF teams carry stuck SOS out of dead zones.", C.violet],
    ["Control room sees", "The first phone with signal uploads to a live, urgency-sorted map.", C.teal],
    ["Help comes back", "Dispatch and evacuation alerts reach phones with no signal.", C.green],
  ];
  s.addShape(pres.shapes.LINE, { x: 1.4, y: 2.62, w: 10.6, h: 0, line: { color: C.line, width: 2 } });
  steps.forEach(([t, b, col], i) => {
    const x = 0.6 + i * 2.48;
    s.addText(String(i + 1), { x: x + 0.62, y: 2.22, w: 0.8, h: 0.8, fontFace: HEAD, fontSize: 20, bold: true, color: "FFFFFF", align: "center", valign: "middle", margin: 0, isTextBox: true,
      shape: pres.shapes.OVAL, fill: { color: C.bg }, line: { color: col, width: 2.5 } });
    s.addText(t, { x, y: 3.3, w: 2.25, h: 0.5, fontFace: HEAD, fontSize: 16, bold: true, color: "FFFFFF", align: "center", margin: 0, isTextBox: true });
    s.addText(b, { x, y: 3.85, w: 2.25, h: 1.6, fontFace: BODY, fontSize: 14, color: C.muted, align: "center", margin: 0, isTextBox: true, valign: "top" });
  });
  s.addText("No towers · No internet · No extra hardware · Tamil and English", { x: 0.6, y: 5.9, w: 12.1, h: 0.5, fontFace: HEAD, fontSize: 18, bold: true, color: C.teal, align: "center", margin: 0, isTextBox: true });
}

// 4 ── The app
{
  const s = base("The app", "Built for the worst day of someone’s life");
  const shots = [["phone-home.png", "Live mesh status and one-tap SOS"], ["phone-situation.png", "Situation report → urgency 81 · Critical"],
    ["phone-countdown.png", "3-second countdown: no false alarms"], ["phone-tamil.png", "Fully in Tamil"]];
  shots.forEach(([f, cap], i) => {
    const x = 0.75 + i * 3.1;
    phone(s, f, x, 1.8, 4.5);
    s.addText(cap, { x: x - 0.2, y: 6.3, w: 3.05, h: 0.6, fontFace: BODY, fontSize: 13.5, color: C.text, align: "center", margin: 0, isTextBox: true, valign: "top" });
  });
}

// 5 ── Control room
{
  const s = base("District control room", "Every SOS on one live map, and help sent back");
  s.addImage({ path: M("browser-dashboard.png"), x: 4.3, y: 1.75, w: 8.6, h: 8.6 * (1246 / 1800), altText: "MeshAlive district control-room dashboard" });
  const pts = [["Sorted by urgency", "Critical cases first, with people count and route.", C.coral],
    ["Dispatch, and the victim knows", "“Rescue team on the way” reaches phones with no signal.", C.green],
    ["Evacuation broadcasts", "In any language, to every phone in the mesh.", C.amber],
    ["Signed commands", "Phones reject anything not signed by the control room.", C.blue]];
  pts.forEach(([t, b, col], i) => {
    const y = 1.95 + i * 1.18;
    s.addShape(pres.shapes.OVAL, { x: 0.6, y: y + 0.08, w: 0.22, h: 0.22, fill: { color: col }, line: { type: "none" } });
    s.addText(t, { x: 1.0, y, w: 3.2, h: 0.4, fontFace: HEAD, fontSize: 15.5, bold: true, color: "FFFFFF", margin: 0, isTextBox: true });
    s.addText(b, { x: 1.0, y: y + 0.42, w: 3.2, h: 0.7, fontFace: BODY, fontSize: 13.5, color: C.muted, margin: 0, isTextBox: true, valign: "top" });
  });
}

// 6 ── Different
{
  const s = base("Why it’s different", "A rescue system, not a chat app");
  const hdr = (t, us) => ({ text: t, options: { bold: true, color: "FFFFFF", fill: { color: us ? "1F1F1F" : "0D0D0D" }, fontFace: HEAD, fontSize: 14 } });
  const row = (a, b, c, d) => [
    { text: a, options: { color: C.text, bold: true } }, { text: b, options: { color: C.muted } }, { text: c, options: { color: C.muted } },
    { text: d, options: { color: "FFFFFF", bold: true, fill: { color: "141414" } } }];
  s.addTable([
    [hdr(""), hdr("Chat apps (Bridgefy, Briar)"), hdr("LoRa radios (Meshtastic, goTenna)"), hdr("MeshAlive", true)],
    row("Reaches rescuers", "No", "No", "Live district dashboard"),
    row("Help back to the victim", "No", "No", "Dispatch + evacuation alerts"),
    row("Prioritises urgent cases", "No", "No", "Triage score 0–100"),
    row("No phone in range", "Message waits", "Message waits", "Rescue carriers carry it out"),
    row("Battery", "Flooding drains phones", "Flooding", "Battery-aware routing"),
    row("Extra hardware", "None", "₹2,500+ per person", "None"),
    row("Language", "English", "English", "Tamil and English"),
  ], { x: 0.6, y: 1.85, w: 12.1, colW: [2.9, 2.9, 3.1, 3.2], fontFace: BODY, fontSize: 14, color: C.text, fill: { color: C.card },
    border: { type: "solid", pt: 0.75, color: C.line }, rowH: 0.56, valign: "middle", margin: [0.06, 0.12, 0.06, 0.12] });
}

// 7 ── How it works
{
  const s = base("Under the hood", "How MeshAlive works");
  const rows = [
    ["Discovery", "Phones find and connect to each other over Bluetooth and Wi-Fi (Google Nearby Connections)."],
    ["Network map", "Each phone shares battery, neighbours and internet status; every phone rebuilds the mesh map."],
    ["Routing", "Battery-aware Dijkstra: link quality + battery penalty + hop cost, one run via a virtual super-node."],
    ["Reliability", "Urgency priority queue, store-and-forward, duplicate filter, hop limit and delivery receipts."],
    ["Carriers & gateways", "Custody transfer to rescue carriers; any phone with signal uploads what it carries."],
    ["Security", "AES-GCM encrypted messages; HMAC-SHA256 signed control-room commands."],
  ];
  rows.forEach(([t, b], i) => {
    const y = 1.85 + i * 0.82;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.6, y, w: 12.1, h: 0.7, rectRadius: 0.1, fill: { color: C.card }, line: { color: C.line, width: 1 } });
    s.addText(t, { x: 0.85, y, w: 3.0, h: 0.7, fontFace: HEAD, fontSize: 15, bold: true, color: C.teal, valign: "middle", margin: 0, isTextBox: true });
    s.addText(b, { x: 3.9, y, w: 8.6, h: 0.7, fontFace: BODY, fontSize: 14.5, color: C.text, valign: "middle", margin: 0, isTextBox: true });
  });
}

// 8 ── Evidence
{
  const s = base("Evidence", "Built, tested and measured");
  const stats = [["100%", "SOS delivered (200-phone simulation)", C.green], ["147×", "fewer radio transmissions than flooding", C.coral],
    ["2×", "battery left on the weakest phone", C.violet], ["49", "automated tests", C.teal]];
  stats.forEach(([n, l, col], i) => {
    const x = 0.6 + i * 3.08;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 1.85, w: 2.88, h: 1.7, rectRadius: 0.15, fill: { color: C.card }, line: { color: C.line, width: 1 } });
    s.addText(n, { x: x + 0.25, y: 1.98, w: 2.4, h: 0.85, fontFace: HEAD, fontSize: 40, bold: true, color: col, margin: 0, isTextBox: true });
    s.addText(l, { x: x + 0.25, y: 2.85, w: 2.45, h: 0.6, fontFace: BODY, fontSize: 13.5, color: C.muted, margin: 0, isTextBox: true, valign: "top" });
  });
  const opts = { x: 0.6, y: 3.85, w: 12.1, h: 2.9, barDir: "col", barGrouping: "clustered", chartColors: [C.coral, C.blue, C.green],
    showTitle: true, title: "Radio transmissions per 20 SOS (log scale not used)", titleColor: "FFFFFF", titleFontFace: "+mn-lt", titleFontSize: 13,
    showValue: true, dataLabelPosition: "outEnd", dataLabelColor: "FFFFFF", dataLabelFontSize: 12, dataLabelFormatCode: "#,##0",
    catAxisLabelColor: "D4D4D4", valAxisLabelColor: "8F8F8F", catAxisLabelFontSize: 12, valAxisLabelFontSize: 11,
    valGridLine: { color: "243049", size: 0.5 }, catGridLine: { style: "none" }, showLegend: false, valAxisHidden: true };
  s.addChart(pres.charts.BAR, [{ name: "Transmissions", labels: ["Flooding", "BFS", "Battery-aware Dijkstra"], values: [14533, 88, 99] }],
    { ...opts, title: "Radio transmissions for 20 SOS on the same 200-phone network", chartColors: [C.coral, C.blue, C.green], invertedColors: [C.coral, C.blue, C.green] });
}

// 9 ── Impact & cost
{
  const s = base("Impact, cost and scale", "Who benefits, what it costs, how it grows");
  phone(s, "phone-planner.png", 0.5, 1.75, 5.0);
  s.addText("District planner (the app’s own routing engine)", { x: 3.7, y: 1.9, w: 9, h: 0.4, fontFace: HEAD, fontSize: 16, bold: true, color: C.teal, margin: 0, isTextBox: true });
  s.addText("1 km² ward, 1,000 app users, 4 people per phone: about 3,700 of 4,000 people can get an SOS to the control room (model, to be validated in the pilot).",
    { x: 3.7, y: 2.35, w: 9, h: 0.9, fontFace: BODY, fontSize: 15, color: C.text, margin: 0, isTextBox: true, valign: "top" });
  const costs = [["Citizens", "₹0", "Free app on their own phone"], ["Rescue carriers", "₹0", "Phones crews already carry"],
    ["Control room", "₹0", "Any browser on an existing PC"], ["Rollout", "DDMA", "Pre-monsoon install & awareness drives"]];
  costs.forEach(([w, c, d], i) => {
    const x = 3.7 + (i % 2) * 4.55, y = 3.45 + Math.floor(i / 2) * 1.55;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: 4.35, h: 1.35, rectRadius: 0.12, fill: { color: C.card }, line: { color: C.line, width: 1 } });
    s.addText(c, { x: x + 0.25, y: y + 0.15, w: 1.6, h: 0.6, fontFace: HEAD, fontSize: 24, bold: true, color: C.green, margin: 0, isTextBox: true });
    s.addText(w, { x: x + 1.8, y: y + 0.2, w: 2.4, h: 0.45, fontFace: HEAD, fontSize: 15, bold: true, color: "FFFFFF", margin: 0, isTextBox: true });
    s.addText(d, { x: x + 0.25, y: y + 0.8, w: 3.9, h: 0.45, fontFace: BODY, fontSize: 13.5, color: C.muted, margin: 0, isTextBox: true });
  });
}

// 10 ── Roadmap
{
  const s = base("Path to deployment", "From one ward to every district");
  const ph = [["Phase 1 · 3 months", "Field pilot", "One flood-prone ward with the DDMA, NDRF/SDRF boats as carriers, dashboard in the district EOC. Measure delivery rate and SOS-to-dispatch time.", C.green],
    ["Phase 2", "District rollout", "Pre-monsoon install drives via schools, colleges, panchayats, ULBs and SHGs, guided by the planner’s adoption targets.", C.violet],
    ["Phase 3", "State & national", "Distribution through the SDMA; state EOC integration; alerts aligned with the national Common Alerting Protocol; more Indian languages.", C.coral]];
  ph.forEach(([t, h, b, col], i) => {
    const x = 0.6 + i * 4.1;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 1.9, w: 3.9, h: 4.4, rectRadius: 0.15, fill: { color: C.card }, line: { color: C.line, width: 1 } });
    s.addText(t.toUpperCase(), { x: x + 0.3, y: 2.15, w: 3.3, h: 0.35, fontFace: BODY, fontSize: 12, bold: true, color: col, charSpacing: 2, margin: 0, isTextBox: true });
    s.addText(h, { x: x + 0.3, y: 2.55, w: 3.3, h: 0.55, fontFace: HEAD, fontSize: 21, bold: true, color: "FFFFFF", margin: 0, isTextBox: true });
    s.addText(b, { x: x + 0.3, y: 3.2, w: 3.3, h: 2.9, fontFace: BODY, fontSize: 15, color: C.muted, margin: 0, isTextBox: true, valign: "top" });
  });
}

// 11 ── Ask
{
  const s = base("What we need", "Help us take MeshAlive to the field", { bg: C.bg2 });
  const asks = ["Pilot partnership with a district administration and its DDMA", "NDRF/SDRF and boat operators to field-test carrier mode",
    "Mentorship in disaster operations and public-sector deployment", "Guidance on security review and Common Alerting Protocol integration",
    "Pilot funding: alert server, test devices, awareness drives", "IP guidance and incubation support"];
  asks.forEach((a, i) => {
    const x = 0.6 + (i % 2) * 6.1, y = 1.95 + Math.floor(i / 2) * 1.05;
    s.addShape(pres.shapes.OVAL, { x, y: y + 0.12, w: 0.22, h: 0.22, fill: { color: C.coral }, line: { type: "none" } });
    s.addText(a, { x: x + 0.4, y, w: 5.5, h: 0.8, fontFace: BODY, fontSize: 16, color: C.text, margin: 0, isTextBox: true, valign: "top" });
  });
  s.addText([
    { text: "MeshAlive: because when the network fails, help shouldn’t.", options: { bold: true, color: "FFFFFF", fontSize: 22, fontFace: HEAD, breakLine: true } },
    { text: "meshalive.benzo.co.in  ·  github.com/yogeshwardev/MESHALIVE-", options: { color: C.teal, fontSize: 15, fontFace: BODY } },
  ], { x: 0.6, y: 5.35, w: 12.1, h: 1.3, margin: 0.25, isTextBox: true, valign: "middle",
    shape: pres.shapes.ROUNDED_RECTANGLE, rectRadius: 0.15, fill: { color: C.card }, line: { color: C.line, width: 1 } });
}

pres.writeFile({ fileName: OUT }).then((f) => console.log("saved", f));
