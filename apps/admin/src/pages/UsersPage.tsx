import React, { useEffect, useState } from "react";
import { adminService } from "../services/adminService";
import { UserItem } from "../data/mockStore";
import { Card, CardContent, CardHeader, CardTitle, Button, Input, Badge } from "../components/ui/core";
import { Modal } from "../components/ui/Modal";
import { formatDate, timeAgo } from "../lib/utils";
import { Search, Download, ShieldAlert, CheckCircle, Ban, Trash2, Eye } from "lucide-react";

export function UsersPage() {
  const [users, setUsers] = useState<UserItem[]>([]);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");
  const [loading, setLoading] = useState(true);

  // Selected User Modal for detail view
  const [selectedUser, setSelectedUser] = useState<UserItem | null>(null);

  // Action Confirmation Modal
  const [actionModal, setActionModal] = useState<{
    type: "status" | "delete";
    user: UserItem;
    targetStatus?: "active" | "disabled" | "inactive";
  } | null>(null);

  const loadUsers = async () => {
    setLoading(true);
    const data = await adminService.getUsers(search, statusFilter);
    setUsers(data);
    setLoading(false);
  };

  useEffect(() => {
    loadUsers();
  }, [search, statusFilter]);

  const handleStatusChange = async (user: UserItem, newStatus: "active" | "disabled" | "inactive") => {
    await adminService.setUserStatus(user.uid, newStatus, "current_superadmin");
    setActionModal(null);
    loadUsers();
  };

  const handleDeleteUser = async (user: UserItem) => {
    await adminService.deleteUser(user.uid, "current_superadmin");
    setActionModal(null);
    loadUsers();
  };

  const handleExportCSV = () => {
    // Generates safe, redacted CSV (No passwords, no PINs, no sensitive tokens)
    const headers = ["UID", "Email", "Display Name", "Family ID", "Status", "Created At", "Last Sign In"];
    const rows = users.map((u) => [
      u.uid,
      u.email,
      `"${u.displayName}"`,
      u.familyId || "",
      u.status,
      u.createdAt,
      u.lastSignInAt,
    ]);

    const csvContent = "data:text/csv;charset=utf-8," + [headers.join(","), ...rows.map((r) => r.join(","))].join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `meritscreen_parents_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Parent Accounts & Users</h1>
          <p className="text-sm text-muted-foreground">
            Manage authenticated parent profiles, toggle suspension, and review family bindings.
          </p>
        </div>
        <Button onClick={handleExportCSV} variant="outline" className="gap-2 shrink-0">
          <Download className="h-4 w-4" />
          Export Safe CSV
        </Button>
      </div>

      {/* Filter and Search Bar */}
      <Card>
        <CardContent className="p-4 flex flex-col sm:flex-row gap-3 items-center justify-between">
          <div className="relative w-full sm:w-80">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search by email, name, or UID..."
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
              <option value="all">All Statuses</option>
              <option value="active">Active</option>
              <option value="disabled">Disabled (Auth Blocked)</option>
              <option value="inactive">Inactive</option>
            </select>
          </div>
        </CardContent>
      </Card>

      {/* Data Table */}
      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-muted/30 text-xs font-semibold uppercase text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Parent Profile</th>
                <th className="px-4 py-3">Family Binding</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Registered</th>
                <th className="px-4 py-3">Last Active</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-muted-foreground">
                    Loading users...
                  </td>
                </tr>
              ) : users.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-muted-foreground">
                    No users matching criteria.
                  </td>
                </tr>
              ) : (
                users.map((u) => (
                  <tr
                    key={u.uid}
                    onClick={() => setSelectedUser(u)}
                    className="hover:bg-muted/50 transition-colors cursor-pointer group"
                  >
                    <td className="px-4 py-3">
                      <div className="font-semibold text-foreground group-hover:text-primary transition-colors">{u.displayName}</div>
                      <div className="text-xs text-muted-foreground font-mono">{u.email}</div>
                    </td>
                    <td className="px-4 py-3">
                      {u.familyId ? (
                        <span className="font-mono text-xs text-primary">{u.familyId}</span>
                      ) : (
                        <span className="text-xs text-muted-foreground italic">No family yet</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {u.status === "active" && <Badge variant="success">Active</Badge>}
                      {u.status === "disabled" && <Badge variant="destructive">Disabled</Badge>}
                      {u.status === "inactive" && <Badge variant="warning">Inactive</Badge>}
                    </td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">
                      {formatDate(u.createdAt)}
                    </td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">
                      {timeAgo(u.lastSignInAt)}
                    </td>
                    <td className="px-4 py-3 text-right" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => setSelectedUser(u)}
                          title="View Profile Details"
                          className="hover:text-primary hover:bg-primary/10"
                        >
                          <Eye className="h-4 w-4" />
                        </Button>

                        {u.status === "active" ? (
                          <Button
                            variant="ghost"
                            size="icon"
                            className="text-amber-500 hover:text-amber-600 hover:bg-amber-500/10"
                            onClick={() =>
                              setActionModal({ type: "status", user: u, targetStatus: "disabled" })
                            }
                            title="Disable Account"
                          >
                            <Ban className="h-4 w-4" />
                          </Button>
                        ) : (
                          <Button
                            variant="ghost"
                            size="icon"
                            className="text-emerald-500 hover:text-emerald-600 hover:bg-emerald-500/10"
                            onClick={() =>
                              setActionModal({ type: "status", user: u, targetStatus: "active" })
                            }
                            title="Activate Account"
                          >
                            <CheckCircle className="h-4 w-4" />
                          </Button>
                        )}

                        <Button
                          variant="ghost"
                          size="icon"
                          className="text-destructive hover:bg-destructive/10"
                          onClick={() => setActionModal({ type: "delete", user: u })}
                          title="Delete User"
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

      {/* User Details Modal */}
      {selectedUser && (
        <Modal
          isOpen={true}
          onClose={() => setSelectedUser(null)}
          title="Parent Account Profile"
          description={`UID: ${selectedUser.uid}`}
          footer={<Button onClick={() => setSelectedUser(null)}>Close</Button>}
        >
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-3 rounded-lg border border-border bg-muted/20">
                <span className="text-muted-foreground block mb-0.5">Full Name</span>
                <strong className="text-sm font-semibold">{selectedUser.displayName}</strong>
              </div>
              <div className="p-3 rounded-lg border border-border bg-muted/20">
                <span className="text-muted-foreground block mb-0.5">Primary Email</span>
                <strong className="text-sm font-semibold">{selectedUser.email}</strong>
              </div>
              <div className="p-3 rounded-lg border border-border bg-muted/20">
                <span className="text-muted-foreground block mb-0.5">Status</span>
                <strong className="text-sm capitalize font-semibold">{selectedUser.status}</strong>
              </div>
              <div className="p-3 rounded-lg border border-border bg-muted/20">
                <span className="text-muted-foreground block mb-0.5">Auth Providers</span>
                <strong className="text-sm font-semibold">{selectedUser.providers?.join(", ") || "Email/Password"}</strong>
              </div>
            </div>

            <div className="p-3 rounded-lg border border-emerald-500/20 bg-emerald-500/5 text-xs">
              <div className="font-semibold text-emerald-600 dark:text-emerald-400 flex items-center gap-1.5">
                <ShieldAlert className="h-3.5 w-3.5" />
                Privacy & Data Safeguard
              </div>
              <p className="mt-1 text-muted-foreground">
                In adherence to MeritScreen data compliance rules, parent PINs, biometric salts, and child tokens are strictly encrypted and hidden from administrative views.
              </p>
            </div>
          </div>
        </Modal>
      )}

      {/* Action Confirmation Modal */}
      {actionModal && (
        <Modal
          isOpen={true}
          onClose={() => setActionModal(null)}
          title={
            actionModal.type === "delete"
              ? "Confirm User Removal"
              : actionModal.targetStatus === "disabled"
              ? "Disable Parent Account?"
              : "Re-enable Parent Account?"
          }
          description={`Target user: ${actionModal.user.email} (${actionModal.user.displayName})`}
          footer={
            <>
              <Button variant="outline" onClick={() => setActionModal(null)}>
                Cancel
              </Button>
              {actionModal.type === "delete" ? (
                <Button
                  variant="destructive"
                  onClick={() => handleDeleteUser(actionModal.user)}
                >
                  Confirm Delete
                </Button>
              ) : (
                <Button
                  variant={actionModal.targetStatus === "disabled" ? "destructive" : "default"}
                  onClick={() =>
                    handleStatusChange(actionModal.user, actionModal.targetStatus!)
                  }
                >
                  Confirm {actionModal.targetStatus === "disabled" ? "Disable" : "Activate"}
                </Button>
              )}
            </>
          }
        >
          <p className="text-xs text-muted-foreground">
            {actionModal.type === "delete"
              ? "Deleting this parent account will unlink their associated family. This administrative action will be recorded in the immutable audit log."
              : actionModal.targetStatus === "disabled"
              ? "Disabling will revoke the Firebase Auth token and prevent the parent from signing in to the app until restored."
              : "Activating will restore full parent sign-in access to MeritScreen."}
          </p>
        </Modal>
      )}
    </div>
  );
}
