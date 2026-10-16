import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ChildSetupWizard } from "../screens/child-setup-wizard";
import { mockBridge } from "./helpers";

const card = { childId: "c1", displayName: "Asha", ageBand: "7_to_9", minutesUsedToday: 0, minutesRemainingToday: null, pairedDeviceCount: 0, platformHint: null, quizMode: "app_block" };
const policy = { quizMode: "app_block", quizIntervalMinutes: 15, allowRetryDuringCooldown: true, dailyCeilingMinutes: null, questionsPerQuiz: 3, passScorePercent: 70, rewardsEnabled: true, weekendBonusEnabled: false, extraMinutesOnPass: 0, defaultBlockMinutes: 20, defaultCooldownMinutes: 10, emergencyApps: [], paused: false };

function impl(cmd: string) {
  switch (cmd) {
    case "parent_onboard":
    case "parent_add_child":
      return card;
    case "parent_child_detail":
      return { card, policy, appRules: [], failLockScope: "all_non_emergency", enforcement: null };
    case "parent_create_pairing":
      return { childId: "c1", code: "482913", secret: "", qrPayload: "", expiresAtEpochMs: Date.now() + 600000 };
  }
  return undefined;
}

describe("ChildSetupWizard", () => {
  it("validates the child name", async () => {
    render(<ChildSetupWizard bridge={mockBridge({}, impl)} mode="onboard" />);
    await userEvent.click(screen.getByRole("button", { name: /continue/i }));
    expect(await screen.findByText(/enter your child’s name/i)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Name"), "Asha123");
    await userEvent.click(screen.getByRole("button", { name: /continue/i }));
    expect(await screen.findByText(/letters, spaces/i)).toBeInTheDocument();
  });

  it("onboarding: rejects weak/mismatched PIN, then creates family, applies limits and shows a real pairing code", async () => {
    const b = mockBridge({}, impl);
    render(<ChildSetupWizard bridge={b} mode="onboard" />);
    await userEvent.type(screen.getByLabelText("Name"), "Asha");
    await userEvent.click(screen.getByRole("button", { name: /continue/i })); // → limits
    await userEvent.click(screen.getByRole("button", { name: /continue/i })); // → apps
    await userEvent.click(screen.getByRole("button", { name: /continue/i })); // → pin
    const [p1, p2] = screen.getAllByRole("group");
    await userEvent.click(p1.querySelector("input")!);
    await userEvent.keyboard("1234");
    await userEvent.click(p2.querySelector("input")!);
    await userEvent.keyboard("1234");
    await userEvent.click(screen.getByRole("button", { name: /create profile/i }));
    expect(await screen.findByText(/too easy/i)).toBeInTheDocument();
    expect(b.invoke).not.toHaveBeenCalledWith("parent_onboard", expect.anything());

    await userEvent.click(p1.querySelector("input")!);
    await userEvent.keyboard("{Backspace}{Backspace}{Backspace}{Backspace}5791");
    await userEvent.click(p2.querySelector("input")!);
    await userEvent.keyboard("{Backspace}{Backspace}{Backspace}{Backspace}5791");
    await userEvent.click(screen.getByRole("button", { name: /create profile/i }));
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("parent_onboard", { childName: "Asha", ageBand: "7_to_9", pin: "5791" }));
    await waitFor(() =>
      expect(b.invoke).toHaveBeenCalledWith("parent_update_policy", expect.objectContaining({ childId: "c1", policy: expect.objectContaining({ dailyCeilingMinutes: 90, quizIntervalMinutes: 30 }) })),
    );
    expect(await screen.findByTestId("pairing-code")).toHaveTextContent("482 913");
  });

  it("add-child has no PIN step and surfaces backend errors", async () => {
    const b = mockBridge({}, (cmd) => {
      if (cmd === "parent_add_child") throw "You can add up to 5 children.";
      return impl(cmd);
    });
    render(<ChildSetupWizard bridge={b} mode="add-child" />);
    expect(screen.queryByText("Parent PIN")).toBeNull();
    await userEvent.type(screen.getByLabelText("Name"), "Ravi");
    await userEvent.click(screen.getByRole("button", { name: /continue/i }));
    await userEvent.click(screen.getByRole("button", { name: /continue/i }));
    await userEvent.click(screen.getByRole("button", { name: /create profile/i }));
    expect(await screen.findByText(/up to 5 children/i)).toBeInTheDocument();
  });
});
