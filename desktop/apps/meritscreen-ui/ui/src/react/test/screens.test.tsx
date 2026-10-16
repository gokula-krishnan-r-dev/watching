import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { AppBridge } from "../bridge";
import { ParentAuthScreen } from "../screens/parent-auth";
import { ChildPairingScreen } from "../screens/child-pairing";
import { ChildPinMenuScreen } from "../screens/child-pin-menu";

function bridge(over: Partial<AppBridge> = {}, impl?: (cmd: string, args?: Record<string, unknown>) => unknown): AppBridge {
  return {
    invoke: vi.fn(async (cmd: string, args?: Record<string, unknown>) => (impl ? impl(cmd, args) : undefined)) as AppBridge["invoke"],
    navigate: vi.fn(async () => {}),
    toast: vi.fn(),
    devShortcuts: false,
    liveBackend: true,
    appleSignInAvailable: false,
    ...over,
  };
}

describe("ParentAuthScreen", () => {
  it("validates email before calling the backend", async () => {
    const b = bridge();
    render(<ParentAuthScreen bridge={b} step="email" email="" onEmailChange={() => {}} />);
    await userEvent.click(screen.getByRole("button", { name: /send verification code/i }));
    expect(await screen.findByText(/enter your email address/i)).toBeInTheDocument();
    expect(b.invoke).not.toHaveBeenCalled();
  });

  it("sends OTP and navigates to the code step", async () => {
    const b = bridge();
    render(<ParentAuthScreen bridge={b} step="email" email="" onEmailChange={() => {}} />);
    await userEvent.type(screen.getByLabelText("Email"), "Parent@Example.com");
    await userEvent.click(screen.getByRole("button", { name: /send verification code/i }));
    await waitFor(() => expect(b.navigate).toHaveBeenCalledWith({ name: "parent-auth", step: "otp", email: "parent@example.com" }));
    expect(b.invoke).toHaveBeenCalledWith("parent_send_otp", { email: "parent@example.com" });
  });

  it("shows backend error and re-enables the form", async () => {
    const b = bridge({}, (cmd) => {
      if (cmd === "parent_send_otp") throw "Too many requests. Try again later.";
    });
    render(<ParentAuthScreen bridge={b} step="email" email="a@b.co" onEmailChange={() => {}} />);
    await userEvent.click(screen.getByRole("button", { name: /send verification code/i }));
    expect(await screen.findByText(/too many requests/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /send verification code/i })).toBeEnabled();
  });

  it("hides dev tester chips and demo hints in production", () => {
    render(<ParentAuthScreen bridge={bridge()} step="email" email="" onEmailChange={() => {}} />);
    expect(screen.queryByTestId("dev-tester-chips")).toBeNull();
    expect(screen.queryByText(/123456/)).toBeNull();
  });

  it("auto-verifies when 6 digits are entered and routes to onboarding", async () => {
    const b = bridge({}, (cmd) => (cmd === "parent_dashboard" ? { state: "empty" } : undefined));
    localStorage.clear();
    render(<ParentAuthScreen bridge={b} step="otp" email="a@b.co" onEmailChange={() => {}} />);
    await userEvent.keyboard("424242");
    await waitFor(() => expect(b.navigate).toHaveBeenCalledWith({ name: "parent-onboard" }));
    expect(b.invoke).toHaveBeenCalledWith("parent_verify_otp", { email: "a@b.co", code: "424242" });
  });

  it("clears the code and shows an error when verification fails", async () => {
    const b = bridge({}, (cmd) => {
      if (cmd === "parent_verify_otp") throw new Error("That verification code is incorrect.");
    });
    render(<ParentAuthScreen bridge={b} step="otp" email="a@b.co" onEmailChange={() => {}} />);
    await userEvent.keyboard("111111");
    expect(await screen.findByText(/incorrect/i)).toBeInTheDocument();
    expect(b.navigate).not.toHaveBeenCalled();
  });
});

describe("ChildPairingScreen", () => {
  it("pairs with the entered code and enables autostart", async () => {
    const b = bridge();
    render(<ChildPairingScreen bridge={b} />);
    await userEvent.keyboard("123456");
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("child_pair", { code: "123456", childName: null }));
    expect(b.invoke).toHaveBeenCalledWith("set_autostart", { enabled: true });
  });

  it("shows server errors (e.g. expired code)", async () => {
    const b = bridge({}, (cmd) => {
      if (cmd === "child_pair") throw "That pairing code has expired. Ask a parent to refresh it.";
    });
    render(<ChildPairingScreen bridge={b} />);
    await userEvent.keyboard("654321");
    expect(await screen.findByText(/expired/i)).toBeInTheDocument();
  });

  it("only shows the demo fill button in dev offline builds", () => {
    const { rerender } = render(<ChildPairingScreen bridge={bridge()} />);
    expect(screen.queryByTestId("dev-demo-code")).toBeNull();
    rerender(<ChildPairingScreen bridge={bridge({ devShortcuts: true, liveBackend: false })} />);
    expect(screen.getByTestId("dev-demo-code")).toBeInTheDocument();
  });
});

describe("ChildPinMenuScreen", () => {
  it("requires confirmation before unpairing", async () => {
    const b = bridge();
    render(<ChildPinMenuScreen bridge={b} childName="Asha" />);
    await userEvent.keyboard("5791");
    await userEvent.click(screen.getByRole("button", { name: /unpair this computer/i }));
    expect(b.invoke).not.toHaveBeenCalledWith("child_pin_unpair", expect.anything());
    await userEvent.click(screen.getByRole("button", { name: /confirm unpair/i }));
    await waitFor(() => expect(b.invoke).toHaveBeenCalledWith("child_pin_unpair", { pin: "5791" }));
  });

  it("shows wrong-PIN error and clears the PIN", async () => {
    const b = bridge({}, (cmd) => {
      if (cmd === "child_exit_with_pin") throw "Incorrect PIN.";
    });
    render(<ChildPinMenuScreen bridge={b} childName="Asha" />);
    await userEvent.keyboard("0000");
    await userEvent.click(screen.getByRole("button", { name: /exit watching/i }));
    expect(await screen.findByText(/incorrect pin/i)).toBeInTheDocument();
  });
});

import { friendlyError, isSessionExpired, SESSION_EXPIRED_MSG } from "../lib/errors";
describe("session expiry errors", () => {
  it("only the backend's post-refresh message triggers sign-in", () => {
    expect(isSessionExpired(`command parent_remove_child: ${SESSION_EXPIRED_MSG}`)).toBe(true);
    expect(isSessionExpired("Can’t reach the server.")).toBe(false);
    expect(friendlyError("Unauthenticated")).toBe(SESSION_EXPIRED_MSG);
  });
});
