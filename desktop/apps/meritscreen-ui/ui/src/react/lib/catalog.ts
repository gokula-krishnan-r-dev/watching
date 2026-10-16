export const AVATARS = [
  { id: "fox", emoji: "🦊", label: "Fox" },
  { id: "rabbit", emoji: "🐰", label: "Rabbit" },
  { id: "owl", emoji: "🦉", label: "Owl" },
  { id: "bear", emoji: "🐻", label: "Bear" },
  { id: "astro", emoji: "🚀", label: "Astro" },
  { id: "panda", emoji: "🐼", label: "Panda" },
  { id: "lion", emoji: "🦁", label: "Lion" },
  { id: "turtle", emoji: "🐢", label: "Turtle" },
] as const;

/** Age bands supported by Android/iOS policy + quiz banks (`AGE_*` in Firestore). */
export const AGE_BANDS = [
  { id: "3_to_6", label: "Ages 3–6", sub: "Early explorers · phonics, counting, shapes" },
  { id: "7_to_9", label: "Ages 7–9", sub: "Builders · reading, maths, science basics" },
  { id: "10_to_12", label: "Ages 10–12", sub: "Thinkers · problem solving, writing, STEM" },
] as const;

export const MAX_CHILDREN = 5;
export const NAME_MAX = 30;

export function ageLabel(band: string): string {
  const b = band.toLowerCase().replace(/^age_/, "");
  return AGE_BANDS.find((a) => a.id === b)?.label ?? (b ? b.replace(/_/g, " ").replace(" to ", "–") : "—");
}

export function avatarFor(childId: string, name: string): string {
  try {
    const chosen = localStorage.getItem("ms_avatar_" + childId) || localStorage.getItem("ms_avatar_" + name);
    const m = AVATARS.find((a) => a.id === chosen);
    if (m) return m.emoji;
  } catch {
    /* storage unavailable */
  }
  const sum = Array.from(name || "Child").reduce((acc, c) => acc + c.charCodeAt(0), 0);
  return AVATARS[sum % AVATARS.length].emoji;
}

export function saveAvatar(childId: string, avatarId: string): void {
  try {
    localStorage.setItem("ms_avatar_" + childId, avatarId);
  } catch {
    /* ignore */
  }
}

export function validateChildName(name: string): string | null {
  const v = name.trim();
  if (!v) return "Enter your child’s name.";
  if (v.length > NAME_MAX) return `Keep the name under ${NAME_MAX} characters.`;
  if (!/^[\p{L}\p{M}' .-]+$/u.test(v)) return "Use letters, spaces, apostrophes or hyphens only.";
  return null;
}

const WEAK_PINS = new Set(["0000", "1111", "2222", "3333", "4444", "5555", "6666", "7777", "8888", "9999", "1234", "4321", "1212", "0123"]);

export function validateNewPin(pin: string, confirm: string): string | null {
  if (!/^\d{4}$/.test(pin)) return "Enter a 4-digit PIN.";
  if (WEAK_PINS.has(pin)) return "That PIN is too easy to guess. Choose a less obvious one.";
  if (confirm.length < 4) return "Re-enter the PIN to confirm it.";
  if (pin !== confirm) return "The PINs don’t match.";
  return null;
}

export function formatMinutes(m: number | null | undefined): string {
  if (m == null) return "No limit";
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  const r = m % 60;
  return r ? `${h} h ${r} min` : `${h} h`;
}
