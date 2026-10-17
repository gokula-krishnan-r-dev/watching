import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { ChildSessionScreen } from "../index";
import { mockBridge } from "./helpers";
import type { ChildUiState } from "../lib/types";

const base: ChildUiState = {
  paired: true, screen: "launcher", phase: "in_block", childName: "Mia", apps: [], checklist: [], checklistComplete: true,
  minutesUsedToday: 20, minutesRemainingBlock: 10, secondsRemainingBlock: 600, blockDurationMinutes: 15, cooldownSeconds: 0,
  dailyRemainingMinutes: 100, ceilingHit: false, quiz: null, quizLock: null, lastFeedback: null, lastResult: null, packSource: "builtin", elapsedMs: 0,
};
const quiz: ChildUiState = { ...base, screen: "quiz", quiz: { id: "q", prompt: "2 + 2?", choices: [{ id: "a", text: "3" }, { id: "b", text: "4" }], index: 0, total: 3, teachTitle: null, teachBody: [] } };

describe("ChildSessionScreen", () => {
  it("quiz answers via keyboard and shows success on pass", async () => {
    const b = mockBridge({}, (c) => (c === "child_answer_quiz" ? { ...base, lastResult: { passed: true, correctCount: 3, total: 3, percent: 100 } } : base));
    render(<ChildSessionScreen bridge={b} initial={quiz} autoMinimize={false} />);
    expect(screen.getByRole("dialog", { name: "2 + 2?" })).toBeInTheDocument();
    fireEvent.keyDown(window, { key: "2" });
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("child_answer_quiz", { choiceId: "b" }));
    expect(await screen.findByText("Great job!")).toBeInTheDocument();
  });

  it("no lab buttons or default PIN hint in production", () => {
    const b = mockBridge({ devShortcuts: false }, () => base);
    render(<ChildSessionScreen bridge={b} initial={{ ...base, screen: "fail_lock", cooldownSeconds: 90 }} autoMinimize={false} />);
    expect(screen.queryByText(/lab/i)).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: /Skip with parent PIN/ }));
    expect(screen.queryByText(/1234/)).toBeNull();
  });

  it("fail lock ticks once per second", async () => {
    vi.useFakeTimers();
    const b = mockBridge({}, () => ({ ...base, screen: "fail_lock", cooldownSeconds: 89 }));
    render(<ChildSessionScreen bridge={b} initial={{ ...base, screen: "fail_lock", cooldownSeconds: 90 }} autoMinimize={false} />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1100);
    });
    expect(b.invoke).toHaveBeenCalledWith("child_tick", { deltaMs: 1000 });
    vi.useRealTimers();
  });

  it("nursery teach-first completes and celebrates the sticker", async () => {
    const doneState: ChildUiState = {
      ...base, screen: "launcher",
      explorer: { xp: 10, explorerLevel: 1, passStreakDays: 1 },
      stickers: [{ unlockId: "stk_c1_sprout_star", stickerId: "sprout_star", stage: "SPROUT", title: "Bright Star", emoji: "⭐", source: "NURSERY_TEACH", unlockedAtEpochMs: 1, xpAtUnlock: 10 }],
      lastAward: { stickerTitle: "Bright Star", stickerEmoji: "⭐", stickerStageLabel: "Sprout", xpGained: 10, leveledUp: false, weekendBonusApplied: false },
    };
    const b = mockBridge({}, (c) => (c === "child_complete_nursery" ? doneState : base));
    const nursery: ChildUiState = {
      ...base, screen: "nursery", earlyLearner: true,
      nursery: { video: null, videoCategory: null, items: [{ id: "a_apple", tag: "apple", title: "A for Apple", subtitle: "Letter A", emoji: "🍎", speechText: "A is for Apple!", colorHex: "#FFEBEE", count: null }] },
    };
    render(<ChildSessionScreen bridge={b} initial={nursery} autoMinimize={false} />);
    expect(screen.getByText("A for Apple")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /All done/ }));
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("child_complete_nursery"));
    expect(await screen.findByText(/New sticker: Bright Star/)).toBeInTheDocument();
  });

  it("locked learn-more shows the lesson and correct answer", () => {
    const b = mockBridge({}, () => base);
    const locked: ChildUiState = {
      ...base, screen: "quiz",
      quiz: { id: "q", prompt: "How many apples?", choices: [{ id: "a", text: "3" }], index: 1, total: 3, teachTitle: null, teachBody: [] },
      quizLock: { remainingSeconds: 29, conceptLine: "Count carefully.", whyLine: "That was 2.", correctChoiceText: "3", lessonTitle: "Counting", lessonBody: ["Count each apple once: 1, 2, 3."] },
    };
    render(<ChildSessionScreen bridge={b} initial={locked} autoMinimize={false} />);
    expect(screen.getByText("Count each apple once: 1, 2, 3.")).toBeInTheDocument();
    expect(screen.getByText(/Correct answer: 3/)).toBeInTheDocument();
  });

  it("visual choices render taxonomy emoji and speak buttons", () => {
    const b = mockBridge({}, () => base);
    const visual: ChildUiState = {
      ...base, screen: "quiz", earlyLearner: true,
      quiz: { id: "v", prompt: "Which one is the dog?", choices: [{ id: "a", text: "Dog", imageTag: "dog" }, { id: "b", text: "Cat", imageTag: "cat" }], index: 0, total: 3, teachTitle: null, teachBody: [], interactionType: "TAP_IMAGE" },
    };
    render(<ChildSessionScreen bridge={b} initial={visual} autoMinimize={false} />);
    expect(screen.getByText("🐶")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Listen to the question" })).toBeInTheDocument();
  });

  it("checklist completes", async () => {
    const b = mockBridge({}, () => undefined);
    render(<ChildSessionScreen bridge={b} initial={{ ...base, screen: "checklist", checklist: [{ id: "x", label: "Allow accessibility", done: false, recommended: true }] }} autoMinimize={false} />);
    fireEvent.click(screen.getByRole("switch", { name: /Allow accessibility/ }));
    expect(b.invoke).toHaveBeenCalledWith("child_set_checklist_item", { id: "x", done: true });
    fireEvent.click(screen.getByRole("button", { name: /Continue/ }));
    await waitFor(() => expect(b.navigate).toHaveBeenCalledWith({ name: "child" }));
  });
});
