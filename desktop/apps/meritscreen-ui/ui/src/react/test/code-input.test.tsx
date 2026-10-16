import * as React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CodeInput, PinInput } from "../components/code-input";

function Harness(props: { length?: number; mask?: boolean; onComplete?: (v: string) => void }) {
  const [v, setV] = React.useState("");
  return (
    <>
      <CodeInput length={props.length ?? 6} label="Code" value={v} onChange={setV} onComplete={props.onComplete} mask={props.mask} />
      <output data-testid="val">{v}</output>
    </>
  );
}

const cells = () => screen.getAllByRole("textbox") as HTMLInputElement[];

describe("CodeInput", () => {
  it("renders one accessible cell per digit inside a labelled group", () => {
    render(<Harness />);
    expect(screen.getByRole("group", { name: "Code" })).toBeInTheDocument();
    expect(cells()).toHaveLength(6);
    expect(cells()[0]).toHaveAttribute("aria-label", "Code digit 1 of 6");
    expect(cells()[0]).toHaveAttribute("inputmode", "numeric");
  });

  it("auto-advances and fires onComplete", async () => {
    const done = vi.fn();
    render(<Harness onComplete={done} />);
    const user = userEvent.setup();
    await user.click(cells()[0]);
    await user.keyboard("123456");
    expect(screen.getByTestId("val")).toHaveTextContent("123456");
    expect(done).toHaveBeenCalledWith("123456");
  });

  it("rejects non-digits", async () => {
    render(<Harness />);
    const user = userEvent.setup();
    await user.click(cells()[0]);
    await user.keyboard("a1b2");
    expect(screen.getByTestId("val")).toHaveTextContent(/^12$/);
  });

  it("backspace removes the previous digit and moves focus back", async () => {
    render(<Harness />);
    const user = userEvent.setup();
    await user.click(cells()[0]);
    await user.keyboard("123");
    expect(document.activeElement).toBe(cells()[3]);
    await user.keyboard("{Backspace}");
    expect(screen.getByTestId("val")).toHaveTextContent(/^12$/);
    expect(document.activeElement).toBe(cells()[2]);
  });

  it("paste fills all cells and strips separators", () => {
    const done = vi.fn();
    render(<Harness onComplete={done} />);
    fireEvent.paste(cells()[2], { clipboardData: { getData: () => "Your code: 987-654" } });
    expect(screen.getByTestId("val")).toHaveTextContent("987654");
    expect(done).toHaveBeenCalledWith("987654");
  });

  it("PinInput masks digits and has 4 cells", async () => {
    function Pin() {
      const [v, setV] = React.useState("");
      return <PinInput value={v} onChange={setV} />;
    }
    const { container } = render(<Pin />);
    const inputs = container.querySelectorAll("input");
    expect(inputs).toHaveLength(4);
    inputs.forEach((i) => expect(i).toHaveAttribute("type", "password"));
    expect(screen.getByRole("group", { name: "Parent PIN" })).toBeInTheDocument();
  });
});
