"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { LangSwitch } from "@/components/LangSwitch";
import { useAuth, useRequireUser } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";

export default function AppLayout({ children }: { children: ReactNode }) {
  const { user, loading } = useRequireUser();
  const { logout, can } = useAuth();
  const { t } = useI18n();
  const pathname = usePathname();
  const router = useRouter();

  if (loading || !user) return null;

  const liens = [
    { href: "/", label: t("nav.tableauDeBord"), visible: true },
    { href: "/centres", label: t("nav.centres"), visible: can("CENTRE_LIRE") },
    { href: "/statistiques", label: t("nav.statistiques"), visible: can("STATISTIQUE_LIRE") },
    { href: "/periodes", label: t("nav.periodes"), visible: can("PERIODE_GERER") },
    { href: "/utilisateurs", label: t("nav.utilisateurs"), visible: can("UTILISATEUR_GERER") },
  ].filter((l) => l.visible);

  const actif = (href: string) => (href === "/" ? pathname === "/" : pathname.startsWith(href));

  return (
    <div className="shell">
      <nav className="sidebar" aria-label={t("nav.principale")}>
        <div className="brand">
          <strong>{t("app.nom")}</strong>
          <span>{t("app.slogan")}</span>
        </div>
        <ul className="nav">
          {liens.map((l) => (
            <li key={l.href}>
              <Link href={l.href} aria-current={actif(l.href) ? "page" : undefined}>{l.label}</Link>
            </li>
          ))}
        </ul>
        <div className="me">
          <strong>{user.nom}</strong>
          <small>{t(`role.${user.role}`)}</small>
          <LangSwitch />
          <button
            type="button"
            onClick={async () => {
              await logout();
              router.replace("/connexion");
            }}
          >
            {t("action.deconnecter")}
          </button>
        </div>
      </nav>
      <main className="main">{children}</main>
    </div>
  );
}
