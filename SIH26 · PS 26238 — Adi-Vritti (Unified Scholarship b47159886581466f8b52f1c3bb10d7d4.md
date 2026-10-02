cd# SIH26 · PS 26238 — Adi-Vritti (Unified Scholarship App for Tribal Students)

<aside>
🎯

**The wedge:** most of the 42 teams will build the same thing — five scheme cards, a status timeline, a chatbot. Screens are the easy 20%. Win on the layer *underneath*: a unified scholar identity, and a claim-based verification layer that issues **reusable signed attestations** instead of re-checking the same certificate every year. That is exactly what MoTA's reform assessment asks for, and almost nobody will build it.

</aside>

## Name: Adi-Vritti (आदि-वृत्ति)

**Adi** is MoTA's live product family — Adi Vaani, AdiKosh, Adi Karmayogi, Adi Adarsh Gram Yojana. **Vritti** (वृत्ति) is the root of छात्रवृत्ति, scholarship. The name reads as MoTA's natural next product, not a hackathon toy.

**Tagline:** One student. One identity. Five schemes.
**Alternates:** Adi Setu · JAGO Scholar · EkVritti

## The PS, decoded

Three stated asks, and one hidden ask that decides the winner.

| Stated ask | The real engineering problem | What most teams will do |
| --- | --- | --- |
| Unified dashboard across 5 schemes | The three systems have **no common key** for the same human. NSP has OTR, SFMP has a scholar ID, NOS has its own registration. You must *resolve identity* before you can unify anything. | Hardcode one student's fake data into five cards |
| JAGO chatbot integration | JAGO already exists and is live on [tribal.nic.in](http://tribal.nic.in) in EN/HI/TE/TA. The job is not to build a chatbot — it is to give JAGO **student-specific tools** and tribal-language voice. | Build a second, competing RAG chatbot |
| Unified verification & integration layer | A claim-based orchestrator with tiered fallbacks, provenance records, and an exception queue that never blocks the applicant. | Show a "DigiLocker" button that fills a form |
| *Hidden:* identify enrolled-but-unbenefited ST students | A **privacy-preserving cross-ministry join** (MoTA ↔ UDISE+/APAAR) that produces school-level action lists without a bulk PII transfer. | Skip it — it is the hardest and highest-scoring part |

## The nine pillars

| # | Pillar | Why it wins |
| --- | --- | --- |
| 1 | **Unified Scholar ID (USID)** — deterministic-first, probabilistic-fallback, human-adjudicated identity resolution | The foundation. Also catches the duplicate payments CAG found. |
| 2 | **Verified Claims Wallet** — verify once, reuse for years, with validity windows | Delivers "reuse of previously submitted information" literally. Zero-document renewal. |
| 3 | **Verification Orchestrator** — one claim API, 4 tiers of strategy, signed provenance | The PS's central ask, built as real architecture. |
| 4 | **Eligibility & Conflict Engine** — rules as versioned data, not code | MoTA changes an income ceiling without a release. |
| 5 | **SLA + deficiency loop** — shows which actor is sitting on your file, and for how long | Turns opacity into accountability. |
| 6 | **DBT Failure Doctor** — decodes PFMS/SFMP rejection codes into fix steps | The last mile that actually fails. Nobody else will think of this. |
| 7 | **JAGO+ scholarship skill** — tool-calling, templated status answers, Bhashini + Adi Vaani voice | Grounded, non-hallucinating, and speaks Santali/Gondi/Bhili/Mundari. |
| 8 | **Coverage Gap engine** — hashed-key join against UDISE+/APAAR → block-level outreach lists | The Ministry-facing impact story. |
| 9 | **DPDP-native consent** — guardian-linked family accounts, purpose-bound consent artefacts | Pre-Matric students are minors, so this is legally mandatory — and it happens to be the exact feature the PS asks for ("a family with children across schemes"). |

## The numbers to put on the impact slide

- **2.43 crore** ST students enrolled, pre-primary to higher secondary (UDISE+ 2024-25).
- **35–55%** of eligible ST students not covered by Pre-Matric, and **30–42%** not covered by Post-Matric, in the areas CAG audited (2019-20 to 2022-23).
- **46%** of sampled schools did not avail the Pre-Matric scheme at all.
- **28%** of 9,076 audited applications were approved *without* the required income or community certificate — the verification layer is not a nice-to-have.
- **5** schemes across **3** disconnected portals; **20** NOS awards a year (17 ST + 3 PVTG), income ceiling ₹6 lakh.
- PFMS is the common DBT rail for all of it.

<aside>
💡

**A correction we caught — use this in the pitch.** The PS says a student "can avail only one scholarship/fellowship scheme at a time." NSP's current student notice says that **from AY 2026-27 a student may apply for one merit-based scheme *and* one or more welfare-based schemes.** The rule is changing. So we model scheme compatibility as a **versioned policy matrix stored as data**, not as an if-statement. Saying this out loud tells judges we read the actual portals, not just the PS text.

</aside>

## Read next

1. **Solution Blueprint** — architecture, every layer, data model, integration honesty matrix
2. **Build Plan** — stack, repo, contracts, synthetic data, pre-finale weeks + the 36-hour runbook
3. **Claude Code Agent Plan** — [CLAUDE.md](http://CLAUDE.md), subagents, worktrees, the ordered prompt playbook
4. **Pitch & Judging Kit** — SIH 2026 criteria mapping, PPT outline, judge Q&A, what not to claim

## Sources

- SIH 2026 Guidelines (evaluation criteria): [https://sih.gov.in/letters/2026/SIH%202026%20Guidelines.pdf](https://sih.gov.in/letters/2026/SIH%202026%20Guidelines.pdf)
- MoTA Scholarship & DBT: [https://tribal.nic.in/Scholarship.aspx](https://tribal.nic.in/Scholarship.aspx)
- NSP student portal / OTR rules: [https://scholarships.gov.in/Students](https://scholarships.gov.in/Students)
- SFMP (Canara Bank): [https://scholarship.canarabank.in/](https://scholarship.canarabank.in/)
- NOS Portal (MoTA): [https://overseas.tribal.gov.in/](https://overseas.tribal.gov.in/)
- API Setu + DigiLocker sandbox: [https://sandbox.api-setu.in/](https://sandbox.api-setu.in/) · [https://apisetu.gov.in/digilocker](https://apisetu.gov.in/digilocker)
- Adi Vaani (MoTA tribal-language AI): [https://adivaani.tribal.gov.in/](https://adivaani.tribal.gov.in/)
- Bhashini services: [https://bhashini.gov.in/](https://bhashini.gov.in/)
- APAAR / UDISE+ SDMS: [https://apaar.education.gov.in/](https://apaar.education.gov.in/) · [https://sdms.udiseplus.gov.in/](https://sdms.udiseplus.gov.in/)
- UDISE+ 2024-25 enrolment booklet: [https://dashboard.udiseplus.gov.in/report2026/static/media/UDISE+2024_25_Booklet_existing.118ba29d4773e6372f72.pdf](https://dashboard.udiseplus.gov.in/report2026/static/media/UDISE+2024_25_Booklet_existing.118ba29d4773e6372f72.pdf)
- CAG audit on Pre/Post-Matric coverage gaps: [https://cag.gov.in/uploads/download_audit_report/2025/7-Chapter-II-06a9ff835cc8ab2.95281229.pdf](https://cag.gov.in/uploads/download_audit_report/2025/7-Chapter-II-06a9ff835cc8ab2.95281229.pdf)

[3 · Claude Code Agent Plan](https://app.notion.com/p/3-Claude-Code-Agent-Plan-be7b9f12090742a2a34cfe829ef5c2d3?pvs=21)

[4 · Pitch & Judging Kit](https://app.notion.com/p/4-Pitch-Judging-Kit-5c1363aa5f4c4152a18e1ac16231c46a?pvs=21)

[1 · Solution Blueprint](https://app.notion.com/p/1-Solution-Blueprint-136ed2ca58284b92a7aefcb975088263?pvs=21)

[2 · Build Plan — stack, sprints, demo](https://app.notion.com/p/2-Build-Plan-stack-sprints-demo-a3deb65604ca43408463e99c63018ff0?pvs=21)