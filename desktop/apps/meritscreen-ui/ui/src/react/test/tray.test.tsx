import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { TrayPopover, ShieldOverlay } from "../index";
import { mockBridge } from "./helpers";
import { localDayKey } from "../lib/catalog";

const card = { childId: "c1", displayName: "Mia", ageBand: "7_to_9", minutesUsedToday: 30, minutesRemainingToday: 90, pairedDeviceCount: 1, platformHint: "macOS", quizMode: "x" };
const policy = { quizMode: "x", quizIntervalMinutes: 30, allowRetryDuringCooldown: false, dailyCeilingMinutes: 120, questionsPerQuiz: 3, passScorePercent: 70, rewardsEnabled: true, weekendBonusEnabled: false, extraMinutesOnPass: 10, defaultBlockMinutes: 30, defaultCooldownMinutes: 5, emergencyApps: [], paused: false };
const actions = () => ({ close: vi.fn(), openMain: vi.fn(async () => {}) });

describe("tray", () => {
  it("parent tray raises the daily limit within bounds", async () => {
    const b = mockBridge({}, (c) => {
      if (c === "child_ui_state") return { paired: false };
      if (c === "parent_dashboard") return { state: "success", data: { profile: { uid: "u", email: "e", familyId: "f" }, children: [card], maxChildren: 5 }, error: null };
      if (c === "parent_child_detail") return { card, policy, appRules: [], failLockScope: "all", enforcement: null };
    });
    render(<TrayPopover bridge={b} actions={actions()} />);
    fireEvent.click(await screen.findByRole("button", { name: /Raise daily limit/ }));
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("parent_update_policy", { childId: "c1", policy: expect.objectContaining({ dailyCeilingMinutes: 135 }) }));
  });

  it("child tray requires a PIN to exit and has no default-PIN hint", async () => {
    const st = { paired: true, screen: "launcher", childName: "Mia", minutesUsedToday: 5, minutesRemainingBlock: 10, secondsRemainingBlock: 600, blockDurationMinutes: 15, dailyRemainingMinutes: 60 };
    const b = mockBridge({}, (c) => (c === "child_ui_state" ? st : c === "child_exit_with_pin" ? Promise.reject("Incorrect PIN") : true));
    render(<TrayPopover bridge={b} actions={actions()} />);
    fireEvent.click(await screen.findByRole("button", { name: /Exit/ }));
    expect(screen.queryByText(/1234/)).toBeNull();
    const inputs = screen.getByRole("group", { name: "Parent PIN" }).querySelectorAll("input");
    "9876".split("").forEach((d, i) => fireEvent.change(inputs[i], { target: { value: d } }));
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("child_exit_with_pin", { pin: "9876" }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });

  it("bonus buttons grant today-only time; emergency needs confirmation; limit untouched", async () => {
    let pol = { ...policy };
    const b = mockBridge({}, (c, a) => {
      if (c === "child_ui_state") return { paired: false };
      if (c === "parent_dashboard") return { state: "success", data: { profile: { uid: "u", email: "e", familyId: "f" }, children: [card], maxChildren: 5 }, error: null };
      if (c === "parent_child_detail") return { card, policy: pol, appRules: [], failLockScope: "all", enforcement: null };
      if (c === "parent_grant_bonus") {
        pol = { ...pol, bonusMinutesToday: ((pol as { bonusMinutesToday?: number }).bonusMinutesToday ?? 0) + (a!.minutes as number), bonusDay: localDayKey() } as typeof pol;
        return pol;
      }
    });
    render(<TrayPopover bridge={b} actions={actions()} />);
    fireEvent.click(await screen.findByRole("button", { name: "Give 15 bonus minutes today" }));
    expect(await screen.findByText(/15 min granted · resets at midnight/)).toBeInTheDocument();
    expect(screen.getByText(/1 h 30 min left|1 h 45 min left/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Emergency unlock/ }));
    expect(b.invoke).not.toHaveBeenCalledWith("parent_grant_bonus", { childId: "c1", minutes: 60 });
    fireEvent.click(screen.getByRole("button", { name: "Unlock 1 hour" }));
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("parent_grant_bonus", { childId: "c1", minutes: 60 }));
    expect(b.invoke).not.toHaveBeenCalledWith("parent_update_policy", expect.anything());
  });

  it("yesterday's bonus is not shown", async () => {
    const b = mockBridge({}, (c) => {
      if (c === "child_ui_state") return { paired: false };
      if (c === "parent_dashboard") return { state: "success", data: { profile: { uid: "u", email: "e", familyId: "f" }, children: [card], maxChildren: 5 }, error: null };
      if (c === "parent_child_detail") return { card, policy: { ...policy, bonusMinutesToday: 30, bonusDay: "2000-01-01" }, appRules: [], failLockScope: "all", enforcement: null };
    });
    render(<TrayPopover bridge={b} actions={actions()} />);
    expect(await screen.findByText("None today")).toBeInTheDocument();
  });

  it("shield overlay renders", () => {
    render(<ShieldOverlay />);
    expect(screen.getByText(/Screen paused/)).toBeInTheDocument();
  });
});
