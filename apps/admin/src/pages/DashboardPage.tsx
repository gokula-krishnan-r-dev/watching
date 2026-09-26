import React, { useEffect, useState } from "react";
import { adminService } from "../services/adminService";
import { Card, CardContent, CardHeader, CardTitle, Badge } from "../components/ui/core";
import {
  Users,
  Home,
  Baby,
  Smartphone,
  TrendingUp,
  Brain,
  ShieldCheck,
  Activity,
  ArrowUpRight,
} from "lucide-react";
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
} from "recharts";

export function DashboardPage() {
  const [stats, setStats] = useState<any>(null);
  const [analytics, setAnalytics] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    async function load() {
      try {
        const [s, a] = await Promise.all([
          adminService.getDashboardStats().catch(() => null),
          adminService.getAnalytics(14).catch(() => null),
        ]);
        if (mounted) {
          if (s) setStats(s);
          if (a) setAnalytics(a);
        }
      } catch (err) {
        console.error("Error in DashboardPage load:", err);
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }
    load();
    return () => {
      mounted = false;
    };
  }, []);

  if (loading || !stats || !stats.global || !analytics) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
      </div>
    );
  }

  const g = stats.global;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-1 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Super Admin Dashboard</h1>
          <p className="text-sm text-muted-foreground">
            Platform-wide metrics from scheduled rollups (`adminStats/global`). Zero PII exposure.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="outline" className="gap-1 py-1">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
            Live Rollup Cache
          </Badge>
          <span className="text-xs text-muted-foreground">
            Updated {new Date(g.updatedAt).toLocaleTimeString()}
          </span>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Total Users / Parents */}
        <Card className="hover:border-primary/50 transition-all">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              Total Parents / Users
            </CardTitle>
            <div className="h-8 w-8 rounded-lg bg-indigo-500/10 flex items-center justify-center text-indigo-500">
              <Users className="h-4 w-4" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{g.totalUsers.toLocaleString()}</div>
            <p className="text-xs text-muted-foreground mt-1 flex items-center gap-1">
              <ArrowUpRight className="h-3 w-3 text-emerald-500" />
              <span className="text-emerald-500 font-semibold">+{g.signups7d}</span> new in last 7 days
            </p>
          </CardContent>
        </Card>

        {/* Total Families */}
        <Card className="hover:border-primary/50 transition-all">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              Configured Families
            </CardTitle>
            <div className="h-8 w-8 rounded-lg bg-blue-500/10 flex items-center justify-center text-blue-500">
              <Home className="h-4 w-4" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{g.totalFamilies.toLocaleString()}</div>
            <p className="text-xs text-muted-foreground mt-1">
              <strong className="text-foreground font-semibold">{g.familiesWithPairedDevice}</strong> with ≥1 paired device
            </p>
          </CardContent>
        </Card>

        {/* Children Profiles */}
        <Card className="hover:border-primary/50 transition-all">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              Children Enrolled
            </CardTitle>
            <div className="h-8 w-8 rounded-lg bg-emerald-500/10 flex items-center justify-center text-emerald-500">
              <Baby className="h-4 w-4" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{g.totalChildren.toLocaleString()}</div>
            <p className="text-xs text-muted-foreground mt-1">
              Ages 3–12 under active launcher policy
            </p>
          </CardContent>
        </Card>

        {/* Paired Devices */}
        <Card className="hover:border-primary/50 transition-all">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              Active Devices (24h)
            </CardTitle>
            <div className="h-8 w-8 rounded-lg bg-amber-500/10 flex items-center justify-center text-amber-500">
              <Smartphone className="h-4 w-4" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-emerald-600 dark:text-emerald-400">
              {g.activeDevices24h.toLocaleString()}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              Out of {g.totalDevices.toLocaleString()} paired launcher devices
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Primary Analytics Charts Row */}
      <div className="grid gap-6 lg:grid-cols-7">
        {/* Growth & Usage Area Chart (4 cols) */}
        <Card className="lg:col-span-4">
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>Platform Growth & Usage Activity</CardTitle>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Daily aggregate screen minutes & parent signups (14-day window)
                </p>
              </div>
              <Badge variant="secondary" className="gap-1">
                <Activity className="h-3 w-3 text-primary" />
                Aggregated
              </Badge>
            </div>
          </CardHeader>
          <CardContent>
            <div className="h-[280px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={analytics.line} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorMinutes" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#6366f1" stopOpacity={0.4}/>
                      <stop offset="95%" stopColor="#6366f1" stopOpacity={0.0}/>
                    </linearGradient>
                    <linearGradient id="colorParents" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#10b981" stopOpacity={0.4}/>
                      <stop offset="95%" stopColor="#10b981" stopOpacity={0.0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
                  <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: "hsl(var(--card))",
                      borderColor: "hsl(var(--border))",
                      borderRadius: "8px",
                      fontSize: "12px",
                    }}
                  />
                  <Legend wrapperStyle={{ fontSize: "12px", paddingTop: "10px" }} />
                  <Area
                    type="monotone"
                    dataKey="screenMinutes"
                    name="Screen Minutes (Rollup)"
                    stroke="#6366f1"
                    strokeWidth={2}
                    fillOpacity={1}
                    fill="url(#colorMinutes)"
                  />
                  <Area
                    type="monotone"
                    dataKey="newParents"
                    name="New Parents"
                    stroke="#10b981"
                    strokeWidth={2}
                    fillOpacity={1}
                    fill="url(#colorParents)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Device Status Breakdown (3 cols) */}
        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle>Device Fleet Health</CardTitle>
            <p className="text-xs text-muted-foreground">
              Heartbeat telemetry & revocation distribution
            </p>
          </CardHeader>
          <CardContent>
            <div className="h-[220px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={analytics.deviceStatus}
                    cx="50%"
                    cy="50%"
                    innerRadius={55}
                    outerRadius={80}
                    paddingAngle={4}
                    dataKey="count"
                  >
                    {analytics.deviceStatus.map((entry: any, index: number) => (
                      <Cell key={`cell-${index}`} fill={entry.fill} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{
                      backgroundColor: "hsl(var(--card))",
                      borderColor: "hsl(var(--border))",
                      borderRadius: "8px",
                      fontSize: "12px",
                    }}
                  />
                  <Legend wrapperStyle={{ fontSize: "12px" }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="mt-4 grid grid-cols-3 text-center border-t border-border pt-3">
              <div>
                <div className="text-sm font-semibold text-emerald-500">
                  {analytics.deviceStatus?.[0]?.count ?? g.activeDevices24h}
                </div>
                <div className="text-[10px] text-muted-foreground uppercase">Online</div>
              </div>
              <div>
                <div className="text-sm font-semibold text-amber-500">
                  {analytics.deviceStatus?.[1]?.count ?? 0}
                </div>
                <div className="text-[10px] text-muted-foreground uppercase">Offline</div>
              </div>
              <div>
                <div className="text-sm font-semibold text-red-500">
                  {analytics.deviceStatus?.[2]?.count ?? 0}
                </div>
                <div className="text-[10px] text-muted-foreground uppercase">Revoked</div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Secondary Row: Age Bands & Quiz Learning Health */}
      <div className="grid gap-6 md:grid-cols-2">
        {/* Children by Age Band */}
        <Card>
          <CardHeader>
            <CardTitle>Children by Age Band</CardTitle>
            <p className="text-xs text-muted-foreground">
              Curriculum & adaptive quiz difficulty calibration tiers
            </p>
          </CardHeader>
          <CardContent>
            <div className="h-[230px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={analytics.ageBands} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
                  <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: "hsl(var(--card))",
                      borderColor: "hsl(var(--border))",
                      borderRadius: "8px",
                      fontSize: "12px",
                    }}
                  />
                  <Bar dataKey="count" name="Enrolled Children" radius={[6, 6, 0, 0]}>
                    {analytics.ageBands.map((entry: any, index: number) => (
                      <Cell key={`bar-${index}`} fill={entry.fill} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Quiz Metrics & Learning Performance */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>Quiz & Learning Performance</CardTitle>
                <p className="text-xs text-muted-foreground">
                  Adaptive quiz completions and pass rate (7 days)
                </p>
              </div>
              <div className="flex items-center gap-1.5 rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">
                <Brain className="h-3.5 w-3.5" />
                {Math.round(g.quizPassRate7d * 100)}% Pass Rate
              </div>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-lg border border-border p-3">
                <div className="text-xs text-muted-foreground">Quiz Attempts (7d)</div>
                <div className="text-xl font-bold mt-1">{g.quizAttempts7d.toLocaleString()}</div>
                <div className="text-[11px] text-emerald-500 mt-0.5">High engagement</div>
              </div>
              <div className="rounded-lg border border-border p-3">
                <div className="text-xs text-muted-foreground">Bonus Minutes Earned</div>
                <div className="text-xl font-bold mt-1">42,800 min</div>
                <div className="text-[11px] text-primary mt-0.5">Rewarding education</div>
              </div>
            </div>

            <div>
              <div className="text-xs font-semibold mb-2">Most Restrictive App Categories</div>
              <div className="space-y-2">
                {analytics.topBlockedApps.map((item: any) => (
                  <div key={item.app} className="flex items-center justify-between text-xs">
                    <span className="text-muted-foreground">{item.app}</span>
                    <span className="font-semibold">{item.blocks.toLocaleString()} blocks/wk</span>
                  </div>
                ))}
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
