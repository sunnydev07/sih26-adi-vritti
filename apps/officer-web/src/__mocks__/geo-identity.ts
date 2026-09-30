import type { CoverageRegion, IdentityCase, SchoolOutreachItem } from "@/types";

export const coverageStates: CoverageRegion[] = [
  { id: "MP", name: "Madhya Pradesh", level: "state", parentId: null, totalStudents: 812000, applications: 402000, pvtg: true },
  { id: "CG", name: "Chhattisgarh", level: "state", parentId: null, totalStudents: 516000, applications: 288000, pvtg: true },
  { id: "JH", name: "Jharkhand", level: "state", parentId: null, totalStudents: 604000, applications: 371000, pvtg: true },
  { id: "GJ", name: "Gujarat", level: "state", parentId: null, totalStudents: 498000, applications: 279000, pvtg: false },
];

export const coverageDistricts: CoverageRegion[] = [
  { id: "MP-MAN", name: "Mandla", level: "district", parentId: "MP", totalStudents: 48200, applications: 21400, pvtg: true },
  { id: "MP-DIN", name: "Dindori", level: "district", parentId: "MP", totalStudents: 39800, applications: 24900, pvtg: true },
  { id: "CG-BAS", name: "Bastar", level: "district", parentId: "CG", totalStudents: 44700, applications: 18900, pvtg: true },
  { id: "JH-KHU", name: "Khunti", level: "district", parentId: "JH", totalStudents: 32100, applications: 20800, pvtg: false },
];

export const coverageBlocks: CoverageRegion[] = [
  { id: "MP-MAN-BIC", name: "Bichhiya", level: "block", parentId: "MP-MAN", totalStudents: 5210, applications: 1980, pvtg: true },
  { id: "MP-MAN-NAR", name: "Narayanganj", level: "block", parentId: "MP-MAN", totalStudents: 4340, applications: 2210, pvtg: false },
  { id: "CG-BAS-JAG", name: "Jagdalpur", level: "block", parentId: "CG-BAS", totalStudents: 6100, applications: 2050, pvtg: true },
];

export const coverageSchools: CoverageRegion[] = [
  { id: "SCH-101", name: "Govt HS Bichhiya", level: "school", parentId: "MP-MAN-BIC", totalStudents: 47, applications: 6, pvtg: true },
  { id: "SCH-102", name: "Govt HSS Narayanganj", level: "school", parentId: "MP-MAN-NAR", totalStudents: 63, applications: 41, pvtg: false },
  { id: "SCH-103", name: "Ashram School Jagdalpur", level: "school", parentId: "CG-BAS-JAG", totalStudents: 88, applications: 22, pvtg: true },
];

export const outreachList: SchoolOutreachItem[] = [
  { id: "SCH-101", schoolName: "Govt HS Bichhiya, Mandla", district: "Mandla", classLevel: "Class IX", stStudents: 47, applications: 6, contact: "HM: +91-98XXXXXX21" },
  { id: "SCH-103", schoolName: "Ashram School Jagdalpur", district: "Bastar", classLevel: "Class IX–XII", stStudents: 88, applications: 22, contact: "Warden: +91-98XXXXXX77" },
  { id: "SCH-102", schoolName: "Govt HSS Narayanganj", district: "Mandla", classLevel: "Class XI", stStudents: 63, applications: 41, contact: "HM: +91-98XXXXXX04" },
];

export const identityQueue: IdentityCase[] = [
  {
    id: "IDQ-301",
    nameA: "Meena Kumari",
    nameB: "Mina Kumari",
    systemA: "NSP (OTR 2144…) ",
    systemB: "UDISE+ (AISHE …)",
    confidence: 0.66,
    fields: [
      { field: "Name", valueA: "Meena Kumari", valueB: "Mina Kumari", match: "partial", similarity: 92 },
      { field: "DOB", valueA: "2008-04-11", valueB: "2008-04-11", match: "exact", similarity: 100 },
      { field: "Guardian", valueA: "Somaru", valueB: "Somaru Gond", match: "partial", similarity: 78 },
      { field: "District", valueA: "Mandla", valueB: "Mandla", match: "exact", similarity: 100 },
      { field: "Bank last-4", valueA: "4821", valueB: "—", match: "mismatch", similarity: 0 },
    ],
    createdAt: "2026-09-25T10:00:00+05:30",
  },
  {
    id: "IDQ-302",
    nameA: "Birsa Munda",
    nameB: "Birsa Munda",
    systemA: "NSP",
    systemB: "SFMP",
    confidence: 0.74,
    fields: [
      { field: "Name", valueA: "Birsa Munda", valueB: "Birsa Munda", match: "exact", similarity: 100 },
      { field: "DOB", valueA: "2012-01-02", valueB: "2012-01-12", match: "mismatch", similarity: 61 },
      { field: "Guardian", valueA: "Soma Munda", valueB: "Soma Munda", match: "exact", similarity: 100 },
      { field: "District", valueA: "Khunti", valueB: "Khunti", match: "exact", similarity: 100 },
      { field: "Institution", valueA: "GMS Khunti", valueB: "GMS Khunti", match: "exact", similarity: 100 },
    ],
    createdAt: "2026-09-24T12:00:00+05:30",
  },
  {
    id: "IDQ-303",
    nameA: "Somaru Gond",
    nameB: "Somaroo Gond",
    systemA: "NOS",
    systemB: "NSP",
    confidence: 0.81,
    fields: [
      { field: "Name", valueA: "Somaru Gond", valueB: "Somaroo Gond", match: "partial", similarity: 95 },
      { field: "DOB", valueA: "1999-07-19", valueB: "1999-07-19", match: "exact", similarity: 100 },
      { field: "Guardian", valueA: "Laru Gond", valueB: "Laru Gond", match: "exact", similarity: 100 },
      { field: "District", valueA: "Bastar", valueB: "Bastar", match: "exact", similarity: 100 },
      { field: "Bank last-4", valueA: "9082", valueB: "9082", match: "exact", similarity: 100 },
    ],
    createdAt: "2026-09-23T09:30:00+05:30",
  },
];
