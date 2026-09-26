// Mock dataset matching real backend schema for instant testing without requiring active Cloud Functions/live Auth
export interface UserItem {
  uid: string;
  email: string;
  displayName: string;
  familyId: string | null;
  status: "active" | "disabled" | "inactive";
  createdAt: string;
  lastSignInAt: string;
  providers: string[];
}

export interface DeviceItem {
  deviceId: string;
  childId: string;
  childName: string;
  familyId: string;
  familyName: string;
  model: string;
  osVersion: string;
  appVersion: string;
  revoked: boolean;
  launcherDefault: boolean;
  batteryPercent: number | null;
  lastSeenAt: string;
}

export interface ChildItem {
  childId: string;
  displayName: string;
  ageBand: "AGE_3_TO_6" | "AGE_7_TO_9" | "AGE_10_TO_12";
  avatarId: string;
  language: string;
  familyId: string;
  familyName: string;
  deviceCount: number;
  devices: DeviceItem[];
  policySummary: {
    quizMode: "app_block" | "earn_minutes" | "curfew";
    dailyCeilingMinutes: number;
    aiQuizzesEnabled: boolean;
    bonusMinutesPerQuiz: number;
  };
}

export interface FamilyItem {
  familyId: string;
  name: string;
  ownerUid: string;
  ownerEmail: string;
  childCount: number;
  deviceCount: number;
  status: "active" | "suspended";
  createdAt: string;
  members: { uid: string; email: string; role: string }[];
  children: ChildItem[];
}

export interface AuditLogItem {
  id: string;
  actorUid: string;
  actorEmail: string;
  action: string;
  targetType: "user" | "family" | "child" | "device" | "export" | "operator";
  targetId: string;
  metadata: Record<string, any>;
  createdAt: string;
}

export interface OperatorItem {
  uid: string;
  email: string;
  displayName: string;
  role: "super_admin";
  grantedAt: string;
}

// Initial Mock Database
export const initialUsers: UserItem[] = [
  {
    uid: "usr_patel_01",
    email: "sarah.patel@example.com",
    displayName: "Sarah Patel",
    familyId: "fam_patel_01",
    status: "active",
    createdAt: "2026-07-12T10:15:00Z",
    lastSignInAt: "2026-09-25T08:30:00Z",
    providers: ["password", "google.com"],
  },
  {
    uid: "usr_miller_02",
    email: "david.miller@example.com",
    displayName: "David Miller",
    familyId: "fam_miller_02",
    status: "active",
    createdAt: "2026-08-01T14:22:00Z",
    lastSignInAt: "2026-09-24T21:40:00Z",
    providers: ["password"],
  },
  {
    uid: "usr_chen_03",
    email: "wei.chen@example.com",
    displayName: "Wei Chen",
    familyId: "fam_chen_03",
    status: "active",
    createdAt: "2026-08-19T09:11:00Z",
    lastSignInAt: "2026-09-25T06:14:00Z",
    providers: ["google.com"],
  },
  {
    uid: "usr_rossi_04",
    email: "elena.rossi@example.com",
    displayName: "Elena Rossi",
    familyId: "fam_rossi_04",
    status: "disabled",
    createdAt: "2026-06-05T18:00:00Z",
    lastSignInAt: "2026-09-10T12:00:00Z",
    providers: ["password"],
  },
  {
    uid: "usr_dubois_05",
    email: "lucas.dubois@example.com",
    displayName: "Lucas Dubois",
    familyId: "fam_dubois_05",
    status: "inactive",
    createdAt: "2026-05-14T11:45:00Z",
    lastSignInAt: "2026-08-20T08:10:00Z",
    providers: ["password"],
  },
  {
    uid: "usr_tanaka_06",
    email: "kenji.tanaka@example.com",
    displayName: "Kenji Tanaka",
    familyId: "fam_tanaka_06",
    status: "active",
    createdAt: "2026-09-01T16:04:00Z",
    lastSignInAt: "2026-09-25T03:20:00Z",
    providers: ["google.com"],
  },
  {
    uid: "usr_johansson_07",
    email: "astrid.johansson@example.com",
    displayName: "Astrid Johansson",
    familyId: "fam_johansson_07",
    status: "active",
    createdAt: "2026-09-18T08:50:00Z",
    lastSignInAt: "2026-09-24T19:30:00Z",
    providers: ["password"],
  },
];

export const initialFamilies: FamilyItem[] = [
  {
    familyId: "fam_patel_01",
    name: "Patel Family",
    ownerUid: "usr_patel_01",
    ownerEmail: "sarah.patel@example.com",
    childCount: 2,
    deviceCount: 2,
    status: "active",
    createdAt: "2026-07-12T10:20:00Z",
    members: [
      { uid: "usr_patel_01", email: "sarah.patel@example.com", role: "owner" },
      { uid: "usr_patel_partner", email: "raj.patel@example.com", role: "parent" },
    ],
    children: [
      {
        childId: "ch_mira_01",
        displayName: "Mira Patel",
        ageBand: "AGE_7_TO_9",
        avatarId: "RABBIT",
        language: "en",
        familyId: "fam_patel_01",
        familyName: "Patel Family",
        deviceCount: 1,
        policySummary: {
          quizMode: "earn_minutes",
          dailyCeilingMinutes: 90,
          aiQuizzesEnabled: true,
          bonusMinutesPerQuiz: 15,
        },
        devices: [
          {
            deviceId: "dev_samsung_tab_a9",
            childId: "ch_mira_01",
            childName: "Mira Patel",
            familyId: "fam_patel_01",
            familyName: "Patel Family",
            model: "Samsung Galaxy Tab A9+",
            osVersion: "Android 14 (OneUI 6.0)",
            appVersion: "1.2.0",
            revoked: false,
            launcherDefault: true,
            batteryPercent: 84,
            lastSeenAt: "2026-09-25T09:10:00Z",
          },
        ],
      },
      {
        childId: "ch_arav_02",
        displayName: "Aarav Patel",
        ageBand: "AGE_3_TO_6",
        avatarId: "LION",
        language: "en",
        familyId: "fam_patel_01",
        familyName: "Patel Family",
        deviceCount: 1,
        policySummary: {
          quizMode: "app_block",
          dailyCeilingMinutes: 45,
          aiQuizzesEnabled: false,
          bonusMinutesPerQuiz: 10,
        },
        devices: [
          {
            deviceId: "dev_lenovo_m8",
            childId: "ch_arav_02",
            childName: "Aarav Patel",
            familyId: "fam_patel_01",
            familyName: "Patel Family",
            model: "Lenovo Tab M8 (4th Gen)",
            osVersion: "Android 13",
            appVersion: "1.2.0",
            revoked: false,
            launcherDefault: true,
            batteryPercent: 62,
            lastSeenAt: "2026-09-25T08:50:00Z",
          },
        ],
      },
    ],
  },
  {
    familyId: "fam_miller_02",
    name: "Miller Household",
    ownerUid: "usr_miller_02",
    ownerEmail: "david.miller@example.com",
    childCount: 1,
    deviceCount: 1,
    status: "active",
    createdAt: "2026-08-01T14:30:00Z",
    members: [{ uid: "usr_miller_02", email: "david.miller@example.com", role: "owner" }],
    children: [
      {
        childId: "ch_leo_03",
        displayName: "Leo Miller",
        ageBand: "AGE_10_TO_12",
        avatarId: "FOX",
        language: "en",
        familyId: "fam_miller_02",
        familyName: "Miller Household",
        deviceCount: 1,
        policySummary: {
          quizMode: "earn_minutes",
          dailyCeilingMinutes: 120,
          aiQuizzesEnabled: true,
          bonusMinutesPerQuiz: 20,
        },
        devices: [
          {
            deviceId: "dev_pixel_7a",
            childId: "ch_leo_03",
            childName: "Leo Miller",
            familyId: "fam_miller_02",
            familyName: "Miller Household",
            model: "Google Pixel 7a",
            osVersion: "Android 15",
            appVersion: "1.2.0",
            revoked: false,
            launcherDefault: true,
            batteryPercent: 91,
            lastSeenAt: "2026-09-25T09:05:00Z",
          },
        ],
      },
    ],
  },
  {
    familyId: "fam_chen_03",
    name: "Chen Family",
    ownerUid: "usr_chen_03",
    ownerEmail: "wei.chen@example.com",
    childCount: 2,
    deviceCount: 2,
    status: "active",
    createdAt: "2026-08-19T09:20:00Z",
    members: [{ uid: "usr_chen_03", email: "wei.chen@example.com", role: "owner" }],
    children: [
      {
        childId: "ch_emma_04",
        displayName: "Emma Chen",
        ageBand: "AGE_7_TO_9",
        avatarId: "PANDA",
        language: "en",
        familyId: "fam_chen_03",
        familyName: "Chen Family",
        deviceCount: 1,
        policySummary: {
          quizMode: "curfew",
          dailyCeilingMinutes: 60,
          aiQuizzesEnabled: true,
          bonusMinutesPerQuiz: 15,
        },
        devices: [
          {
            deviceId: "dev_moto_g84",
            childId: "ch_emma_04",
            childName: "Emma Chen",
            familyId: "fam_chen_03",
            familyName: "Chen Family",
            model: "Motorola Moto G84",
            osVersion: "Android 14",
            appVersion: "1.1.9",
            revoked: false,
            launcherDefault: true,
            batteryPercent: 35,
            lastSeenAt: "2026-09-24T18:20:00Z",
          },
        ],
      },
      {
        childId: "ch_lucas_05",
        displayName: "Lucas Chen",
        ageBand: "AGE_3_TO_6",
        avatarId: "BEAR",
        language: "en",
        familyId: "fam_chen_03",
        familyName: "Chen Family",
        deviceCount: 1,
        policySummary: {
          quizMode: "app_block",
          dailyCeilingMinutes: 40,
          aiQuizzesEnabled: false,
          bonusMinutesPerQuiz: 10,
        },
        devices: [
          {
            deviceId: "dev_fire_hd10",
            childId: "ch_lucas_05",
            childName: "Lucas Chen",
            familyId: "fam_chen_03",
            familyName: "Chen Family",
            model: "Amazon Fire HD 10 (Sideload)",
            osVersion: "Android 11 / FireOS",
            appVersion: "1.1.8",
            revoked: false,
            launcherDefault: false,
            batteryPercent: 78,
            lastSeenAt: "2026-09-23T11:00:00Z",
          },
        ],
      },
    ],
  },
  {
    familyId: "fam_rossi_04",
    name: "Rossi Residence",
    ownerUid: "usr_rossi_04",
    ownerEmail: "elena.rossi@example.com",
    childCount: 1,
    deviceCount: 1,
    status: "suspended",
    createdAt: "2026-06-05T18:10:00Z",
    members: [{ uid: "usr_rossi_04", email: "elena.rossi@example.com", role: "owner" }],
    children: [
      {
        childId: "ch_marco_06",
        displayName: "Marco Rossi",
        ageBand: "AGE_10_TO_12",
        avatarId: "OWL",
        language: "it",
        familyId: "fam_rossi_04",
        familyName: "Rossi Residence",
        deviceCount: 1,
        policySummary: {
          quizMode: "app_block",
          dailyCeilingMinutes: 90,
          aiQuizzesEnabled: true,
          bonusMinutesPerQuiz: 15,
        },
        devices: [
          {
            deviceId: "dev_xiaomi_pad6",
            childId: "ch_marco_06",
            childName: "Marco Rossi",
            familyId: "fam_rossi_04",
            familyName: "Rossi Residence",
            model: "Xiaomi Pad 6",
            osVersion: "Android 14 (HyperOS)",
            appVersion: "1.1.4",
            revoked: true,
            launcherDefault: true,
            batteryPercent: 12,
            lastSeenAt: "2026-09-10T12:00:00Z",
          },
        ],
      },
    ],
  },
];

export const initialAuditLogs: AuditLogItem[] = [
  {
    id: "aud_01",
    actorUid: "admin_super_01",
    actorEmail: "admin@meritscreen.internal",
    action: "user.setStatus",
    targetType: "user",
    targetId: "usr_rossi_04",
    metadata: { newStatus: "disabled", reason: "Parent requested account pause" },
    createdAt: "2026-09-24T14:10:00Z",
  },
  {
    id: "aud_02",
    actorUid: "admin_super_01",
    actorEmail: "admin@meritscreen.internal",
    action: "family.setStatus",
    targetType: "family",
    targetId: "fam_rossi_04",
    metadata: { newStatus: "suspended" },
    createdAt: "2026-09-24T14:12:00Z",
  },
  {
    id: "aud_03",
    actorUid: "admin_super_01",
    actorEmail: "admin@meritscreen.internal",
    action: "device.revoke",
    targetType: "device",
    targetId: "dev_xiaomi_pad6",
    metadata: { reason: "Device reported stolen / unlinked" },
    createdAt: "2026-09-24T14:15:00Z",
  },
  {
    id: "aud_04",
    actorUid: "admin_super_01",
    actorEmail: "admin@meritscreen.internal",
    action: "export.generate",
    targetType: "export",
    targetId: "exp_users_20260920",
    metadata: { entity: "users", format: "csv" },
    createdAt: "2026-09-20T10:00:00Z",
  },
];

export const initialOperators: OperatorItem[] = [
  {
    uid: "admin_super_01",
    email: "lead.ops@meritscreen.internal",
    displayName: "Lead Security Operator",
    role: "super_admin",
    grantedAt: "2026-01-01T00:00:00Z",
  },
  {
    uid: "admin_super_02",
    email: "compliance@meritscreen.internal",
    displayName: "Privacy & Compliance Officer",
    role: "super_admin",
    grantedAt: "2026-03-15T09:30:00Z",
  },
];
