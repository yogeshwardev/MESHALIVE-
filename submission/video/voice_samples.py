"""Short voice samples of the same conversational passage, to pick the narrator."""
import asyncio
import sys
from pathlib import Path

import edge_tts

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
TEXT = ("Imagine a cyclone has just hit Chennai. The towers are down, there's no signal, "
        "and a family is stuck on their roof. That's exactly when MeshAlive steps in. "
        "One tap on SOS, and the phone quietly passes the message to the next phone, and the next, "
        "until it reaches someone who can help.")
VOICES = [
    ("05_Emma_cheerful_female", "en-US-EmmaMultilingualNeural", "+0%", "+0Hz"),
    ("06_Jenny_warm_female", "en-US-JennyNeural", "+0%", "+0Hz"),
    ("07_Michelle_pleasant_female", "en-US-MichelleNeural", "+0%", "+0Hz"),
    ("08_Brian_casual_male", "en-US-BrianMultilingualNeural", "+0%", "+0Hz"),
    ("09_Libby_British_female", "en-GB-LibbyNeural", "+0%", "+0Hz"),
    ("10_Natasha_Australian_female", "en-AU-NatashaNeural", "+0%", "+0Hz"),
    ("11_Swara_Indian_female", "hi-IN-SwaraNeural", "+0%", "+0Hz"),
    ("12_Pallavi_Tamil_Indian_female", "ta-IN-PallaviNeural", "+0%", "+0Hz"),
    ("13_Madhur_Indian_male", "hi-IN-MadhurNeural", "+0%", "+0Hz"),
    ("14_Neerja_brighter_Indian_female", "en-IN-NeerjaExpressiveNeural", "+6%", "+4Hz"),
]


async def main():
    for name, voice, rate, pitch in VOICES:
        await edge_tts.Communicate(TEXT, voice, rate=rate, pitch=pitch).save(str(OUT / f"{name}.mp3"))
        print(name)


asyncio.run(main())
