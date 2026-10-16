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

function impl(cmd: string) {
  if (cmd === "parent_dashboard" || cmd === "parent_refresh") return { state: "success", data: { profile: { uid: "u", email: "p@x.com", familyId: "f" }, children: [card], maxChildren: 5 }, error: null };
  if (cmd === "parent_child_detail") return detail;
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
