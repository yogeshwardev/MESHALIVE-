"""MeshAlive prototype video: narration script (one entry per scene), written to be spoken."""

SCENES = [
    ("title",
     "Imagine a cyclone has just hit your city. The towers are down, there's no signal, "
     "and a family is stuck on their roof. They can't call anyone. "
     "That's the problem MeshAlive solves."),
    ("problem",
     "We've seen this before. In the 2015 Chennai floods, and again during Cyclone Michaung, "
     "whole areas lost signal for days. People couldn't ask for help, "
     "and control rooms simply couldn't see who needed it, or where."),
    ("setup",
     "MeshAlive is a free Android app, and setting it up takes about thirty seconds. "
     "You pick English or Tamil, add a few details about your household, and choose your role. That's it."),
    ("home",
     "From then on, even with no towers and no internet, your phone keeps quietly looking "
     "for other phones nearby, over Bluetooth and Wi-Fi."),
    ("sos",
     "When something goes wrong, you just tap SOS. "
     "Your message carries your location, along with a quick picture of your situation: "
     "how many people are with you, whether someone's trapped or elderly, and how high the water has risen. "
     "The app turns all of that into an urgency score. This family scores eighty-one, which is critical. "
     "And to avoid false alarms, there's a three-second countdown, so you can cancel if you tapped by mistake."),
    ("tamil",
     "And yes, the whole thing works in Tamil too."),
    ("mesh",
     "Now, here's the clever part. Your SOS hops from one phone to the next. "
     "It avoids phones that are running low on battery, and the most urgent messages always go first. "
     "But what if there's no phone nearby at all? "
     "Then a rescue boat's phone picks up the message, and carries it out to where there's signal."),
    ("dashboard",
     "As soon as any phone finds signal, everything reaches the district control room. "
     "Every SOS shows up on a live map, with the most urgent ones on top. "
     "The officer sends a rescue team with one click, and that reply travels back through the same mesh. "
     "So the family sees, rescue team on the way, even without any signal. "
     "Evacuation alerts reach everyone the same way. "
     "And every message from the control room is digitally signed, so nobody can send a fake order."),
    ("evidence",
     "And we've tested it. In a simulation with two hundred phones, MeshAlive delivered every single SOS, "
     "using a hundred and forty-seven times fewer radio transmissions than simple flooding. "
     "A built-in planner even tells each ward how many people need the app."),
    ("close",
     "No towers. No extra hardware. Nothing for citizens to pay. "
     "MeshAlive. Because when the network fails, help shouldn't."),
]

# shot changes inside a scene: cut when the sentence starting with this text begins
CUTS = {"sos": "And to avoid false alarms", "mesh": "But what if"}
