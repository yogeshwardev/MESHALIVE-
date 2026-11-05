"""Generates one narration MP3 per scene with sentence timings (edge-tts, Indian English)."""
import asyncio
import json
import sys
from pathlib import Path

import edge_tts

sys.path.insert(0, str(Path(__file__).parent))
from script import SCENES  # noqa: E402

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
VOICE = sys.argv[2] if len(sys.argv) > 2 else "en-IN-NeerjaExpressiveNeural"


async def one(name, text):
    com = edge_tts.Communicate(text, VOICE, rate="+0%", boundary="SentenceBoundary")
    sents = []
    with open(OUT / f"{name}.mp3", "wb") as f:
        async for chunk in com.stream():
            if chunk["type"] == "audio":
                f.write(chunk["data"])
            elif chunk["type"] == "SentenceBoundary":
                sents.append({"start": chunk["offset"] / 1e7, "end": (chunk["offset"] + chunk["duration"]) / 1e7, "text": chunk["text"]})
    return sents


async def main():
    timings = {}
    for name, text in SCENES:
        timings[name] = await one(name, text)
        print(name, len(timings[name]), "sentences")
    (OUT / "timings.json").write_text(json.dumps(timings, indent=1, ensure_ascii=False), encoding="utf-8")


asyncio.run(main())
