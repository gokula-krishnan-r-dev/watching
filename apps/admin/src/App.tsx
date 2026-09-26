import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { ThemeProvider } from "./components/theme/ThemeProvider";
import { AdminLayout } from "./components/layout/AdminLayout";
import { DashboardPage } from "./pages/DashboardPage";
import { UsersPage } from "./pages/UsersPage";
import { FamiliesPage } from "./pages/FamiliesPage";
import { ChildrenPage } from "./pages/ChildrenPage";
import { DevicesPage } from "./pages/DevicesPage";
import { AnalyticsPage } from "./pages/AnalyticsPage";
import { ExportsPage } from "./pages/ExportsPage";
import { AuditPage } from "./pages/AuditPage";
import { OperatorsPage } from "./pages/OperatorsPage";
import { LoginPage } from "./pages/LoginPage";

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex h-screen w-full items-center justify-center bg-background">
        <div className="h-8 w-8 animate-spin rounded-full border-b-2 border-primary" />
      </div>
    );
  }

  if (!user || user.role !== "super_admin") {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

export function App() {
  return (
    <ThemeProvider defaultTheme="dark">
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />

            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <AdminLayout />
                </ProtectedRoute>
              }
            >
              <Route index element={<DashboardPage />} />
              <Route path="users" element={<UsersPage />} />
              <Route path="families" element={<FamiliesPage />} />
              <Route path="children" element={<ChildrenPage />} />
              <Route path="devices" element={<DevicesPage />} />
              <Route path="analytics" element={<AnalyticsPage />} />
              <Route path="exports" element={<ExportsPage />} />
              <Route path="audit" element={<AuditPage />} />
              <Route path="operators" element={<OperatorsPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  );
}

export default App;
