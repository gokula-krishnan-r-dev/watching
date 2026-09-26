import React from "react";
import { useAuth } from "../../context/AuthContext";
import { useTheme } from "../theme/ThemeProvider";
import { Button } from "../ui/core";
import { Sun, Moon, LogOut, ShieldCheck, Menu } from "lucide-react";

interface TopbarProps {
  onToggleSidebar?: () => void;
}

export function Topbar({ onToggleSidebar }: TopbarProps) {
  const { user, logout } = useAuth();
  const { theme, setTheme } = useTheme();

  const toggleTheme = () => {
    setTheme(theme === "dark" ? "light" : "dark");
  };

  return (
    <header className="sticky top-0 z-40 flex h-16 w-full items-center justify-between border-b border-border bg-card/60 px-6 backdrop-blur-md">
      <div className="flex items-center gap-4">
        {onToggleSidebar && (
          <Button
            variant="ghost"
            size="icon"
            onClick={onToggleSidebar}
            className="text-muted-foreground"
          >
            <Menu className="h-5 w-5" />
          </Button>
        )}
        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1.5 rounded-md border border-emerald-500/20 bg-emerald-500/10 px-2 py-0.5 text-xs font-medium text-emerald-600 dark:text-emerald-400">
            <ShieldCheck className="h-3.5 w-3.5" />
            Super Admin Gate
          </span>
          <span className="text-xs text-muted-foreground hidden sm:inline">
            Firebase Project: <strong className="font-semibold text-foreground">managing-screen-time</strong>
          </span>
        </div>
      </div>

      <div className="flex items-center gap-3">
        {/* Dark/Light toggle */}
        <Button
          variant="outline"
          size="icon"
          onClick={toggleTheme}
          title="Toggle Light / Dark mode"
          className="rounded-lg h-9 w-9 border-border"
        >
          {theme === "dark" ? (
            <Sun className="h-4 w-4 text-amber-400 transition-transform rotate-0" />
          ) : (
            <Moon className="h-4 w-4 text-slate-700 transition-transform" />
          )}
        </Button>

        {/* User Operator Menu */}
        <div className="flex items-center gap-3 pl-2 border-l border-border">
          <div className="hidden text-right md:block">
            <div className="text-xs font-semibold text-foreground">{user?.displayName || "Operator"}</div>
            <div className="text-[11px] text-muted-foreground">{user?.email}</div>
          </div>
          <Button
            variant="ghost"
            size="icon"
            onClick={logout}
            title="Sign out"
            className="text-muted-foreground hover:text-destructive hover:bg-destructive/10"
          >
            <LogOut className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </header>
  );
}
