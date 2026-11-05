"""MeshAlive prototype video: narration script (one entry per scene)."""

SCENES = [
    ("title",
     "When cyclones and floods take down mobile towers, people in danger cannot call for help. "
     "MeshAlive changes that."),
    ("problem",
     "In the 2015 Chennai floods, and again during Cyclone Michaung, whole neighbourhoods lost signal for days. "
     "Families were trapped, and control rooms could not see who needed help, or where."),
    ("setup",
     "MeshAlive is a free Android app. Setup takes thirty seconds. "
     "Choose English or Tamil, add your household, and pick your role."),
    ("home",
     "With no towers and no internet, the phone keeps looking for other phones nearby, "
     "over Bluetooth and Wi-Fi."),
    ("sos",
     "In an emergency, one tap sends an SOS. It carries your location and a short situation report: "
     "how many people, who is trapped or elderly, and how high the water is. "
     "The app turns this into an urgency score. Here, eighty-one. Critical. "
     "A three-second countdown with Cancel prevents false alarms."),
    ("tamil",
     "The complete SOS flow also works in Tamil."),
    ("mesh",
     "The SOS hops from phone to phone. Battery-aware routing avoids phones that are about to die, "
     "and the most urgent messages go first. "
     "If there is no route at all, a rescue boat's phone picks up the message, and carries it out of the dead zone."),
    ("dashboard",
     "The first phone that finds signal uploads everything to the district control room. "
     "Every SOS appears on a live map, sorted by urgency. "
     "The officer dispatches a rescue team, and the reply travels back through the mesh, "
     "so the family sees: rescue team on the way, even without signal. "
     "Evacuation alerts reach every phone the same way, and every command is digitally signed, "
     "so fake orders are rejected."),
    ("evidence",
     "In a two-hundred-phone simulation, MeshAlive delivered every SOS, "
     "with a hundred and forty-seven times fewer radio transmissions than flooding. "
     "And the built-in planner shows how many installs each ward needs."),
    ("close",
     "No towers. No new hardware. Zero cost to citizens. "
     "MeshAlive. Because when the network fails, help shouldn't."),
]
