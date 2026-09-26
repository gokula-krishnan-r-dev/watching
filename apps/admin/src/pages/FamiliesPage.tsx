import React, { useEffect, useState } from "react";
import { adminService } from "../services/adminService";
import { FamilyItem, ChildItem } from "../data/mockStore";
import { Card, CardContent, Button, Input, Badge } from "../components/ui/core";
import { Modal } from "../components/ui/Modal";
import { formatDate } from "../lib/utils";
import {
  Search,
  Home,
  Baby,
  Smartphone,
  Eye,
  Ban,
  CheckCircle,
  Trash2,
  Download,
  AlertTriangle,
  Layers,
  Clock,
  Edit2,
  Users,
  ShieldCheck,
  Sparkles,
  Save,
  Check,
  Settings,
  Battery,
  Calendar,
  Lock,
} from "lucide-react";

export function FamiliesPage() {
  const [families, setFamilies] = useState<FamilyItem[]>([]);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");
  const [loading, setLoading] = useState(true);

  // Selected Family Rich Details Modal
  const [selectedFamily, setSelectedFamily] = useState<FamilyItem | null>(null);

  // Real-time Editing Family Name in Modal
  const [isEditingName, setIsEditingName] = useState(false);
  const [editNameValue, setEditNameValue] = useState("");
  const [savingName, setSavingName] = useState(false);

  // Real-time Child Policy Edit Modal from within Family View
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

  // Hard Delete Modal (Requires typing DELETE)
  const [deleteModal, setDeleteModal] = useState<FamilyItem | null>(null);
  const [deleteConfirmationText, setDeleteConfirmationText] = useState("");

  // Status Modal
  const [statusModal, setStatusModal] = useState<{
    family: FamilyItem;
    targetStatus: "active" | "suspended";
  } | null>(null);

  const loadFamilies = async () => {
    setLoading(true);
    try {
      const data = await adminService.getFamilies(search, statusFilter);
      setFamilies(data);
      // If modal currently open, refresh selectedFamily instance
      if (selectedFamily) {
        const updated = data.find((f) => f.familyId === selectedFamily.familyId);
        if (updated) setSelectedFamily(updated);
      }
    } catch (err) {
      console.error("Failed to load families:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadFamilies();
  }, [search, statusFilter]);

  const handleOpenFamily = (family: FamilyItem) => {
    setSelectedFamily(family);
    setEditNameValue(family.name);
    setIsEditingName(false);
  };

  const handleSaveFamilyName = async () => {
    if (!selectedFamily || !editNameValue.trim()) return;
    setSavingName(true);
    try {
      await adminService.updateFamilyName(selectedFamily.familyId, editNameValue.trim(), "current_superadmin");
      setIsEditingName(false);
      await loadFamilies();
    } catch (err) {
      console.error("Failed to save family name:", err);
    } finally {
      setSavingName(false);
    }
  };

  const handleOpenEditChild = (familyId: string, child: ChildItem) => {
    setEditingChild({
      familyId,
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
      await loadFamilies();
    } catch (err) {
      console.error("Failed to update child policy:", err);
    } finally {
      setSavingChild(false);
    }
  };

  const handleStatusChange = async (family: FamilyItem, newStatus: "active" | "suspended") => {
    await adminService.setFamilyStatus(family.familyId, newStatus, "current_superadmin");
    setStatusModal(null);
    await loadFamilies();
  };

  const handleConfirmDelete = async () => {
    if (!deleteModal || deleteConfirmationText !== "DELETE") return;
    await adminService.deleteFamily(deleteModal.familyId, "current_superadmin");
    setDeleteModal(null);
    setDeleteConfirmationText("");
    if (selectedFamily?.familyId === deleteModal.familyId) {
      setSelectedFamily(null);
    }
    await loadFamilies();
  };

  const handleExportCSV = () => {
    const headers = ["Family ID", "Family Name", "Owner Email", "Owner UID", "Children Count", "Devices Count", "Status", "Created At"];
    const rows = families.map((f) => [
      f.familyId,
      `"${f.name}"`,
      f.ownerEmail,
      f.ownerUid,
      f.childCount,
      f.deviceCount,
      f.status,
      f.createdAt,
    ]);

    const csvContent = "data:text/csv;charset=utf-8," + [headers.join(","), ...rows.map((r) => r.join(","))].join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `meritscreen_families_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Family Accounts & Tree Hierarchy</h1>
          <p className="text-sm text-muted-foreground">
            Explore family clusters, inspect all children profiles and hardware bindings, or click any family to view & edit details.
          </p>
        </div>
        <Button onClick={handleExportCSV} variant="outline" className="gap-2 shrink-0">
          <Download className="h-4 w-4" />
          Export Families CSV
        </Button>
      </div>

      {/* Filter and Search Bar */}
      <Card>
        <CardContent className="p-4 flex flex-col sm:flex-row gap-3 items-center justify-between">
          <div className="relative w-full sm:w-80">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search family name, ID, or owner email..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>

          <div className="flex items-center gap-2 w-full sm:w-auto">
            <span className="text-xs text-muted-foreground shrink-0">Status:</span>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="h-9 rounded-md border border-input bg-card px-3 text-sm focus:outline-none focus:ring-1 focus:ring-ring"
            >
              <option value="all">All Families</option>
              <option value="active">Active</option>
              <option value="suspended">Suspended</option>
            </select>
          </div>
        </CardContent>
      </Card>

      {/* Families Table */}
      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-muted/30 text-xs font-semibold uppercase text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Family Unit</th>
                <th className="px-4 py-3">Owner Account</th>
                <th className="px-4 py-3 text-center">Children</th>
                <th className="px-4 py-3 text-center">Devices</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Created</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loading ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-muted-foreground">
                    <div className="flex items-center justify-center gap-2">
                      <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-primary" />
                      Loading families...
                    </div>
                  </td>
                </tr>
              ) : families.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-muted-foreground">
                    No families found matching search criteria.
                  </td>
                </tr>
              ) : (
                families.map((f) => (
                  <tr
                    key={f.familyId}
                    onClick={() => handleOpenFamily(f)}
                    className="hover:bg-muted/50 transition-colors cursor-pointer group"
                  >
                    <td className="px-4 py-3">
                      <div className="font-semibold text-foreground flex items-center gap-2 group-hover:text-primary transition-colors">
                        <Home className="h-4 w-4 text-primary" />
                        {f.name}
                      </div>
                      <div className="text-xs text-muted-foreground font-mono">{f.familyId}</div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="text-xs text-foreground font-medium">{f.ownerEmail}</div>
                      <div className="text-[11px] text-muted-foreground font-mono">{f.ownerUid}</div>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <Badge variant="secondary" className="gap-1">
                        <Baby className="h-3 w-3" />
                        {f.childCount}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <Badge variant="secondary" className="gap-1">
                        <Smartphone className="h-3 w-3" />
                        {f.deviceCount}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">
                      {f.status === "active" ? (
                        <Badge variant="success">Active</Badge>
                      ) : (
                        <Badge variant="destructive">Suspended</Badge>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">
                      {formatDate(f.createdAt)}
                    </td>
                    <td className="px-4 py-3 text-right" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleOpenFamily(f)}
                          title="View Family Details & Tree"
                          className="hover:text-primary hover:bg-primary/10"
                        >
                          <Eye className="h-4 w-4" />
                        </Button>

                        {f.status === "active" ? (
                          <Button
                            variant="ghost"
                            size="icon"
                            className="text-amber-500 hover:text-amber-600 hover:bg-amber-500/10"
                            onClick={() =>
                              setStatusModal({ family: f, targetStatus: "suspended" })
                            }
                            title="Suspend Family"
                          >
                            <Ban className="h-4 w-4" />
                          </Button>
                        ) : (
                          <Button
                            variant="ghost"
                            size="icon"
                            className="text-emerald-500 hover:text-emerald-600 hover:bg-emerald-500/10"
                            onClick={() =>
                              setStatusModal({ family: f, targetStatus: "active" })
                            }
                            title="Activate Family"
                          >
                            <CheckCircle className="h-4 w-4" />
                          </Button>
                        )}

                        <Button
                          variant="ghost"
                          size="icon"
                          className="text-destructive hover:bg-destructive/10"
                          onClick={() => {
                            setDeleteModal(f);
                            setDeleteConfirmationText("");
                          }}
                          title="Delete Family"
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

      {/* COMPREHENSIVE FAMILY DETAILS & TREE MODAL */}
      {selectedFamily && (
        <Modal
          isOpen={true}
          onClose={() => setSelectedFamily(null)}
          title={`Family Overview: ${selectedFamily.name}`}
          description={`Family ID: ${selectedFamily.familyId} • Created: ${formatDate(selectedFamily.createdAt)}`}
          maxWidth="max-w-3xl"
          footer={
            <div className="flex items-center justify-between w-full">
              <div className="flex items-center gap-2">
                {selectedFamily.status === "active" ? (
                  <Button
                    variant="outline"
                    size="sm"
                    className="text-amber-500 hover:text-amber-600 border-amber-500/30"
                    onClick={() => {
                      setStatusModal({ family: selectedFamily, targetStatus: "suspended" });
                    }}
                  >
                    <Ban className="h-3.5 w-3.5 mr-1" />
                    Suspend Family
                  </Button>
                ) : (
                  <Button
                    variant="outline"
                    size="sm"
                    className="text-emerald-500 hover:text-emerald-600 border-emerald-500/30"
                    onClick={() => {
                      setStatusModal({ family: selectedFamily, targetStatus: "active" });
                    }}
                  >
                    <CheckCircle className="h-3.5 w-3.5 mr-1" />
                    Reactivate Family
                  </Button>
                )}
                <Button
                  variant="outline"
                  size="sm"
                  className="text-destructive border-destructive/30 hover:bg-destructive/10"
                  onClick={() => {
                    setDeleteModal(selectedFamily);
                    setDeleteConfirmationText("");
                  }}
                >
                  <Trash2 className="h-3.5 w-3.5 mr-1" />
                  Hard Delete
                </Button>
              </div>
              <Button onClick={() => setSelectedFamily(null)}>Done</Button>
            </div>
          }
        >
          <div className="space-y-5 max-h-[72vh] overflow-y-auto pr-1">
            {/* 1. Header Card with Quick Edit Name */}
            <div className="rounded-xl border border-border bg-muted/20 p-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex-1">
                  <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider block">
                    Family Unit Name
                  </span>
                  {isEditingName ? (
                    <div className="flex items-center gap-2 mt-1">
                      <Input
                        value={editNameValue}
                        onChange={(e) => setEditNameValue(e.target.value)}
                        className="h-8 text-sm"
                        placeholder="Enter family name"
                        autoFocus
                      />
                      <Button
                        size="sm"
                        onClick={handleSaveFamilyName}
                        disabled={savingName || !editNameValue.trim()}
                        className="h-8 px-2.5 gap-1 shrink-0"
                      >
                        <Check className="h-3.5 w-3.5" />
                        Save
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => {
                          setIsEditingName(false);
                          setEditNameValue(selectedFamily.name);
                        }}
                        className="h-8 px-2.5 text-xs shrink-0"
                      >
                        Cancel
                      </Button>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-lg font-bold text-foreground">{selectedFamily.name}</span>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setIsEditingName(true)}
                        className="h-7 px-2 text-xs text-primary gap-1"
                      >
                        <Edit2 className="h-3 w-3" />
                        Edit Name
                      </Button>
                    </div>
                  )}
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <div className="text-right">
                    <span className="text-[11px] text-muted-foreground block">Current Status</span>
                    {selectedFamily.status === "active" ? (
                      <Badge variant="success">Active Status</Badge>
                    ) : (
                      <Badge variant="destructive">Suspended</Badge>
                    )}
                  </div>
                </div>
              </div>

              {/* Quick Summary Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 pt-3 border-t border-border/60 text-xs">
                <div>
                  <span className="text-muted-foreground block text-[11px]">Primary Owner</span>
                  <span className="font-semibold text-foreground truncate block">{selectedFamily.ownerEmail}</span>
                  <span className="text-[10px] text-muted-foreground font-mono truncate block">{selectedFamily.ownerUid}</span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-[11px]">Children Enrolled</span>
                  <span className="font-bold text-sm text-foreground">{selectedFamily.children.length} learners</span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-[11px]">Paired Devices</span>
                  <span className="font-bold text-sm text-foreground">{selectedFamily.deviceCount} units</span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-[11px]">Created Date</span>
                  <span className="font-medium text-foreground">{formatDate(selectedFamily.createdAt)}</span>
                </div>
              </div>
            </div>

            {/* 2. Parent & Co-Parent Members */}
            <div className="rounded-xl border border-border p-4 bg-card">
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                  <Users className="h-4 w-4 text-primary" />
                  Parent Accounts & Co-Guardians ({selectedFamily.members.length})
                </span>
                <span className="text-[11px] text-muted-foreground">Full family authority</span>
              </div>

              <div className="grid gap-2 sm:grid-cols-2">
                {selectedFamily.members.map((m) => (
                  <div
                    key={m.uid}
                    className="flex items-center justify-between p-2.5 rounded-lg border border-border/70 bg-muted/30"
                  >
                    <div className="min-w-0 pr-2">
                      <div className="font-semibold text-xs text-foreground truncate">{m.email}</div>
                      <div className="text-[10px] text-muted-foreground font-mono truncate">UID: {m.uid}</div>
                    </div>
                    <Badge variant={m.role === "owner" ? "default" : "outline"} className="capitalize text-[10px] shrink-0">
                      {m.role}
                    </Badge>
                  </div>
                ))}
              </div>
            </div>

            {/* 3. Children Profiles, Policies & Paired Hardware Devices */}
            <div className="rounded-xl border border-border p-4 bg-card">
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                  <Baby className="h-4 w-4 text-emerald-500" />
                  Enrolled Children & Launcher Hardware ({selectedFamily.children.length})
                </span>
                <span className="text-[11px] text-muted-foreground">COPPA Compliant: Zero PII Stored</span>
              </div>

              {selectedFamily.children.length === 0 ? (
                <div className="text-center py-6 text-xs text-muted-foreground italic border border-dashed rounded-lg">
                  No children configured under this family unit yet.
                </div>
              ) : (
                <div className="space-y-4">
                  {selectedFamily.children.map((child) => (
                    <div
                      key={child.childId}
                      className="border border-border rounded-xl p-3.5 bg-card shadow-sm hover:border-primary/40 transition-colors"
                    >
                      {/* Child Header Row */}
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-2.5 border-b border-border/60">
                        <div className="flex items-center gap-2">
                          <div className="h-8 w-8 rounded-full bg-emerald-500/10 flex items-center justify-center text-emerald-600 font-bold text-xs">
                            {child.avatarId?.slice(0, 2) || "CH"}
                          </div>
                          <div>
                            <div className="font-bold text-sm text-foreground flex items-center gap-2">
                              {child.displayName}
                              <Badge variant="secondary" className="text-[10px]">
                                {child.ageBand === "AGE_3_TO_6" && "3-6 yrs (Early)"}
                                {child.ageBand === "AGE_7_TO_9" && "7-9 yrs (Foundational)"}
                                {child.ageBand === "AGE_10_TO_12" && "10-12 yrs (Independent)"}
                              </Badge>
                            </div>
                            <span className="text-[10px] text-muted-foreground font-mono">
                              ID: {child.childId} • Lang: <span className="uppercase">{child.language}</span>
                            </span>
                          </div>
                        </div>

                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleOpenEditChild(selectedFamily.familyId, child)}
                          className="h-7 px-2.5 text-xs gap-1.5 shrink-0 self-start sm:self-auto"
                        >
                          <Settings className="h-3.5 w-3.5 text-primary" />
                          Edit Rules & Policy
                        </Button>
                      </div>

                      {/* Policy & Enforcement Highlights */}
                      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 my-2.5 p-2 rounded-lg bg-muted/30 text-xs">
                        <div>
                          <span className="text-[10px] text-muted-foreground block">Daily Ceiling</span>
                          <span className="font-bold text-foreground flex items-center gap-1 mt-0.5">
                            <Clock className="h-3 w-3 text-primary" />
                            {child.policySummary.dailyCeilingMinutes} min/day
                          </span>
                        </div>
                        <div>
                          <span className="text-[10px] text-muted-foreground block">Quiz Mode</span>
                          <span className="font-semibold text-foreground capitalize mt-0.5 block">
                            {child.policySummary.quizMode.replace("_", " ")}
                          </span>
                        </div>
                        <div>
                          <span className="text-[10px] text-muted-foreground block">AI Adaptive Quizzes</span>
                          <span className="font-semibold text-foreground mt-0.5 block">
                            {child.policySummary.aiQuizzesEnabled ? (
                              <span className="text-emerald-500 font-semibold flex items-center gap-1">
                                <Sparkles className="h-3 w-3" /> Enabled
                              </span>
                            ) : (
                              <span className="text-muted-foreground">Disabled</span>
                            )}
                          </span>
                        </div>
                        <div>
                          <span className="text-[10px] text-muted-foreground block">Bonus / Quiz</span>
                          <span className="font-semibold text-foreground mt-0.5 block">
                            +{child.policySummary.bonusMinutesPerQuiz || 15} min
                          </span>
                        </div>
                      </div>

                      {/* Bound Devices List */}
                      <div className="space-y-1.5 pt-1">
                        <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider block">
                          Paired Hardware Units ({child.devices.length}):
                        </span>
                        {child.devices.length === 0 ? (
                          <p className="text-xs text-muted-foreground italic pl-1">No devices registered for this child profile.</p>
                        ) : (
                          <div className="grid gap-2 sm:grid-cols-2">
                            {child.devices.map((dev) => (
                              <div
                                key={dev.deviceId}
                                className="flex items-center justify-between p-2.5 rounded-lg border border-border/60 bg-card hover:bg-muted/30 transition-colors text-xs"
                              >
                                <div className="min-w-0 pr-2">
                                  <div className="font-medium text-foreground flex items-center gap-1.5 truncate">
                                    <Smartphone className="h-3.5 w-3.5 text-primary shrink-0" />
                                    <span className="truncate">{dev.model}</span>
                                  </div>
                                  <div className="text-[10px] text-muted-foreground font-mono truncate">
                                    {dev.deviceId} • {dev.osVersion}
                                  </div>
                                  <div className="text-[10px] text-muted-foreground mt-0.5">
                                    Last heartbeat: {formatDate(dev.lastSeenAt)}
                                  </div>
                                </div>
                                <div className="flex flex-col items-end gap-1 shrink-0">
                                  {dev.revoked ? (
                                    <Badge variant="destructive" className="text-[10px]">Revoked</Badge>
                                  ) : (
                                    <Badge variant="success" className="text-[10px]">Active</Badge>
                                  )}
                                  {dev.batteryPercent !== null && (
                                    <span className="text-[10px] text-muted-foreground flex items-center gap-1">
                                      <Battery className="h-2.5 w-2.5" />
                                      {dev.batteryPercent}%
                                    </span>
                                  )}
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* 4. Privacy & COPPA Compliance Note */}
            <div className="p-3 rounded-xl border border-emerald-500/25 bg-emerald-500/5 flex items-start gap-2.5">
              <ShieldCheck className="h-4 w-4 text-emerald-500 shrink-0 mt-0.5" />
              <div className="text-xs text-muted-foreground">
                <strong className="text-foreground">Audited Super Admin Protocol:</strong> All parent accounts, child rules, and device revocations are logged under immutable audit trails. Child profiles never collect contact information or exact location.
              </div>
            </div>
          </div>
        </Modal>
      )}

      {/* EDIT CHILD POLICY MODAL */}
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
              <Button onClick={handleSaveChildPolicy} disabled={savingChild} className="gap-1.5">
                <Save className="h-4 w-4" />
                {savingChild ? "Saving Changes..." : "Save Policy"}
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

            <div className="p-2.5 rounded-lg border border-border bg-muted/30 text-muted-foreground text-[11px]">
              Changes take effect immediately across all paired launcher devices upon next sync heartbeat.
            </div>
          </div>
        </Modal>
      )}

      {/* Suspend / Activate Modal */}
      {statusModal && (
        <Modal
          isOpen={true}
          onClose={() => setStatusModal(null)}
          title={statusModal.targetStatus === "suspended" ? "Suspend Family?" : "Restore Family?"}
          description={`Family: ${statusModal.family.name} (${statusModal.family.familyId})`}
          footer={
            <>
              <Button variant="outline" onClick={() => setStatusModal(null)}>Cancel</Button>
              <Button
                variant={statusModal.targetStatus === "suspended" ? "destructive" : "default"}
                onClick={() => handleStatusChange(statusModal.family, statusModal.targetStatus)}
              >
                Confirm {statusModal.targetStatus === "suspended" ? "Suspend" : "Activate"}
              </Button>
            </>
          }
        >
          <p className="text-xs text-muted-foreground">
            {statusModal.targetStatus === "suspended"
              ? "Suspending this family will flag their account. Android launcher clients will pause access once enforced in the upcoming client update."
              : "Restoring will reactivate the family and all configured policies."}
          </p>
        </Modal>
      )}

      {/* Hard Delete Modal with "DELETE" Confirmation */}
      {deleteModal && (
        <Modal
          isOpen={true}
          onClose={() => setDeleteModal(null)}
          title="Hard Delete Family Tree"
          description={`Family: ${deleteModal.name} (${deleteModal.familyId})`}
          footer={
            <>
              <Button variant="outline" onClick={() => setDeleteModal(null)}>Cancel</Button>
              <Button
                variant="destructive"
                disabled={deleteConfirmationText !== "DELETE"}
                onClick={handleConfirmDelete}
              >
                Permanently Delete Family
              </Button>
            </>
          }
        >
          <div className="space-y-3">
            <div className="p-3 rounded-lg border border-red-500/20 bg-red-500/10 text-red-600 dark:text-red-400 text-xs flex gap-2">
              <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
              <div>
                <strong>Warning:</strong> This permanently deletes all children, paired device bindings, policy rules, and usage records.
              </div>
            </div>

            <p className="text-xs text-muted-foreground">
              To confirm hard deletion, please type <strong className="text-foreground font-mono">DELETE</strong> in the box below:
            </p>

            <Input
              placeholder="Type DELETE to confirm"
              value={deleteConfirmationText}
              onChange={(e) => setDeleteConfirmationText(e.target.value)}
              className="font-mono text-xs uppercase"
            />
          </div>
        </Modal>
      )}
    </div>
  );
}
