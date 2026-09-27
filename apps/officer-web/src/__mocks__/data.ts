import type {
  DashboardMetrics,
  DisbursementRecord,
  ExceptionItem,
  FailureBreakdown,
  StudentDashboard,
  WalletClaim,
  ChatMessage,
} from "@/types";

export const dashboardMetrics: DashboardMetrics = {
  totalApplications: 18432,
  stpRate: 68.4,
  pendingReview: 187,
  paymentSuccessRate: 91.2,
  schemeBreakdown: [
    { scheme: "Pre-Matric", count: 7210 },
    { scheme: "Post-Matric", count: 6844 },
    { scheme: "Top Class", count: 1930 },
    { scheme: "NFST", count: 412 },
    { scheme: "NOS", count: 68 },
  ],
  disbursementTrend: [
    { month: "Mar", sanctionedPaise: 482000000, paidPaise: 431000000 },
    { month: "Apr", sanctionedPaise: 511000000, paidPaise: 468000000 },
    { month: "May", sanctionedPaise: 498000000, paidPaise: 459000000 },
    { month: "Jun", sanctionedPaise: 546000000, paidPaise: 502000000 },
    { month: "Jul", sanctionedPaise: 589000000, paidPaise: 541000000 },
    { month: "Aug", sanctionedPaise: 624000000, paidPaise: 578000000 },
  ],
  totalStudents: 2430000,
  totalApplied: 1340000,
};

export const recentExceptionsPreview: Pick<
  ExceptionItem,
  "id" | "studentName" | "scheme" | "slaElapsedDays" | "slaLimitDays"
>[] = [
  { id: "APP-90412", studentName: "Sunita Meena", scheme: "Post-Matric", slaElapsedDays: 11, slaLimitDays: 7 },
  { id: "APP-90388", studentName: "Birsa Munda", scheme: "Pre-Matric", slaElapsedDays: 6, slaLimitDays: 7 },
  { id: "APP-90351", studentName: "Mina Rathwa", scheme: "Top Class", slaElapsedDays: 3, slaLimitDays: 7 },
  { id: "APP-90302", studentName: "Somaru Gond", scheme: "NFST", slaElapsedDays: 9, slaLimitDays: 7 },
  { id: "APP-90277", studentName: "Jemma Tudu", scheme: "Post-Matric", slaElapsedDays: 2, slaLimitDays: 7 },
];

export const exceptionQueue: ExceptionItem[] = [
  {
    id: "APP-90412",
    studentName: "Sunita Meena",
    usidLast8: "A91F04C2",
    aadhaarRefLast4: "4821",
    scheme: "Post-Matric",
    stage: "district_nodal",
    slaElapsedDays: 11,
    slaLimitDays: 7,
    riskScore: 82,
    stpScore: 91,
    claims: [
      { label: "ST Certificate", status: "gov-verified", detail: "Verified via state portal on 2026-08-02" },
      { label: "Income Certificate", status: "gov-verified", detail: "Verified via state portal on 2026-08-02" },
      { label: "Bank Account", status: "pending-review", detail: "Name token differs: SUNITA MEENA vs SUNITA MINA" },
    ],
    justification:
      "All Tier-1 claims verified. Income ₹2,40,000 below Post-Matric ceiling. Bank name mismatch is minor transliteration variant — eligible for STP approve with advisory.",
    updatedAt: "2026-09-20T10:30:00+05:30",
  },
  {
    id: "APP-90388",
    studentName: "Birsa Munda",
    usidLast8: "77C2B910",
    aadhaarRefLast4: "1190",
    scheme: "Pre-Matric",
    stage: "institute_verification",
    slaElapsedDays: 6,
    slaLimitDays: 7,
    riskScore: 44,
    stpScore: 76,
    claims: [
      { label: "ST Certificate", status: "gov-verified", detail: "Verified via state portal on 2026-08-28" },
      { label: "Enrolment", status: "corroborated", detail: "UDISE+ and institute record agree" },
      { label: "Income Certificate", status: "pending-review", detail: "FY 2026-27 certificate awaited" },
    ],
    justification:
      "Guardian-linked minor account. Income claim pending refetch from DigiLocker. Route to assisted review once certificate arrives.",
    updatedAt: "2026-09-24T09:12:00+05:30",
  },
  {
    id: "APP-90351",
    studentName: "Mina Rathwa",
    usidLast8: "C04D88E1",
    aadhaarRefLast4: "6634",
    scheme: "Top Class",
    stage: "state_dept",
    slaElapsedDays: 3,
    slaLimitDays: 7,
    riskScore: 21,
    stpScore: 96,
    claims: [
      { label: "ST Certificate", status: "gov-verified", detail: "Verified on 2026-07-30" },
      { label: "Academic Record", status: "gov-verified", detail: "NTA/UGC record corroborated" },
      { label: "Income Certificate", status: "gov-verified", detail: "Verified on 2026-07-30" },
    ],
    justification:
      "Clean STP candidate. All Tier-1 verified, rules pass for AY 2026-27. Recommend one-click approve.",
    updatedAt: "2026-09-25T14:02:00+05:30",
  },
  {
    id: "APP-90302",
    studentName: "Somaru Gond",
    usidLast8: "19ABF377",
    aadhaarRefLast4: "9082",
    scheme: "NFST",
    stage: "ministry",
    slaElapsedDays: 9,
    slaLimitDays: 7,
    riskScore: 67,
    stpScore: 58,
    claims: [
      { label: "NET/JRF", status: "assisted", detail: "Doc AI parsed scorecard, tamper signals clean" },
      { label: "Enrolment", status: "gov-verified", detail: "University record verified" },
      { label: "Bank Account", status: "pending-review", detail: "IFSC changed after bank merger" },
    ],
    justification:
      "Fellowship eligible. Disbursement held on stale IFSC. Fix is one-tap IFSC update, then approve.",
    updatedAt: "2026-09-22T11:45:00+05:30",
  },
  {
    id: "APP-90277",
    studentName: "Jemma Tudu",
    usidLast8: "E5D201AC",
    aadhaarRefLast4: "3355",
    scheme: "Post-Matric",
    stage: "submitted",
    slaElapsedDays: 2,
    slaLimitDays: 7,
    riskScore: 18,
    stpScore: 88,
    claims: [
      { label: "ST Certificate", status: "gov-verified", detail: "Verified on 2026-09-01" },
      { label: "Domicile", status: "corroborated", detail: "Two systems agree" },
      { label: "Income Certificate", status: "gov-verified", detail: "Verified on 2026-09-01" },
    ],
    justification: "Fresh file, on track. No officer action needed yet.",
    updatedAt: "2026-09-26T08:20:00+05:30",
  },
];

export const disbursementSummary = {
  sanctionedPaise: 6240000000,
  paidPaise: 5780000000,
  pendingPaise: 312000000,
  failedPaise: 148000000,
  failedCount: 900,
};

export const failureBreakdown: FailureBreakdown[] = [
  { category: "Aadhaar not seeded", count: 268, severity: "high" },
  { category: "Account dormant", count: 141, severity: "medium" },
  { category: "IFSC changed", count: 122, severity: "medium" },
  { category: "Name mismatch", count: 118, severity: "medium" },
  { category: "Funds not released", count: 96, severity: "high" },
  { category: "Other", count: 155, severity: "low" },
];

export const disbursementRecords: DisbursementRecord[] = [
  {
    id: "DBT-77121",
    studentName: "Sunita Meena",
    usidLast8: "A91F04C2",
    scheme: "Post-Matric",
    amountPaise: 1240000,
    status: "failed",
    failureCode: "REJ-AADHAAR-01",
    failureReason: "Aadhaar not seeded with bank account (NPCI mapper)",
    fix: "Visit nearest bank branch with Aadhaar reference slip; seeding takes ~24h, then retry.",
    district: "Mandla",
    updatedAt: "2026-09-18T10:00:00+05:30",
  },
  {
    id: "DBT-77098",
    studentName: "Somaru Gond",
    usidLast8: "19ABF377",
    scheme: "NFST",
    amountPaise: 3100000,
    status: "failed",
    failureCode: "REJ-IFSC-04",
    failureReason: "IFSC changed after bank merger",
    fix: "Confirm new IFSC with branch, then use 1-tap IFSC update here.",
    district: "Bastar",
    updatedAt: "2026-09-17T12:30:00+05:30",
  },
  {
    id: "DBT-77054",
    studentName: "Mina Rathwa",
    usidLast8: "C04D88E1",
    scheme: "Top Class",
    amountPaise: 8600000,
    status: "paid",
    failureCode: null,
    failureReason: null,
    fix: null,
    district: "Narmada",
    updatedAt: "2026-09-15T09:00:00+05:30",
  },
  {
    id: "DBT-77031",
    studentName: "Birsa Munda",
    usidLast8: "77C2B910",
    scheme: "Pre-Matric",
    amountPaise: 480000,
    status: "pending",
    failureCode: null,
    failureReason: "Sanction issued, funds not yet released by PFMS",
    fix: "Sanction order visible. Escalate to district nodal if older than 7 days.",
    district: "Khunti",
    updatedAt: "2026-09-19T16:00:00+05:30",
  },
];

export const studentDashboard: StudentDashboard = {
  greetingName: "Sunita",
  schemes: [
    { name: "Pre-Matric", eligible: false, status: "Not eligible", amountPaise: 0, reason: "Passed Class X in 2024" },
    { name: "Post-Matric", eligible: true, status: "Applied · district nodal", amountPaise: 1240000, reason: null },
    { name: "Top Class", eligible: false, status: "Not eligible", amountPaise: 0, reason: "Institution not in Top Class list" },
    { name: "NFST", eligible: false, status: "Need PG + NET", amountPaise: 0, reason: "Requires post-graduation" },
    { name: "NOS", eligible: false, status: "Need top-100 admit", amountPaise: 0, reason: "Requires foreign university admit" },
  ],
  applications: [
    { id: "APP-90412", scheme: "Post-Matric", stage: "District nodal verification", actor: "District Nodal Officer, Mandla", elapsedDays: 11, slaDays: 7 },
  ],
  receivedPaise: 1240000,
  pendingPaise: 480000,
  actions: [
    { id: "ACT-1", title: "Income certificate expired for FY 2026-27 — refetch from DigiLocker (1 tap)", cta: "Refetch now" },
  ],
};

export const walletClaims: WalletClaim[] = [
  { id: "CLM-1", type: "ST Certificate", preview: "Community: Gond · State: MP", source: "State portal", validUntil: "2028-03-31T00:00:00+05:30", status: "valid", verifiedAt: "2026-08-02T10:00:00+05:30" },
  { id: "CLM-2", type: "Income Certificate", preview: "₹2,40,000 · FY 2025-26", source: "DigiLocker", validUntil: "2026-09-10T00:00:00+05:30", status: "expired", verifiedAt: "2025-08-02T10:00:00+05:30" },
  { id: "CLM-3", type: "Bank Account", preview: "A/c ····4821 · IFSC updated", source: "PFMS", validUntil: "2027-01-01T00:00:00+05:30", status: "expiring", verifiedAt: "2026-06-11T10:00:00+05:30" },
];

export const jagoSeedMessages: ChatMessage[] = [
  {
    id: "m1",
    role: "jago",
    text: "Namaste Sunita! I am JAGO+. Ask me about your scholarship in Hindi, English, Santali or Gondi.",
    createdAt: "2026-09-26T09:00:00+05:30",
  },
  {
    id: "m2",
    role: "user",
    text: "Meri Post-Matric application kahan atki hai?",
    createdAt: "2026-09-26T09:01:00+05:30",
  },
  {
    id: "m3",
    role: "jago",
    text: "Your Post-Matric application is at the district nodal stage. District Nodal Officer, Mandla has been reviewing it for 11 days (SLA is 7 days).",
    card: {
      title: "APP-90412 · Post-Matric",
      rows: [
        { label: "Stage", value: "District nodal verification" },
        { label: "Pending with", value: "District Nodal Officer, Mandla" },
        { label: "SLA", value: "11 / 7 days — breached" },
      ],
      actions: ["Escalate", "Connect DigiLocker"],
    },
    createdAt: "2026-09-26T09:01:20+05:30",
  },
];
