import React, { useState } from "react";
import { adminService } from "../services/adminService";
import { Card, CardContent, CardHeader, CardTitle, Button, Badge } from "../components/ui/core";
import { Download, FileSpreadsheet, FileJson, ShieldCheck, CheckCircle2, Clock } from "lucide-react";

export function ExportsPage() {
  const [entity, setEntity] = useState<"users" | "families" | "children" | "devices">("users");
  const [format, setFormat] = useState<"csv" | "json">("csv");
  const [downloading, setDownloading] = useState(false);
  const [recentDownloads, setRecentDownloads] = useState<
    { id: string; entity: string; format: string; timestamp: string; rows: number }[]
  >([
    {
      id: "exp_users_prev",
      entity: "users",
      format: "csv",
      timestamp: new Date(Date.now() - 3600000).toLocaleTimeString(),
      rows: 1204,
    },
  ]);

  const handleGenerateExport = async () => {
    setDownloading(true);

    let dataToExport: any[] = [];
    if (entity === "users") {
      const users = await adminService.getUsers();
      dataToExport = users.map((u) => ({
        uid: u.uid,
        email: u.email,
        displayName: u.displayName,
        familyId: u.familyId,
        status: u.status,
        createdAt: u.createdAt,
        lastSignInAt: u.lastSignInAt,
      }));
    } else if (entity === "families") {
      const fams = await adminService.getFamilies();
      dataToExport = fams.map((f) => ({
        familyId: f.familyId,
        name: f.name,
        ownerEmail: f.ownerEmail,
        childCount: f.childCount,
        deviceCount: f.deviceCount,
        status: f.status,
        createdAt: f.createdAt,
      }));
    } else if (entity === "children") {
      const kids = await adminService.getAllChildren();
      dataToExport = kids.map((k) => ({
        childId: k.childId,
        displayName: k.displayName,
        ageBand: k.ageBand,
        familyId: k.familyId,
        dailyCeilingMinutes: k.policySummary.dailyCeilingMinutes,
        quizMode: k.policySummary.quizMode,
      }));
    } else if (entity === "devices") {
      const devs = await adminService.getAllDevices();
      dataToExport = devs.map((d) => ({
        deviceId: d.deviceId,
        model: d.model,
        osVersion: d.osVersion,
        appVersion: d.appVersion,
        childName: d.childName,
        revoked: d.revoked,
        lastSeenAt: d.lastSeenAt,
      }));
    }

    // Trigger Browser Download
    let blob: Blob;
    let filename = `meritscreen_${entity}_${new Date().toISOString().slice(0, 10)}.${format}`;

    if (format === "csv") {
      if (dataToExport.length > 0) {
        const headers = Object.keys(dataToExport[0]);
        const csvRows = [
          headers.join(","),
          ...dataToExport.map((row) =>
            headers.map((h) => JSON.stringify(row[h] ?? "")).join(",")
          ),
        ];
        blob = new Blob([csvRows.join("\n")], { type: "text/csv;charset=utf-8;" });
      } else {
        blob = new Blob([""], { type: "text/csv" });
      }
    } else {
      blob = new Blob([JSON.stringify(dataToExport, null, 2)], {
        type: "application/json;charset=utf-8;",
      });
    }

    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.setAttribute("download", filename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    setRecentDownloads((prev) => [
      {
        id: `exp_${Date.now()}`,
        entity,
        format,
        timestamp: new Date().toLocaleTimeString(),
        rows: dataToExport.length,
      },
      ...prev,
    ]);

    setDownloading(false);
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Compliance & Data Export Center</h1>
        <p className="text-sm text-muted-foreground">
          Generate sanitized CSV and JSON datasets for business intelligence, audits, and reporting.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        {/* Export Wizard Form (2 cols) */}
        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle>Export Configuration Wizard</CardTitle>
            <p className="text-xs text-muted-foreground">
              Select collection and target serialization format
            </p>
          </CardHeader>
          <CardContent className="space-y-6">
            {/* Entity Selector */}
            <div className="space-y-2">
              <label className="text-xs font-semibold text-foreground uppercase tracking-wider block">
                1. Target Entity Collection
              </label>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {[
                  { id: "users", label: "Parents / Users", desc: "Accounts & status" },
                  { id: "families", label: "Families", desc: "Clusters & owners" },
                  { id: "children", label: "Children", desc: "Age bands & policy" },
                  { id: "devices", label: "Devices", desc: "Hardware & liveness" },
                ].map((item) => (
                  <button
                    key={item.id}
                    onClick={() => setEntity(item.id as any)}
                    className={`rounded-lg border p-3 text-left transition-all ${
                      entity === item.id
                        ? "border-primary bg-primary/5 text-foreground shadow-sm"
                        : "border-border hover:bg-muted/30 text-muted-foreground"
                    }`}
                  >
                    <div className="text-sm font-semibold text-foreground">{item.label}</div>
                    <div className="text-[11px] text-muted-foreground mt-0.5">{item.desc}</div>
                  </button>
                ))}
              </div>
            </div>

            {/* Format Selector */}
            <div className="space-y-2">
              <label className="text-xs font-semibold text-foreground uppercase tracking-wider block">
                2. Output File Format
              </label>
              <div className="grid grid-cols-2 gap-3 max-w-sm">
                <button
                  onClick={() => setFormat("csv")}
                  className={`flex items-center gap-2 rounded-lg border p-3 text-left transition-all ${
                    format === "csv"
                      ? "border-primary bg-primary/5 text-foreground shadow-sm"
                      : "border-border hover:bg-muted/30 text-muted-foreground"
                  }`}
                >
                  <FileSpreadsheet className="h-5 w-5 text-emerald-500" />
                  <div>
                    <div className="text-sm font-semibold">CSV Spreadsheet</div>
                    <div className="text-[11px] text-muted-foreground">For Excel / BI</div>
                  </div>
                </button>

                <button
                  onClick={() => setFormat("json")}
                  className={`flex items-center gap-2 rounded-lg border p-3 text-left transition-all ${
                    format === "json"
                      ? "border-primary bg-primary/5 text-foreground shadow-sm"
                      : "border-border hover:bg-muted/30 text-muted-foreground"
                  }`}
                >
                  <FileJson className="h-5 w-5 text-indigo-500" />
                  <div>
                    <div className="text-sm font-semibold">JSON Payload</div>
                    <div className="text-[11px] text-muted-foreground">For API ingestion</div>
                  </div>
                </button>
              </div>
            </div>

            {/* Privacy Redaction Notice */}
            <div className="p-3.5 rounded-lg border border-emerald-500/20 bg-emerald-500/5 text-xs text-muted-foreground flex gap-2.5">
              <ShieldCheck className="h-4 w-4 text-emerald-500 shrink-0 mt-0.5" />
              <div>
                <strong className="text-foreground">Automated Privacy Sanitization:</strong> Exports automatically exclude internal Parent PIN hashes, pairing secrets, push notification tokens (`fcmToken`), and session tokens.
              </div>
            </div>

            <Button
              onClick={handleGenerateExport}
              disabled={downloading}
              className="w-full sm:w-auto gap-2"
            >
              <Download className="h-4 w-4" />
              {downloading ? "Preparing Export..." : `Download ${entity.toUpperCase()} (${format.toUpperCase()})`}
            </Button>
          </CardContent>
        </Card>

        {/* Recent Exports Activity */}
        <Card>
          <CardHeader>
            <CardTitle>Session Downloads</CardTitle>
            <p className="text-xs text-muted-foreground">Recent generated files</p>
          </CardHeader>
          <CardContent className="space-y-3">
            {recentDownloads.map((item) => (
              <div
                key={item.id}
                className="flex items-center justify-between p-3 rounded-lg border border-border bg-muted/20 text-xs"
              >
                <div>
                  <div className="font-semibold uppercase tracking-wide text-foreground">
                    {item.entity} ({item.format})
                  </div>
                  <div className="text-muted-foreground flex items-center gap-1 mt-0.5">
                    <Clock className="h-3 w-3" />
                    Generated at {item.timestamp}
                  </div>
                </div>
                <Badge variant="secondary">{item.rows} records</Badge>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
