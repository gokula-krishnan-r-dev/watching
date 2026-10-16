import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RoleSelectScreen } from "../screens/role-select";
import { mockBridge } from "./helpers";

describe("RoleSelectScreen", () => {
  it("parent goes to sign-in", async () => {
    const b = mockBridge();
    render(<RoleSelectScreen bridge={b} platform="macos" />);
    await userEvent.click(screen.getByRole("button", { name: /continue as parent/i }));
    expect(b.navigate).toHaveBeenCalledWith({ name: "parent-auth", step: "email" });
  });

  it("child sets role then navigates; shows error on failure", async () => {
    const b = mockBridge({}, (cmd) => {
      if (cmd === "set_role") throw "Guardian unavailable";
    });
    render(<RoleSelectScreen bridge={b} platform="macos" />);
    await userEvent.click(screen.getByRole("button", { name: /set up child computer/i }));
    expect(await screen.findByText(/guardian unavailable/i)).toBeInTheDocument();
    expect(b.navigate).not.toHaveBeenCalled();
  });

  it("dev reset is hidden in production and never deletes the family", async () => {
    const reset = vi.fn(async () => {});
    const { rerender } = render(<RoleSelectScreen bridge={mockBridge()} platform="macos" onResetLocal={reset} />);
    expect(screen.queryByTestId("dev-reset")).toBeNull();
    const b = mockBridge({ devShortcuts: true });
    rerender(<RoleSelectScreen bridge={b} platform="macos" onResetLocal={reset} />);
    await userEvent.click(screen.getByTestId("dev-reset"));
    await waitFor(() => expect(reset).toHaveBeenCalled());
    expect(b.invoke).not.toHaveBeenCalledWith("parent_delete_family", expect.anything());
  });
});
