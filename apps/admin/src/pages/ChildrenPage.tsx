import React, { useEffect, useState } from "react";
import { adminService } from "../services/adminService";
import { ChildItem } from "../data/mockStore";
import { Card, CardContent, Button, Input, Badge } from "../components/ui/core";
import { Modal } from "../components/ui/Modal";
import { Search, Baby, Smartphone, Trash2, Download, ShieldCheck, BookOpen, Clock } from "lucide-react";

export function ChildrenPage() {
  const [children, setChildren] = useState<ChildItem[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);

  // Selected child detail modal
  const [selectedChild, setSelectedChild] = useState<ChildItem | null>(null);

  // Real-time policy editing modal
  const [editingChild, setEditingChild] = useState<{
    familyId: string;
    child: ChildItem;
    displayName: string;
    ageBand: "AGE_3_TO_6" | "AGE_7_TO_9" | "AGE_10_TO_12";
    dailyCeilingMinutes: number;
    quizMode: "app_block" | "earn_minutes" | "curfew";
    aiQuizzesEnabled: boolean;
  } | null>(null);
  const [savingChild, setSavingChild] = useState(false);

  // Delete modal
  const [deleteModal, setDeleteModal] = useState<ChildItem | null>(null);

  const loadChildren = async () => {
    setLoading(true);
    try {
      const data = await adminService.getAllChildren(search);
      setChildren(data);
      if (selectedChild) {
        const updated = data.find((c) => c.childId === selectedChild.childId);
        if (updated) setSelectedChild(updated);
      }
    } catch (err) {
      console.error("Failed to load children:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadChildren();
  }, [search]);

  const handleOpenEditChild = (child: ChildItem) => {
    setEditingChild({
      familyId: child.familyId,
      child,
      displayName: child.displayName,
      ageBand: child.ageBand,
      dailyCeilingMinutes: child.policySummary.dailyCeilingMinutes,
      quizMode: child.policySummary.quizMode,
      aiQuizzesEnabled: child.policySummary.aiQuizzesEnabled,
    });
  };

  const handleSaveChildPolicy = async () => {
    if (!editingChild) return;
    setSavingChild(true);
    try {
      await adminService.updateChildPolicy(
        editingChild.familyId,
        editingChild.child.childId,
        {
          displayName: editingChild.displayName,
          ageBand: editingChild.ageBand,
          dailyCeilingMinutes: Number(editingChild.dailyCeilingMinutes),
          quizMode: editingChild.quizMode,
          aiQuizzesEnabled: editingChild.aiQuizzesEnabled,
        },
        "current_superadmin"
      );
      setEditingChild(null);
      await loadChildren();
    } catch (err) {
      console.error("Failed to save child policy:", err);
    } finally {
      setSavingChild(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteModal) return;
    await adminService.deleteChild(deleteModal.familyId, deleteModal.childId, "current_superadmin");
    setDeleteModal(null);
    loadChildren();
  };

  const handleExportCSV = () => {
    const headers = ["Child ID", "Display Name", "Age Band", "Family ID", "Family Name", "Device Count", "Daily Ceiling (min)", "Quiz Mode"];
    const rows = children.map((c) => [
      c.childId,
      `"${c.displayName}"`,
      c.ageBand,
      c.familyId,
      `"${c.familyName}"`,
      c.deviceCount,
      c.policySummary.dailyCeilingMinutes,
      c.policySummary.quizMode,
    ]);

    const csvContent = "data:text/csv;charset=utf-8," + [headers.join(","), ...rows.map((r) => r.join(","))].join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `meritscreen_children_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Children & Learner Profiles</h1>
          <p className="text-sm text-muted-foreground">
            Cross-family overview of enrolled children, learning tiers, and policy caps.
          </p>
        </div>
        <Button onClick={handleExportCSV} variant="outline" className="gap-2 shrink-0">
          <Download className="h-4 w-4" />
          Export Children CSV
        </Button>
      </div>

      {/* Filter and Search Bar */}
      <Card>
        <CardContent className="p-4 flex flex-col sm:flex-row gap-3 items-center justify-between">
          <div className="relative w-full sm:w-80">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search by child name, child ID, or family..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>
          <div className="text-xs text-muted-foreground">
            Showing <strong className="text-foreground">{children.length}</strong> active profiles
          </div>
        </CardContent>
      </Card>

      {/* Children Table */}
      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-muted/30 text-xs font-semibold uppercase text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Child Name & ID</th>
                <th className="px-4 py-3">Family Group</th>
                <th className="px-4 py-3">Age Band Tier</th>
                <th className="px-4 py-3">Screen Time Ceiling</th>
                <th className="px-4 py-3">Quiz Mode</th>
                <th className="px-4 py-3 text-center">Devices</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loading ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-muted-foreground">
                    Loading children...
                  </td>
                </tr>
              ) : children.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-muted-foreground">
                    No children matching criteria.
                  </td>
                </tr>
              ) : (
                children.map((c) => (
                  <tr
                    key={c.childId}
                    onClick={() => setSelectedChild(c)}
                    className="hover:bg-muted/50 transition-colors cursor-pointer group"
                  >
                    <td className="px-4 py-3">
                      <div className="font-semibold text-foreground flex items-center gap-2 group-hover:text-primary transition-colors">
                        <Baby className="h-4 w-4 text-emerald-500" />
                        {c.displayName}
                      </div>
                      <div className="text-xs text-muted-foreground font-mono">{c.childId}</div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="text-xs font-medium text-foreground">{c.familyName}</div>
                      <div className="text-[11px] text-muted-foreground font-mono">{c.familyId}</div>
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant="secondary" className="text-xs font-medium">
                        {c.ageBand === "AGE_3_TO_6" && "3-6 (Early)"}
                        {c.ageBand === "AGE_7_TO_9" && "7-9 (Foundational)"}
                        {c.ageBand === "AGE_10_TO_12" && "10-12 (Independent)"}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1.5 text-xs">
                        <Clock className="h-3.5 w-3.5 text-muted-foreground" />
                        <strong className="text-foreground">{c.policySummary.dailyCeilingMinutes} min</strong> / day
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className="text-xs font-mono capitalize text-muted-foreground">
                        {c.policySummary.quizMode.replace("_", " ")}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <Badge variant="outline" className="gap-1">
                        <Smartphone className="h-3 w-3" />
                        {c.deviceCount}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-right" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => setSelectedChild(c)}
                          title="View Profile Details"
                          className="hover:text-primary hover:bg-primary/10"
                        >
                          <BookOpen className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleOpenEditChild(c)}
                          title="Edit Policy & Quotas"
                          className="text-primary hover:bg-primary/10"
                        >
                          <Clock className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="text-destructive hover:bg-destructive/10"
                          onClick={() => setDeleteModal(c)}
                          title="Delete Child Profile"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Child Detail Modal */}
      {selectedChild && (
        <Modal
          isOpen={true}
          onClose={() => setSelectedChild(null)}
          title={`Child Profile: ${selectedChild.displayName}`}
          description={`ID: ${selectedChild.childId} • Family: ${selectedChild.familyName}`}
          footer={
            <div className="flex items-center justify-between w-full">
              <Button
                variant="outline"
                size="sm"
                className="text-primary border-primary/30"
                onClick={() => {
                  const target = selectedChild;
                  setSelectedChild(null);
                  handleOpenEditChild(target);
                }}
              >
                Edit Policy Rules
              </Button>
              <Button onClick={() => setSelectedChild(null)}>Close</Button>
            </div>
          }
        >
          <div className="space-y-4 text-xs">
            <div className="grid grid-cols-2 gap-3">
              <div className="p-3 rounded-lg border border-border bg-muted/20">
                <span className="text-muted-foreground block mb-0.5">Avatar ID</span>
                <strong className="text-sm font-semibold">{selectedChild.avatarId}</strong>
              </div>
              <div className="p-3 rounded-lg border border-border bg-muted/20">
                <span className="text-muted-foreground block mb-0.5">Language</span>
                <strong className="text-sm uppercase font-semibold">{selectedChild.language}</strong>
              </div>
              <div className="p-3 rounded-lg border border-border bg-muted/20">
                <span className="text-muted-foreground block mb-0.5">Daily Ceiling</span>
                <strong className="text-sm font-semibold">{selectedChild.policySummary.dailyCeilingMinutes} min</strong>
              </div>
              <div className="p-3 rounded-lg border border-border bg-muted/20">
                <span className="text-muted-foreground block mb-0.5">AI Adaptive Quizzes</span>
                <strong className="text-sm font-semibold">{selectedChild.policySummary.aiQuizzesEnabled ? "Enabled" : "Disabled"}</strong>
              </div>
            </div>

            <div className="rounded-lg border border-border p-3">
              <span className="font-semibold block mb-2">Paired Launcher Devices ({selectedChild.devices.length})</span>
              {selectedChild.devices.length === 0 ? (
                <p className="text-xs text-muted-foreground italic">No devices paired to this child profile.</p>
              ) : (
                selectedChild.devices.map((d) => (
                  <div key={d.deviceId} className="flex items-center justify-between p-2 rounded bg-muted/30 mb-1 last:mb-0">
                    <div>
                      <div className="font-medium text-foreground">{d.model}</div>
                      <div className="text-[11px] text-muted-foreground font-mono">{d.deviceId}</div>
                    </div>
                    {d.revoked ? (
                      <Badge variant="destructive" className="text-[10px]">Revoked</Badge>
                    ) : (
                      <Badge variant="success" className="text-[10px]">Active</Badge>
                    )}
                  </div>
                ))
              )}
            </div>

            <div className="p-3 rounded-lg border border-emerald-500/20 bg-emerald-500/5 flex items-center gap-2">
              <ShieldCheck className="h-4 w-4 text-emerald-500 shrink-0" />
              <p className="text-muted-foreground text-[11px]">
                COPPA Compliance: Child profiles carry no child emails, personal phone numbers, or geolocations.
              </p>
            </div>
          </div>
        </Modal>
      )}

      {/* Edit Child Policy Modal */}
      {editingChild && (
        <Modal
          isOpen={true}
          onClose={() => setEditingChild(null)}
          title={`Edit Child Policy: ${editingChild.child.displayName}`}
          description={`Child ID: ${editingChild.child.childId}`}
          footer={
            <>
              <Button variant="outline" onClick={() => setEditingChild(null)}>
                Cancel
              </Button>
              <Button onClick={handleSaveChildPolicy} disabled={savingChild}>
                {savingChild ? "Saving..." : "Save Policy"}
              </Button>
            </>
          }
        >
          <div className="space-y-4 text-xs">
            <div>
              <label className="font-semibold text-foreground block mb-1">Display Name</label>
              <Input
                value={editingChild.displayName}
                onChange={(e) => setEditingChild({ ...editingChild, displayName: e.target.value })}
                placeholder="Child Name"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="font-semibold text-foreground block mb-1">Age Band Tier</label>
                <select
                  value={editingChild.ageBand}
                  onChange={(e) =>
                    setEditingChild({
                      ...editingChild,
                      ageBand: e.target.value as "AGE_3_TO_6" | "AGE_7_TO_9" | "AGE_10_TO_12",
                    })
                  }
                  className="h-9 w-full rounded-md border border-input bg-card px-3 text-xs focus:outline-none focus:ring-1 focus:ring-ring"
                >
                  <option value="AGE_3_TO_6">Ages 3-6 (Early Explorers)</option>
                  <option value="AGE_7_TO_9">Ages 7-9 (Foundational)</option>
                  <option value="AGE_10_TO_12">Ages 10-12 (Independent)</option>
                </select>
              </div>

              <div>
                <label className="font-semibold text-foreground block mb-1">Quiz Policy Mode</label>
                <select
                  value={editingChild.quizMode}
                  onChange={(e) =>
                    setEditingChild({
                      ...editingChild,
                      quizMode: e.target.value as "app_block" | "earn_minutes" | "curfew",
                    })
                  }
                  className="h-9 w-full rounded-md border border-input bg-card px-3 text-xs focus:outline-none focus:ring-1 focus:ring-ring"
                >
                  <option value="earn_minutes">Earn Minutes via Quiz</option>
                  <option value="app_block">App Block Only</option>
                  <option value="curfew">Curfew Schedule</option>
                </select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="font-semibold text-foreground block mb-1">
                  Daily Screen Ceiling (Minutes)
                </label>
                <Input
                  type="number"
                  min={15}
                  max={480}
                  step={15}
                  value={editingChild.dailyCeilingMinutes}
                  onChange={(e) =>
                    setEditingChild({
                      ...editingChild,
                      dailyCeilingMinutes: Number(e.target.value),
                    })
                  }
                />
              </div>

              <div>
                <label className="font-semibold text-foreground block mb-1">AI Adaptive Curriculum</label>
                <select
                  value={editingChild.aiQuizzesEnabled ? "true" : "false"}
                  onChange={(e) =>
                    setEditingChild({
                      ...editingChild,
                      aiQuizzesEnabled: e.target.value === "true",
                    })
                  }
                  className="h-9 w-full rounded-md border border-input bg-card px-3 text-xs focus:outline-none focus:ring-1 focus:ring-ring"
                >
                  <option value="true">Enabled (Adaptive Questions)</option>
                  <option value="false">Disabled (Standard Quizzes)</option>
                </select>
              </div>
            </div>
          </div>
        </Modal>
      )}

      {/* Delete Child Modal */}
      {deleteModal && (
        <Modal
          isOpen={true}
          onClose={() => setDeleteModal(null)}
          title="Delete Child Profile?"
          description={`Target: ${deleteModal.displayName} (${deleteModal.childId})`}
          footer={
            <>
              <Button variant="outline" onClick={() => setDeleteModal(null)}>Cancel</Button>
              <Button variant="destructive" onClick={handleDelete}>Confirm Delete</Button>
            </>
          }
        >
          <p className="text-xs text-muted-foreground">
            Deleting this child profile will remove all policy configurations, quiz attempts, and unpair their linked launcher devices. This action is logged to the audit trail.
          </p>
        </Modal>
      )}
    </div>
  );
}
