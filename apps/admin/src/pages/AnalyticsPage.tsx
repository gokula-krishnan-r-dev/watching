import React, { useEffect, useState } from "react";
import { adminService } from "../services/adminService";
import { Card, CardContent, CardHeader, CardTitle, Button, Badge } from "../components/ui/core";
import {
  TrendingUp,
  BarChart2,
  PieChart as PieChartIcon,
  Calendar,
  Layers,
  ArrowRight,
  Filter,
} from "lucide-react";
import {
  ResponsiveContainer,
  LineChart,
  Line,
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

export function AnalyticsPage() {
  const [rangeDays, setRangeDays] = useState(30);
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    async function load() {
      setLoading(true);
      try {
        const res = await adminService.getAnalytics(rangeDays);
        if (mounted) {
          setData(res);
        }
      } catch (err) {
        console.error("Error in AnalyticsPage load:", err);
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
  }, [rangeDays]);

  if (loading || !data || !data.line) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header & Range Filters */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Deep Product Analytics</h1>
          <p className="text-sm text-muted-foreground">
            Multi-metric time series, learning engagement, and onboarding funnel analytics.
          </p>
        </div>

        {/* Date Range Selector */}
        <div className="flex items-center gap-1.5 rounded-lg border border-border bg-card p-1">
          {[7, 14, 30, 90].map((d) => (
            <button
              key={d}
              onClick={() => setRangeDays(d)}
              className={`rounded-md px-3 py-1 text-xs font-semibold transition-colors ${
                rangeDays === d
                  ? "bg-primary text-primary-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              {d} Days
            </button>
          ))}
        </div>
      </div>

      {/* Main Multi-Metric Time Series (Line Chart) */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Acquisition & Quiz Engagement Over Time</CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5">
                Daily new parent signups vs. daily quiz completions ({rangeDays}d trend)
              </p>
            </div>
            <Badge variant="outline" className="gap-1">
              <TrendingUp className="h-3.5 w-3.5 text-primary" />
              High Growth
            </Badge>
          </div>
        </CardHeader>
        <CardContent>
          <div className="h-[320px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={data.line} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis yAxisId="left" tick={{ fontSize: 11 }} />
                <YAxis yAxisId="right" orientation="right" tick={{ fontSize: 11 }} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: "hsl(var(--card))",
                    borderColor: "hsl(var(--border))",
                    borderRadius: "8px",
                    fontSize: "12px",
                  }}
                />
                <Legend wrapperStyle={{ fontSize: "12px", paddingTop: "10px" }} />
                <Line
                  yAxisId="left"
                  type="monotone"
                  dataKey="newParents"
                  name="New Parents"
                  stroke="#6366f1"
                  strokeWidth={2.5}
                  dot={false}
                />
                <Line
                  yAxisId="left"
                  type="monotone"
                  dataKey="newFamilies"
                  name="New Families"
                  stroke="#10b981"
                  strokeWidth={2}
                  strokeDasharray="4 4"
                  dot={false}
                />
                <Line
                  yAxisId="right"
                  type="monotone"
                  dataKey="quizAttempts"
                  name="Quiz Attempts"
                  stroke="#f59e0b"
                  strokeWidth={2}
                  dot={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      {/* Funnel & Policy Modes */}
      <div className="grid gap-6 md:grid-cols-2">
        {/* Onboarding Conversion Funnel */}
        <Card>
          <CardHeader>
            <CardTitle>Onboarding & Pairing Funnel</CardTitle>
            <p className="text-xs text-muted-foreground">
              Conversion from initial parent signup to active launcher usage
            </p>
          </CardHeader>
          <CardContent className="space-y-3">
            {data.funnel.map((step: any, i: number) => (
              <div key={step.stage} className="space-y-1">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-semibold text-foreground flex items-center gap-1.5">
                    <span className="h-5 w-5 rounded-full bg-primary/10 text-primary flex items-center justify-center text-[10px]">
                      {i + 1}
                    </span>
                    {step.stage}
                  </span>
                  <div className="flex items-center gap-2">
                    <span className="text-muted-foreground">{step.count.toLocaleString()}</span>
                    <Badge variant="secondary" className="text-[10px]">{step.rate}</Badge>
                  </div>
                </div>
                <div className="h-2 w-full overflow-hidden rounded-full bg-muted/50">
                  <div
                    className="h-full rounded-full bg-primary transition-all duration-500"
                    style={{ width: step.rate }}
                  />
                </div>
              </div>
            ))}
          </CardContent>
        </Card>

        {/* Policy Modes Distribution (Pie Chart) */}
        <Card>
          <CardHeader>
            <CardTitle>Parent Policy Mode Distribution</CardTitle>
            <p className="text-xs text-muted-foreground">
              Active operating enforcement configurations across families
            </p>
          </CardHeader>
          <CardContent>
            <div className="h-[220px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={data.policyModes}
                    cx="50%"
                    cy="50%"
                    outerRadius={75}
                    dataKey="count"
                    label={({ name, percent }) => `${name} (${(percent * 100).toFixed(0)}%)`}
                    labelLine={false}
                  >
                    {data.policyModes.map((entry: any, index: number) => (
                      <Cell key={`mode-${index}`} fill={entry.fill} />
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
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="mt-2 text-center text-xs text-muted-foreground">
              <strong>Earn Minutes Quiz</strong> is the most favored mode by parents (49% adoption).
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
