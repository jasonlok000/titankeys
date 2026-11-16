Redone with the owner's definitions:

- **Mistap:** you didn't mean to type, nothing was typed, but the touchpad vibrated.
- **Mistype:** you didn't mean to type, but a word was typed anyway (deleted within 5 s, nothing kept in its place).
- **Wrong word:** you meant to swipe and got the wrong word. That's engine accuracy (#31).

To find which touches vibrated, I replayed the last day and a half of logs (8,664 touchpad contacts) through the keyboard's own touch code with its tick rule.

**Mistaps:** about 8 per 100 swipes. With tap keys off, the only touchpad vibration that can come without typing is the swipe tick. It fired at 0.2 keys of movement, but a touch only counts as a swipe at 0.6 keys. 9 in 10 mistaps ticked before that point. About half were a finger sliding on the pad while pushing a key. Touch size does not separate them from real swipes; the distance moved does.

Outcomes:

1. **The first tick now waits for 0.6 keys**, the same distance that makes a swipe. In the logs this silences about 6 in 10 mistaps, including every touch read as a tap. Nothing that gets typed changes. A real swipe's first tick comes about 19 ms later, and about 1% of real swipes lose their ticks. This goes in 2.1.
2. **The size rule (step 6+, or landing 2+ over usual) is parked again.** It catches nothing in real use, either for vibrations or for unwanted words. It only catches the exaggerated resting thumbs of the recording.
3. **Mistypes (about 5 per 100 swipes): nothing for now.** Neither size nor the pause before a swipe catches them without losing more real words. One word backspace is already the cheap undo.

Leaving this open.
