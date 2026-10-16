import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DashboardScreen, ChildDetailScreen, PolicyScreen, AllowlistScreen } from "../index";
import { mockBridge } from "./helpers";
import type { ChildDetail } from "../lib/types";

const card = { childId: "c1", displayName: "Mia", ageBand: "7_to_9", minutesUsedToday: 30, minutesRemainingToday: 90, pairedDeviceCount: 1, platformHint: "macOS", quizMode: "x" };
const policy = { quizMode: "x", quizIntervalMinutes: 30, allowRetryDuringCooldown: false, dailyCeilingMinutes: 120, questionsPerQuiz: 3, passScorePercent: 70, rewardsEnabled: true, weekendBonusEnabled: false, extraMinutesOnPass: 10, defaultBlockMinutes: 30, defaultCooldownMinutes: 5, emergencyApps: [], paused: false };
const rules = [
  { appId: "a", packageOrBundleId: "com.a", displayName: "Alpha", allowed: true, blockMinutes: 0, grantOnPassMinutes: 0, cooldownMinutes: 0, isEmergency: false },
  { appId: "p", packageOrBundleId: "com.phone", displayName: "Phone", allowed: true, blockMinutes: 0, grantOnPassMinutes: 0, cooldownMinutes: 0, isEmergency: true },
];
const detail: ChildDetail = { card, policy, appRules: rules, failLockScope: "all", enforcement: null };

const insightChild = {
  childId: "c1", displayName: "Mia", ageBand: "7_to_9", paused: false, dailyLimitMinutes: 120, bonusMinutesToday: 15,
  usedTodayMinutes: 30, remainingTodayMinutes: 105, weekTotalMinutes: 300, dailyAverageMinutes: 50, weekDeltaPercent: -12,
  trend: ["2026-10-10", "2026-10-11", "2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15", "2026-10-16"].map((day, i) => ({ day, minutes: i * 10 })),
  topApps: [{ appId: "a", label: "Alpha", minutes: 120, percent: 60 }],
  quiz: { attemptsWeek: 5, passesWeek: 4, passRatePercent: 80, attemptsToday: 1, passesToday: 1, minutesEarnedWeek: 40, passStreakDays: 3, lastAttemptAtMs: Date.now() - 60_000, recent: [{ atMs: Date.now() - 60_000, passed: true, score: 3, total: 3, topic: "Fractions" }] },
  skills: { strengths: [{ topic: "addition", label: "Addition", level: 4, tier: "basic", concepts: [] }], needsPractice: [{ topic: "fractions", label: "Fractions", level: 1, tier: "basic", concepts: ["Halves"] }] },
  rewards: { xp: 120, level: 3, stickerCount: 2, recentStickers: [{ emoji: "🦊", title: "Fox", unlockedAtMs: 1 }] },
  devices: [{ deviceId: "d1", platform: "android", model: "Pixel 7", lastSeenAtMs: Date.now(), online: true, batteryPercent: 12, appVersion: "1.0" }],
  alerts: [{ severity: "warning", kind: "battery_low", message: "Mia’s device battery is at 12%." }],
  error: null,
};
const insights = { source: "cloud", generatedAtMs: Date.now(), children: [insightChild] };

function impl(cmd: string) {
  if (cmd === "parent_dashboard" || cmd === "parent_refresh") return { state: "success", data: { profile: { uid: "u", email: "p@x.com", familyId: "f" }, children: [card], maxChildren: 5 }, error: null };
  if (cmd === "parent_child_detail") return detail;
  if (cmd === "parent_family_insights") return insights;
  return undefined;
}

describe("parent screens", () => {
  it("dashboard lists children and pauses", async () => {
    const b = mockBridge({}, impl);
    render(<DashboardScreen bridge={b} />);
    expect(await screen.findByText("Mia")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Pause Mia" }));
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("parent_update_policy", expect.objectContaining({ policy: expect.objectContaining({ paused: true }) })));
  });

  it("dashboard shows analytics, alerts and grants a bonus", async () => {
    const b = mockBridge({}, impl);
    render(<DashboardScreen bridge={b} />);
    expect(await screen.findByText("Alpha")).toBeInTheDocument();
    expect(screen.getByText("Mia’s device battery is at 12%.")).toBeInTheDocument();
    expect(screen.getByText("80%")).toBeInTheDocument();
    expect(screen.getByText(/Fox/)).toBeInTheDocument();
    expect(screen.getByText("Pixel 7")).toBeInTheDocument();
    expect(screen.getByText(/12% vs last week/)).toBeInTheDocument();
    expect(screen.getByText(/Synced from your family/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Give Mia 15 bonus minutes today" }));
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("parent_grant_bonus", { childId: "c1", minutes: 15 }));
  });

  it("dashboard keeps child cards when analytics fail", async () => {
    const b = mockBridge({}, (c) => {
      if (c === "parent_family_insights") throw "offline";
      return impl(c);
    });
    render(<DashboardScreen bridge={b} />);
    expect(await screen.findByText(/Couldn’t load activity/)).toBeInTheDocument();
    expect(screen.getByText("Mia", { selector: "span" })).toBeInTheDocument();
  });

  it("dashboard empty state", async () => {
    const b = mockBridge({}, (c) => (c === "parent_dashboard" ? { state: "empty", data: { profile: { uid: "u", email: "", familyId: "f" }, children: [], maxChildren: 5 }, error: null } : undefined));
    render(<DashboardScreen bridge={b} />);
    expect(await screen.findByText("No children yet")).toBeInTheDocument();
  });

  it("dashboard error state", async () => {
    const b = mockBridge({}, () => {
      throw "network down";
    });
    render(<DashboardScreen bridge={b} />);
    expect(await screen.findByRole("button", { name: /try again|retry/i })).toBeInTheDocument();
  });

  it("child removal requires typing the name", async () => {
    const b = mockBridge({}, impl);
    render(<ChildDetailScreen bridge={b} childId="c1" />);
    fireEvent.click(await screen.findByRole("button", { name: /Remove Mia/ }));
    const confirm = screen.getByRole("button", { name: "Remove permanently" });
    expect(confirm).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/Type/), { target: { value: "Mia" } });
    expect(confirm).toBeEnabled();
    fireEvent.click(confirm);
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("parent_remove_child", { childId: "c1" }));
  });

  it("quiz settings save only when dirty", async () => {
    const b = mockBridge({}, impl);
    render(<PolicyScreen bridge={b} childId="c1" kind="rewards" />);
    const save = await screen.findByRole("button", { name: /Save changes/ });
    expect(save).toBeDisabled();
    fireEvent.click(screen.getByRole("switch", { name: /Reward passed quizzes/ }));
    expect(save).toBeEnabled();
    fireEvent.click(save);
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("parent_update_policy", expect.objectContaining({ policy: expect.objectContaining({ rewardsEnabled: false }) })));
  });

  it("allowlist keeps emergency apps allowed on block all", async () => {
    const b = mockBridge({}, impl);
    render(<AllowlistScreen bridge={b} childId="c1" />);
    fireEvent.click(await screen.findByRole("button", { name: "Block all" }));
    fireEvent.click(screen.getByRole("button", { name: /Save changes/ }));
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("parent_update_app_rules", expect.anything()));
    const call = (b.invoke as ReturnType<typeof vi.fn>).mock.calls.find((c) => c[0] === "parent_update_app_rules")!;
    const saved = call[1].rules as typeof rules;
    expect(saved.find((r) => r.appId === "a")!.allowed).toBe(false);
    expect(saved.find((r) => r.appId === "p")!.allowed).toBe(true);
  });
});
