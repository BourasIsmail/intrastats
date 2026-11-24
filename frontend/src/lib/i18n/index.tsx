"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { ApiError } from "../api";
import type { Periode } from "../types";
import { COOKIE_LANGUE, LOCALES, traduire, langueValide, type CleTraduction, type Lang, type Translate } from "./core";
import { fr } from "./fr";

export { COOKIE_LANGUE, langueValide, traduire };
export type { Lang, Translate };

interface I18n {
  lang: Lang;
  dir: "ltr" | "rtl";
  t: Translate;
  setLang: (lang: Lang) => void;
  /** Nom à afficher : l'arabe en interface arabe quand il existe, sinon le français. */
  nom: (nomFr: string | null | undefined, nomAr: string | null | undefined) => string;
  fmt: (n: number) => string;
  periode: (p: Pick<Periode, "mois" | "annee">) => string;
  periodeComplete: (p: Pick<Periode, "mois" | "annee" | "statut">) => string;
  erreur: (e: unknown) => string;
}

const Contexte = createContext<I18n | null>(null);

export function I18nProvider({ initiale, children }: { initiale: Lang; children: ReactNode }) {
  const [lang, setLangEtat] = useState<Lang>(initiale);

  const setLang = useCallback((l: Lang) => {
    setLangEtat(l);
    document.cookie = `${COOKIE_LANGUE}=${l}; path=/; max-age=31536000; samesite=lax`;
  }, []);

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
    document.title = traduire(lang, "app.titre");
  }, [lang]);

  const valeur = useMemo<I18n>(() => {
    const t: Translate = (cle, params) => traduire(lang, cle, params);
    const periode = (p: Pick<Periode, "mois" | "annee">) => `${t(`mois.${p.mois}` as CleTraduction)} ${p.annee}`;
    return {
      lang,
      dir: lang === "ar" ? "rtl" : "ltr",
      t,
      setLang,
      nom: (nomFr, nomAr) => (lang === "ar" && nomAr ? nomAr : nomFr ?? ""),
      fmt: (n) => n.toLocaleString(LOCALES[lang]),
      periode,
      periodeComplete: (p) =>
        `${periode(p)} (${t(p.statut === "OUVERTE" ? "periode.ouverteMin" : "periode.clotureeMin")})`,
      erreur: (e) => {
        if (!(e instanceof ApiError)) return e instanceof Error ? e.message : t("erreur.generique");
        const details = e.details.length ? ` : ${e.details.join(" ; ")}` : "";
        if (lang === "fr") return `${e.message}${details}`;
        // Les messages du serveur sont en français : en arabe, on affiche un message général traduit
        // et on garde le détail d'origine entre parenthèses pour ne rien perdre.
        const cle = `erreur.${e.status}` as CleTraduction;
        const general = t(cle in fr ? cle : "erreur.generique");
        return e.message ? `${general} (${e.message}${details})` : general;
      },
    };
  }, [lang, setLang]);

  return <Contexte.Provider value={valeur}>{children}</Contexte.Provider>;
}

export function useI18n(): I18n {
  const ctx = useContext(Contexte);
  if (!ctx) throw new Error("useI18n doit être utilisé dans I18nProvider");
  return ctx;
}
