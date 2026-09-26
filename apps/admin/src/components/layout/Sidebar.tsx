import React from "react";
import { NavLink } from "react-router-dom";
import { cn } from "../../lib/utils";
import {
  LayoutDashboard,
  Users,
  Home,
  Baby,
  Smartphone,
  BarChart3,
  Download,
  ScrollText,
  ShieldAlert,
  Shield,
} from "lucide-react";

interface SidebarProps {
  collapsed?: boolean;
}

const navItems = [
  { label: "Dashboard", to: "/", icon: LayoutDashboard },
  { label: "Users & Parents", to: "/users", icon: Users },
  { label: "Families", to: "/families", icon: Home },
  { label: "Children", to: "/children", icon: Baby },
  { label: "Devices", to: "/devices", icon: Smartphone },
  { label: "Analytics", to: "/analytics", icon: BarChart3 },
  { label: "Data Exports", to: "/exports", icon: Download },
  { label: "Audit Logs", to: "/audit", icon: ScrollText },
  { label: "Operators & IAM", to: "/operators", icon: ShieldAlert },
];

export function Sidebar({ collapsed = false }: SidebarProps) {
  return (
    <aside
      className={cn(
        "flex flex-col border-r border-border bg-card/50 backdrop-blur-md transition-all duration-200 select-none",
        collapsed ? "w-16" : "w-64"
      )}
    >
      {/* Brand Header */}
      <div className="flex h-16 items-center px-4 border-b border-border/80 gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-md shadow-primary/20">
          <Shield className="h-5 w-5" />
        </div>
        {!collapsed && (
          <div className="flex flex-col">
            <span className="font-bold tracking-tight text-foreground text-sm">
              MeritScreen
            </span>
            <span className="text-[10px] font-semibold text-primary uppercase tracking-wider">
              Super Admin Console
            </span>
          </div>
        )}
      </div>

      {/* Nav items */}
      <div className="flex-1 py-4 px-2 space-y-1 overflow-y-auto">
        <div className="px-3 py-1 text-[11px] font-medium text-muted-foreground uppercase tracking-wider">
          {!collapsed && "Platform Ops"}
        </div>
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all",
                  isActive
                    ? "bg-primary text-primary-foreground shadow-sm shadow-primary/25"
                    : "text-muted-foreground hover:bg-muted/70 hover:text-foreground"
                )
              }
            >
              <Icon className="h-4 w-4 shrink-0" />
              {!collapsed && <span>{item.label}</span>}
            </NavLink>
          );
        })}
      </div>

      {/* Environment / Security Badge */}
      {!collapsed && (
        <div className="p-3 m-3 rounded-xl border border-border/60 bg-muted/40 text-xs">
          <div className="flex items-center gap-2 font-medium text-foreground">
            <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
            Security Claim: Active
          </div>
          <p className="mt-1 text-[11px] text-muted-foreground leading-relaxed">
            Zero PII exposure. PINs and pairing secrets are redacted server-side.
          </p>
        </div>
      )}
    </aside>
  );
}
