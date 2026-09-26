import React, { useEffect, useState } from "react";
import { adminService } from "../services/adminService";
import { OperatorItem } from "../data/mockStore";
import { Card, CardContent, CardHeader, CardTitle, Button, Input, Badge } from "../components/ui/core";
import { Modal } from "../components/ui/Modal";
import { formatDate } from "../lib/utils";
import { ShieldCheck, UserPlus, ShieldAlert, Trash2 } from "lucide-react";

export function OperatorsPage() {
  const [operators, setOperators] = useState<OperatorItem[]>([]);
  const [loading, setLoading] = useState(true);

  // New Operator Modal
  const [addModal, setAddModal] = useState(false);
  const [emailInput, setEmailInput] = useState("");
  const [nameInput, setNameInput] = useState("");

  // Revoke Modal
  const [revokeModal, setRevokeModal] = useState<OperatorItem | null>(null);

  const loadOperators = async () => {
    setLoading(true);
    const ops = await adminService.getOperators();
    setOperators(ops);
    setLoading(false);
  };

  useEffect(() => {
    loadOperators();
  }, []);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!emailInput) return;
    await adminService.addOperator(emailInput, nameInput || "Super Admin", "current_superadmin");
    setAddModal(false);
    setEmailInput("");
    setNameInput("");
    loadOperators();
  };

  const handleRevoke = async () => {
    if (!revokeModal) return;
    await adminService.revokeOperator(revokeModal.uid, "current_superadmin");
    setRevokeModal(null);
    loadOperators();
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Operators & IAM Claims</h1>
          <p className="text-sm text-muted-foreground">
            Manage engineers and support personnel with the <code className="font-mono text-primary">role: super_admin</code> Firebase Auth claim.
          </p>
        </div>
        <Button onClick={() => setAddModal(true)} className="gap-2">
          <UserPlus className="h-4 w-4" />
          Grant Operator Claim
        </Button>
      </div>

      {/* Security Guidance Alert */}
      <Card className="border-indigo-500/30 bg-indigo-500/5">
        <CardContent className="p-4 flex items-start gap-3">
          <ShieldAlert className="h-5 w-5 text-indigo-500 shrink-0 mt-0.5" />
          <div className="text-xs text-muted-foreground leading-relaxed">
            <strong className="text-foreground">Super Admin Authorization Model:</strong> Super admin privileges are enforced at the API layer by checking Firebase Auth custom claims (<code className="font-mono text-primary">role === 'super_admin'</code>). Only operators holding this claim can execute administrative Cloud Functions or view cross-family records.
          </div>
        </CardContent>
      </Card>

      {/* Operators List */}
      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-muted/30 text-xs font-semibold uppercase text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Operator Name & Email</th>
                <th className="px-4 py-3">Operator UID</th>
                <th className="px-4 py-3">Assigned Role</th>
                <th className="px-4 py-3">Granted Timestamp</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loading ? (
                <tr>
                  <td colSpan={5} className="py-12 text-center text-muted-foreground">
                    Loading operators...
                  </td>
                </tr>
              ) : (
                operators.map((op) => (
                  <tr key={op.uid} className="hover:bg-muted/40 transition-colors">
                    <td className="px-4 py-3">
                      <div className="font-semibold text-foreground flex items-center gap-2">
                        <ShieldCheck className="h-4 w-4 text-primary" />
                        {op.displayName}
                      </div>
                      <div className="text-xs text-muted-foreground font-mono">{op.email}</div>
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                      {op.uid}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant="default" className="text-[11px] font-mono">
                        {op.role}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">
                      {formatDate(op.grantedAt)}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button
                        variant="ghost"
                        size="icon"
                        className="text-destructive hover:bg-destructive/10"
                        onClick={() => setRevokeModal(op)}
                        title="Revoke Super Admin Role"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Grant Operator Modal */}
      {addModal && (
        <Modal
          isOpen={true}
          onClose={() => setAddModal(false)}
          title="Grant Super Admin Claim"
          description="Assign administrative role claim to a verified internal Firebase Auth user."
          footer={
            <>
              <Button variant="outline" onClick={() => setAddModal(false)}>Cancel</Button>
              <Button onClick={handleAdd}>Grant Claim</Button>
            </>
          }
        >
          <form onSubmit={handleAdd} className="space-y-4">
            <div>
              <label className="text-xs font-semibold block mb-1">Corporate / Internal Email</label>
              <Input
                type="email"
                placeholder="e.g. engineer@meritscreen.internal"
                value={emailInput}
                onChange={(e) => setEmailInput(e.target.value)}
                required
              />
            </div>
            <div>
              <label className="text-xs font-semibold block mb-1">Display Name / Title</label>
              <Input
                placeholder="e.g. Lead Platform Engineer"
                value={nameInput}
                onChange={(e) => setNameInput(e.target.value)}
              />
            </div>
            <p className="text-[11px] text-muted-foreground">
              Note: This assigns <code className="font-mono">super_admin</code> claim to the user's Auth token and writes an entry into the audit trail.
            </p>
          </form>
        </Modal>
      )}

      {/* Revoke Operator Modal */}
      {revokeModal && (
        <Modal
          isOpen={true}
          onClose={() => setRevokeModal(null)}
          title="Revoke Super Admin Claim?"
          description={`Target: ${revokeModal.email} (${revokeModal.displayName})`}
          footer={
            <>
              <Button variant="outline" onClick={() => setRevokeModal(null)}>Cancel</Button>
              <Button variant="destructive" onClick={handleRevoke}>Revoke Claim</Button>
            </>
          }
        >
          <p className="text-xs text-muted-foreground">
            This will strip the <code className="font-mono">super_admin</code> claim from their Firebase Auth account and invalidate their active admin session.
          </p>
        </Modal>
      )}
    </div>
  );
}
