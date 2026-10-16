import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ReportsScreen, AccountScreen, NotificationsScreen, DeleteFamilyScreen } from "../index";
import { mockBridge } from "./helpers";

const card = { childId: "c1", displayName: "Mia", ageBand: "7_to_9", minutesUsedToday: 30, minutesRemainingToday: 90, pairedDeviceCount: 1, platformHint: "macOS", quizMode: "x" };
const dash = (children = [card]) => ({ state: "success", data: { profile: { uid: "u", email: "p@x.com", familyId: "fam1" }, children, maxChildren: 5 }, error: null });
const report = { childId: "c1", childName: "Mia", todayMinutes: 30, todayQuizMinutes: 2, weeklyTotalMinutes: 300, weeklyQuizMinutes: 10, dailyAverageMinutes: 43, topApps: [{ appId: "a", displayName: "Safari", minutes: 100, percentage: 33 }], dailyTrend: [{ date: "2026-10-15", totalMinutes: 60, quizMinutes: 2 }], quizStats: { totalAttempts: 4, passes: 3, passRatePercent: 75, totalQuizMinutes: 10 }, syncStatus: "local" };

describe("module 4 screens", () => {
  it("reports render KPIs", async () => {
    const b = mockBridge({}, (c) => (c === "parent_dashboard" ? dash() : c === "parent_child_reports" ? report : undefined));
    render(<ReportsScreen bridge={b} childId="c1" />);
    expect(await screen.findByText("75%")).toBeInTheDocument();
    expect(screen.getByText("Safari")).toBeInTheDocument();
  });
  it("reports empty state with no children", async () => {
    const b = mockBridge({}, (c) => (c === "parent_dashboard" ? dash([]) : undefined));
    render(<ReportsScreen bridge={b} />);
    expect(await screen.findByText("No reports yet")).toBeInTheDocument();
  });
  it("account rejects weak PIN", async () => {
    const b = mockBridge({}, (c) => (c === "parent_dashboard" ? dash() : undefined));
    render(<AccountScreen bridge={b} />);
    await screen.findByText("p@x.com");
    const type = (group: string, v: string) => {
      const inputs = screen.getByRole("group", { name: group }).querySelectorAll("input");
      v.split("").forEach((d, i) => fireEvent.change(inputs[i], { target: { value: d } }));
    };
    type("New PIN", "1234");
    type("Confirm PIN", "1234");
    fireEvent.click(screen.getByRole("button", { name: "Save PIN" }));
    expect(await screen.findByText(/too easy/)).toBeInTheDocument();
    expect(b.invoke).not.toHaveBeenCalledWith("parent_reset_pin", expect.anything());
  });
  it("notifications roll back on failure", async () => {
    const b = mockBridge({}, (c) => {
      if (c === "parent_notification_prefs") return { quizResults: true, failLockAlerts: true, tamperAlerts: true };
      if (c === "parent_set_notification_prefs") throw "offline";
    });
    render(<NotificationsScreen bridge={b} />);
    const sw = await screen.findByRole("switch", { name: /Quiz results/ });
    fireEvent.click(sw);
    await waitFor(() => expect(b.toast).toHaveBeenCalled());
    expect(sw).toHaveAttribute("aria-checked", "true");
  });
  it("delete family requires DELETE", async () => {
    const b = mockBridge();
    render(<DeleteFamilyScreen bridge={b} />);
    const btn = screen.getByRole("button", { name: /Delete family permanently/ });
    expect(btn).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/Type DELETE/), { target: { value: "DELETE" } });
    fireEvent.click(btn);
    await waitFor(() => expect(b.navigate).toHaveBeenCalledWith({ name: "parent-onboard" }));
  });
});
