# SIH Demo Video Pack — Adi-Vritti (PS 26238)

> Principle: **Higgsfield makes the cinema, the screen-recording makes the proof.**
> Never ask AI video to render app UI (text comes out garbled and judges spot it).
> Structure: AI intro → real app demo on phone viewport → AI outro.
> Total runtime: 5 minutes (~680 spoken words @ ~135 wpm).

Canonical 5-min path: `docs/specs/demo-path.md` (6 beats, WIFI OFF except DigiLocker beat).
In-app guided rails: officer-web `?demo=1` → `DemoTour` (3 judge-sized beats).

---

## Part 1 — Higgsfield prompt pack (copy-paste ready)

**Global settings (set once, reuse for every clip):**

- Aspect ratio **9:16 vertical**, 1080p, 5–10s per clip
- Workflow: **image-to-video** (generate a still first, then animate — more consistent faces)
- Same character reference (Soul ID / reference slot) for the student in all clips
- Generate **silent** — voiceover is laid in the editor, not in AI audio
- Add ALL titles/captions in the editor (CapCut/Premiere), never in the prompt

**STYLE LOCK — append to every prompt:**

```text
cinematic photorealism, warm morning light, rural central India (Madhya Pradesh), teal-and-amber grade, shallow depth of field, subtle 35mm film grain, no text, no watermark, no logos
```

**Negative prompt (every clip):**

```text
blurry faces, distorted hands, extra fingers, readable text, phone UI screens, Aadhaar logo, government emblems, watermark, cartoon, oversaturated
```

### The 7 clips

```text
H1 — HOOK / INTRO (0:00–0:12)
A 12-year-old tribal schoolgirl in navy-blue uniform walks down a dusty village
path toward a small school at sunrise, books hugged to her chest, other children
behind her. FPV drone sweep: camera glides low past her, then rises to reveal
the school and hills. She looks at the camera and smiles. Camera: FPV drone,
fast then smooth. Action: girl smiles as camera passes.
```

```text
H2 — THE PROBLEM (0:15–0:30, under voiceover)
Dim government office interior. Slow dolly-in across a desk buried in paper
ledgers, stamped forms, and a dusty CRT monitor. A clerk's hands flip pages,
searching. Dust particles float in a shaft of window light. Camera: slow dolly
in. Mood: heavy, stuck, bureaucratic.
```

```text
H3 — ONE IDENTITY (1:00–1:30, under voiceover)
Extreme macro of a fingertip touching a glowing ID-card scanner. Golden light
ripples outward from the touchpoint; scattered paper records in the background
dissolve into particles of light that converge into one glowing card. Generic
biometric card, NO real logos. Camera: macro push-in, then slow orbit.
```

```text
H4 — VERIFY ONCE (1:45–2:15, under voiceover)
Close-up of a student's hands holding a smartphone in a sunlit classroom.
A soft green checkmark glow blooms from the screen and washes over the room;
classmates look up and smile. Screen stays blurred abstract glow, no readable
UI. Camera: gentle arc left around the phone.
```

```text
H5 — MONEY REACHES HOME (2:45–3:15, under voiceover)
A tribal mother outside a rural bank branch looks at her basic phone and
breaks into a relieved smile; warm light. Shallow-focus bank signboard blurred
beyond readability in background. Rupee coins motif as soft golden bokeh, NOT
literal falling money. Camera: slow push-in on her smile.
```

```text
H6 — THE OFFICER (3:45–4:15, under voiceover)
Young district officer at a wooden desk at dusk, laptop open with a blurred
abstract dashboard glow (indigo and emerald, no readable text). She taps once,
leans back satisfied. Warm desk lamp vs cool blue window light. Camera: dolly
right tracking across the desk.
```

```text
H7 — OUTRO (4:45–5:00)
Wide crane-up shot: sunlit classroom full of tribal students in uniform raising
their hands eagerly; the girl from H1 (same face, same uniform) turns and
smiles at camera holding a tablet. Camera: crane up and back. Resolve on warm,
triumphant stillness.
```

**Assembly notes:**

- Reuse H1's girl as the reference image for H4 and H7 — judges track one child.
- Higgsfield prompt formula per clip: subject + action + setting + camera preset
  (Dolly / Pan / FPV / Bullet Time / Crane). Prefer the Camera Control preset
  parameter over describing the move in prose.
- Quality gate per clip: identity stable, hands intact, no legible text, no logos.

---

## Part 2 — Recording setup (night before)

1. **Stack:** `make dev` (full stack, foreground). Open the officer-web URL it prints.
   Confirm the 3 hero cards load with numbers.
2. **Phone viewport:** DevTools → device toolbar → **390×844**, re-check **360×740**
   (no-horizontal-scroll baseline). Record the browser window, not full screen.
3. **Guided rails:** append `?demo=1` to the dashboard URL — the Judge demo tour
   card appears with 3 beats + "Take me there →". Rehearse it twice.
4. **Two-pass recording:** Pass 1 = screen only, silent, slow deliberate mouse.
   Pass 2 = voiceover separately on a phone mic in a quiet room. Clean audio
   matters more than anything.
5. **WIFI OFF rehearsal:** demo runs offline except the DigiLocker pull — know
   which beat needs network. Ask Adi still answers from its offline FAQ mirror
   if the backend drops.
6. **Seed demo data:** Mandla district loaded; one STP-eligible file at top of the
   exceptions queue; one E001 stuck payment ready. Never search live.

---

## Part 3 — 5-minute speech (word-for-word, ~680 words)

Pace: unhurried, ~135 wpm. Pause at every **[click]** — let the screen settle.

**[0:00–0:12 · H1 plays, title card: "ADI-VRITTI — One student. One identity. Five schemes."]**

> "This is the walk lakhs of tribal students take every morning. And this is the
> walk their scholarships never complete. I'm [name], Team [name], and in the
> next five minutes I'll show you Adi-Vritti — built for problem statement 26238."

**[0:12–0:45 · BEAT 1, ON SCREEN: H2 under voiceover, then coverage-gap map]**

> "Two point four three crore ST students are enrolled. Thirty-five to
> fifty-five percent never appear on any scholarship portal. Three disconnected
> systems — and the gap lives at the school level. **[click]** This is Mandla
> district. Government High School, Bichhiya: forty-seven ST students in Class
> Nine… six applications. That missing forty-one — that is our problem
> statement, on one screen."

**[0:45–1:30 · BEAT 2, ON SCREEN: identity page / resolve API]**

> "Why do they go missing? Because Sunita Meena on NSP and Sunita K. Mina on
> SFMP are the same girl — and every portal thinks she's two people, or none.
> **[click]** Our USID resolver links both records into one identity, shows its
> confidence — and flags the duplicate beneficiary trying to claim twice. One
> student. One identity. Five schemes."

**[1:30–2:30 · BEAT 3, ON SCREEN: verification flow, verified badge, second scheme]**

> "Then we verify once — and reuse everywhere. **[click]** A DigiLocker pull
> confirms her income claim… verified. That same claim now satisfies a second
> scheme automatically — she never uploads the same certificate twice. And watch
> what happens on a name mismatch: **[click]** the system raises an actionable
> deficiency — it does NOT block her application. Help, not rejection. That's a
> design choice, and we're proud of it."

**[2:30–3:30 · BEAT 4, ON SCREEN: H5 under first lines, then disbursements page, E001 fix, Ask Adi]**

> "But the cruelest gap is money sanctioned and never received. **[click]** This
> payment failed with E001 — Aadhaar not seeded — and instead of a dead end, the
> student gets the fix: which branch, what to carry, what to say. Every stuck
> payment shows its fix. And when she asks why — **[click]** — Ask Adi answers,
> grounded only in real tool output. It can never invent a status, because
> status is template-filled from the system, never generated. Let me ask in
> Hindi — **[type/speak: 'मेरा पेमेंट क्यों रुका है?']** — same grounded answer,
> in her language."

**[3:30–4:30 · BEAT 5, ON SCREEN: H6 under first lines, then exceptions queue, one-click approve]**

> "Now the officer's side. Hundreds of files — so the queue sorts itself: most
> overdue first, and files marked Ready to approve clear in one tap. **[click]**
> That file was auto-verified straight-through — STP — the officer just
> confirms. Thirty seconds a file. Multiply by every district in India."

**[4:30–5:00 · BEAT 6, ON SCREEN: H7, integration matrix, team + PS number card]**

> "And we're integration-ready: DigiLocker live via sandbox; NSP, PFMS, Bhashini
> contract-ready behind simulators. The entire demo you just saw runs offline.
> One student, one identity, five schemes — Adi-Vritti. Thank you."

---

## Part 4 — Delivery tips

- Stand while recording VO — voice carries more authority.
- Smile on "we're proud of it" and the Hindi line — judges remember the human
  moment, not the architecture.
- End ~10s early — finishing under time signals preparation.
- If anything fails live: "this is exactly why we built the offline mirror" —
  recovery impresses more than perfection.
