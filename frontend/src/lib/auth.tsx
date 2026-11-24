"use client";

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError, post } from "./api";
import type { User } from "./types";

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (login: string, motDePasse: string) => Promise<User>;
  logout: () => Promise<void>;
  can: (droit: string) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api<User>("/api/auth/me")
      .then(setUser)
      .catch((e) => {
        if (!(e instanceof ApiError) || e.status !== 401) console.error(e);
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (login: string, motDePasse: string) => {
    const u = await post<User>("/api/auth/login", { login, motDePasse });
    setUser(u);
    return u;
  }, []);

  const logout = useCallback(async () => {
    await post("/api/auth/logout");
    setUser(null);
  }, []);

  const can = useCallback((droit: string) => !!user?.droits.includes(droit), [user]);

  return <AuthContext.Provider value={{ user, loading, login, logout, can }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth doit être utilisé dans AuthProvider");
  return ctx;
}

/** Redirige vers la page de connexion quand la session est absente. */
export function useRequireUser(): { user: User | null; loading: boolean } {
  const { user, loading } = useAuth();
  const router = useRouter();
  useEffect(() => {
    if (!loading && !user) router.replace("/connexion");
  }, [loading, user, router]);
  return { user, loading };
}
