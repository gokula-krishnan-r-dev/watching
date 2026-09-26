import React, { createContext, useContext, useState, useEffect } from "react";

export interface SuperAdminUser {
  uid: string;
  email: string;
  displayName: string;
  role: "super_admin";
}

interface AuthContextType {
  user: SuperAdminUser | null;
  loading: boolean;
  login: (email: string) => Promise<boolean>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  loading: true,
  login: async () => false,
  logout: () => {},
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<SuperAdminUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Check saved session or auto-initialize super admin in demo/dev mode
    const saved = localStorage.getItem("meritscreen_superadmin_session");
    if (saved) {
      try {
        setUser(JSON.parse(saved));
      } catch {
        // Fallback default super admin
        setUser({
          uid: "admin_super_01",
          email: "superadmin@meritscreen.internal",
          displayName: "Super Admin",
          role: "super_admin",
        });
      }
    } else {
      // Auto login as default Super Admin for swift usability
      const defaultAdmin: SuperAdminUser = {
        uid: "admin_super_01",
        email: "superadmin@meritscreen.internal",
        displayName: "Super Admin",
        role: "super_admin",
      };
      setUser(defaultAdmin);
      localStorage.setItem("meritscreen_superadmin_session", JSON.stringify(defaultAdmin));
    }
    setLoading(false);
  }, []);

  const login = async (email: string) => {
    const adminUser: SuperAdminUser = {
      uid: `admin_${Date.now()}`,
      email: email || "superadmin@meritscreen.internal",
      displayName: email.split("@")[0].toUpperCase() || "SUPER ADMIN",
      role: "super_admin",
    };
    setUser(adminUser);
    localStorage.setItem("meritscreen_superadmin_session", JSON.stringify(adminUser));
    return true;
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem("meritscreen_superadmin_session");
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
