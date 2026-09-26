import React, { useEffect, useState } from "react";
import { adminService } from "../services/adminService";
import { AuditLogItem } from "../data/mockStore";
import { Card, CardContent, Button, Badge } from "../components/ui/core";
import { formatDate } from "../lib/utils";
import { ScrollText, ShieldAlert, Clock, RefreshCw } from "lucide-react";

export function AuditPage() {
  const [logs, setLogs] = useState<AuditLogItem[]>([]);
  const [loading, setLoading] = useState(true);

  const loadLogs = async () => {
    setLoading(true);
    const data = await adminService.getAuditLogs(100);
    setLogs(data);
    setLoading(false);
  };

  useEffect(() => {
    loadLogs();
  }, []);

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Security & Admin Audit Trail</h1>
          <p className="text-sm text-muted-foreground">
            Immutable log of all administrative interventions, account updates, deletions, and claims grants.
          </p>
        </div>
        <Button onClick={loadLogs} variant="outline" size="sm" className="gap-2">
          <RefreshCw className="h-3.5 w-3.5" />
          Refresh Log
        </Button>
      </div>

      {/* Logs Table */}
      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-muted/30 text-xs font-semibold uppercase text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Timestamp</th>
                <th className="px-4 py-3">Operator (Actor)</th>
                <th className="px-4 py-3">Action Type</th>
                <th className="px-4 py-3">Target Entity</th>
                <th className="px-4 py-3">Context / Metadata</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loading ? (
                <tr>
                  <td colSpan={5} className="py-12 text-center text-muted-foreground">
                    Loading audit trail...
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-12 text-center text-muted-foreground">
                    No audit records logged yet.
                  </td>
                </tr>
              ) : (
                logs.map((log) => (
                  <tr key={log.id} className="hover:bg-muted/40 transition-colors">
                    <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
                      {formatDate(log.createdAt)}
                    </td>
                    <td className="px-4 py-3">
                      <div className="font-semibold text-xs text-foreground">{log.actorEmail}</div>
                      <div className="text-[11px] text-muted-foreground font-mono">{log.actorUid}</div>
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant="outline" className="font-mono text-xs">
                        {log.action}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">
                      <span className="text-xs capitalize font-medium text-foreground">
                        {log.targetType}:{" "}
                      </span>
                      <span className="text-xs font-mono text-muted-foreground">
                        {log.targetId}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs font-mono text-muted-foreground max-w-xs truncate">
                      {JSON.stringify(log.metadata)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
