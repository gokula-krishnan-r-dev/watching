import React, { useEffect, useState } from "react";
import { adminService } from "../services/adminService";
import { DeviceItem } from "../data/mockStore";
import { Card, CardContent, Button, Input, Badge } from "../components/ui/core";
import { Modal } from "../components/ui/Modal";
import { formatDate, timeAgo } from "../lib/utils";
import { Search, Smartphone, Ban, Download, CheckCircle, Battery, ShieldAlert } from "lucide-react";

export function DevicesPage() {
  const [devices, setDevices] = useState<DeviceItem[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);

  // Selected device detail modal
  const [selectedDevice, setSelectedDevice] = useState<DeviceItem | null>(null);

  // Revoke modal
  const [revokeModal, setRevokeModal] = useState<DeviceItem | null>(null);

  const loadDevices = async () => {
    setLoading(true);
    try {
      const data = await adminService.getAllDevices(search);
      setDevices(data);
      if (selectedDevice) {
        const updated = data.find((d) => d.deviceId === selectedDevice.deviceId);
        if (updated) setSelectedDevice(updated);
      }
    } catch (err) {
      console.error("Failed to load devices:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDevices();
  }, [search]);

  const handleRevoke = async () => {
    if (!revokeModal) return;
    await adminService.revokeDevice(revokeModal.deviceId, "current_superadmin");
    setRevokeModal(null);
    loadDevices();
  };

  const handleExportCSV = () => {
    const headers = ["Device ID", "Model", "OS Version", "App Version", "Child Name", "Family Name", "Battery %", "Revoked", "Last Seen At"];
    const rows = devices.map((d) => [
      d.deviceId,
      `"${d.model}"`,
      `"${d.osVersion}"`,
      d.appVersion,
      `"${d.childName}"`,
      `"${d.familyName}"`,
      d.batteryPercent ?? "",
      d.revoked ? "Yes" : "No",
      d.lastSeenAt,
    ]);

    const csvContent = "data:text/csv;charset=utf-8," + [headers.join(","), ...rows.map((r) => r.join(","))].join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `meritscreen_devices_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Launcher Device Fleet</h1>
          <p className="text-sm text-muted-foreground">
            Inventory of paired Android tablets and phones running MeritScreen Launcher.
          </p>
        </div>
        <Button onClick={handleExportCSV} variant="outline" className="gap-2 shrink-0">
          <Download className="h-4 w-4" />
          Export Fleet CSV
        </Button>
      </div>

      {/* Filter and Search Bar */}
      <Card>
        <CardContent className="p-4 flex flex-col sm:flex-row gap-3 items-center justify-between">
          <div className="relative w-full sm:w-80">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search by device model, device ID, or family..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>
          <div className="text-xs text-muted-foreground">
            Showing <strong className="text-foreground">{devices.length}</strong> registered hardware units
          </div>
        </CardContent>
      </Card>

      {/* Devices Table */}
      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-muted/30 text-xs font-semibold uppercase text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Hardware Model & ID</th>
                <th className="px-4 py-3">Assigned Child & Family</th>
                <th className="px-4 py-3">OS & App Version</th>
                <th className="px-4 py-3">Telemetry Status</th>
                <th className="px-4 py-3">Last Heartbeat</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-muted-foreground">
                    Loading devices...
                  </td>
                </tr>
              ) : devices.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-muted-foreground">
                    No devices found.
                  </td>
                </tr>
              ) : (
                devices.map((d) => (
                  <tr
                    key={d.deviceId}
                    onClick={() => setSelectedDevice(d)}
                    className="hover:bg-muted/50 transition-colors cursor-pointer group"
                  >
                    <td className="px-4 py-3">
                      <div className="font-semibold text-foreground flex items-center gap-2 group-hover:text-primary transition-colors">
                        <Smartphone className="h-4 w-4 text-primary" />
                        {d.model}
                      </div>
                      <div className="text-xs text-muted-foreground font-mono">{d.deviceId}</div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="text-xs font-semibold text-foreground">{d.childName}</div>
                      <div className="text-[11px] text-muted-foreground">{d.familyName}</div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="text-xs text-foreground font-medium">{d.osVersion}</div>
                      <div className="text-[11px] text-muted-foreground font-mono">v{d.appVersion}</div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        {d.revoked ? (
                          <Badge variant="destructive">Revoked</Badge>
                        ) : (
                          <Badge variant="success">Active</Badge>
                        )}
                        {d.batteryPercent !== null && (
                          <span className="flex items-center gap-1 text-xs text-muted-foreground">
                            <Battery className="h-3 w-3" />
                            {d.batteryPercent}%
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">
                      <div>{timeAgo(d.lastSeenAt)}</div>
                      <div className="text-[10px] text-muted-foreground/80">{formatDate(d.lastSeenAt)}</div>
                    </td>
                    <td className="px-4 py-3 text-right" onClick={(e) => e.stopPropagation()}>
                      {!d.revoked ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:bg-destructive/10 gap-1 text-xs"
                          onClick={() => setRevokeModal(d)}
                        >
                          <Ban className="h-3.5 w-3.5" />
                          Revoke
                        </Button>
                      ) : (
                        <span className="text-xs text-muted-foreground italic">Revoked</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Device Details Modal */}
      {selectedDevice && (
        <Modal
          isOpen={true}
          onClose={() => setSelectedDevice(null)}
          title={`Device Telemetry: ${selectedDevice.model}`}
          description={`ID: ${selectedDevice.deviceId} • Child: ${selectedDevice.childName}`}
          footer={
            <div className="flex items-center justify-between w-full">
              {!selectedDevice.revoked ? (
                <Button
                  variant="outline"
                  size="sm"
                  className="text-destructive border-destructive/30 hover:bg-destructive/10"
                  onClick={() => {
                    const target = selectedDevice;
                    setSelectedDevice(null);
                    setRevokeModal(target);
                  }}
                >
                  <Ban className="h-3.5 w-3.5 mr-1" />
                  Revoke Device
                </Button>
              ) : (
                <span className="text-xs text-destructive font-semibold">Device is Revoked</span>
              )}
              <Button onClick={() => setSelectedDevice(null)}>Close</Button>
            </div>
          }
        >
          <div className="space-y-4 text-xs">
            <div className="grid grid-cols-2 gap-3">
              <div className="p-3 rounded-lg border border-border bg-muted/20">
                <span className="text-muted-foreground block mb-0.5">Assigned Child Profile</span>
                <strong className="text-sm font-semibold">{selectedDevice.childName}</strong>
                <span className="text-[10px] text-muted-foreground font-mono block mt-0.5">{selectedDevice.childId}</span>
              </div>
              <div className="p-3 rounded-lg border border-border bg-muted/20">
                <span className="text-muted-foreground block mb-0.5">Family Unit</span>
                <strong className="text-sm font-semibold">{selectedDevice.familyName}</strong>
                <span className="text-[10px] text-muted-foreground font-mono block mt-0.5">{selectedDevice.familyId}</span>
              </div>
              <div className="p-3 rounded-lg border border-border bg-muted/20">
                <span className="text-muted-foreground block mb-0.5">Android OS & Patch</span>
                <strong className="text-sm font-semibold">{selectedDevice.osVersion}</strong>
              </div>
              <div className="p-3 rounded-lg border border-border bg-muted/20">
                <span className="text-muted-foreground block mb-0.5">Launcher App Version</span>
                <strong className="text-sm font-semibold font-mono">v{selectedDevice.appVersion}</strong>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2 p-3 rounded-lg border border-border bg-card text-center">
              <div>
                <span className="text-[10px] text-muted-foreground uppercase block">Battery</span>
                <span className="text-sm font-bold text-foreground">
                  {selectedDevice.batteryPercent !== null ? `${selectedDevice.batteryPercent}%` : "Unknown"}
                </span>
              </div>
              <div>
                <span className="text-[10px] text-muted-foreground uppercase block">Default Launcher</span>
                <span className="text-sm font-bold text-foreground">
                  {selectedDevice.launcherDefault ? "Active" : "Unset"}
                </span>
              </div>
              <div>
                <span className="text-[10px] text-muted-foreground uppercase block">Status</span>
                <span className="text-sm font-bold">
                  {selectedDevice.revoked ? (
                    <span className="text-destructive">Revoked</span>
                  ) : (
                    <span className="text-emerald-500">Authorized</span>
                  )}
                </span>
              </div>
            </div>

            <div className="p-3 rounded-lg border border-border bg-muted/30">
              <span className="text-muted-foreground block text-[11px]">Heartbeat Telemetry</span>
              <span className="font-semibold text-foreground block">{formatDate(selectedDevice.lastSeenAt)}</span>
              <span className="text-[10px] text-muted-foreground">Recorded {timeAgo(selectedDevice.lastSeenAt)}</span>
            </div>
          </div>
        </Modal>
      )}

      {/* Revoke Modal */}
      {revokeModal && (
        <Modal
          isOpen={true}
          onClose={() => setRevokeModal(null)}
          title="Force Revoke Device Access?"
          description={`Device: ${revokeModal.model} (${revokeModal.deviceId})`}
          footer={
            <>
              <Button variant="outline" onClick={() => setRevokeModal(null)}>Cancel</Button>
              <Button variant="destructive" onClick={handleRevoke}>Confirm Revocation</Button>
            </>
          }
        >
          <div className="space-y-3">
            <div className="p-3 rounded-lg border border-amber-500/20 bg-amber-500/10 text-amber-600 dark:text-amber-400 text-xs flex gap-2">
              <ShieldAlert className="h-4 w-4 shrink-0 mt-0.5" />
              <div>
                Revoking this device will mark <code className="font-mono">revoked: true</code>, invalidate its child device token, clear its push token, and prevent it from pulling policy updates.
              </div>
            </div>
            <p className="text-xs text-muted-foreground">
              Assigned child: <strong className="text-foreground">{revokeModal.childName}</strong> ({revokeModal.familyName})
            </p>
          </div>
        </Modal>
      )}
    </div>
  );
}
