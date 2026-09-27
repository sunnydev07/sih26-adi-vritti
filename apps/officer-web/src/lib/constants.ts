export const SCHEMES = ["Pre-Matric", "Post-Matric", "Top Class", "NFST", "NOS"] as const;
export type Scheme = (typeof SCHEMES)[number];

export const STAGES = [
  "submitted",
  "institute_verification",
  "district_nodal",
  "state_dept",
  "ministry",
  "pfms_payment",
  "disbursed",
] as const;

export const STAGE_LABELS: Record<string, string> = {
  submitted: "Submitted",
  institute_verification: "Institute Verification",
  district_nodal: "District Nodal",
  state_dept: "State Department",
  ministry: "Ministry",
  pfms_payment: "PFMS Payment",
  disbursed: "Disbursed",
};

export const SLA_DAYS = 7;

export const STATUS_COLORS: Record<string, string> = {
  verified: "#10b981",
  pending: "#f59e0b",
  failed: "#f43f5e",
  review: "#8b5cf6",
};

export const FAILURE_FIX_HINTS: Record<string, string> = {
  "REJ-AADHAAR-01": "Seed Aadhaar at the bank branch, then retry. Takes ~24h on the NPCI mapper.",
  "REJ-IFSC-04": "Confirm the new IFSC after the bank merger and update it in one tap.",
};
