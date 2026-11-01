`quick-brown-fox.pcm` is synthetic English speech saying:

> The quick brown fox jumps over the lazy dog.

Generated locally with macOS `say -v Samantha`, converted with ffmpeg to
16,000 Hz mono signed 16-bit little-endian PCM, with no container header.
It is used only by the opt-in real-recognizer test, never by the installed app.
The test feeds a two-second silence tail separately. This is not live-mic or
natural-speech accuracy evidence.
