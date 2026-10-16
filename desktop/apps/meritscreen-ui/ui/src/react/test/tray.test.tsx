import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { TrayPopover, ShieldOverlay } from "../index";
import { mockBridge } from "./helpers";

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

  it("shield overlay renders", () => {
    render(<ShieldOverlay />);
    expect(screen.getByText(/Screen paused/)).toBeInTheDocument();
  });
});
