import { ar } from "./ar";
import { fr, type CleTraduction } from "./fr";

// Partie sans React : utilisable aussi dans les composants serveur (mise en page, métadonnées) et dans les tests.

export type Lang = "fr" | "ar";
export type Params = Record<string, string | number>;
export type Translate = (cle: CleTraduction, params?: Params) => string;
export type { CleTraduction };

export const DICTIONNAIRES: Record<Lang, Record<CleTraduction, string>> = { fr, ar };

/** Chiffres latins aussi en arabe : c'est l'usage dans l'administration marocaine et cela évite de mélanger les saisies. */
export const LOCALES: Record<Lang, string> = { fr: "fr-FR", ar: "ar-MA-u-nu-latn" };

export const COOKIE_LANGUE = "lang";

export function traduire(lang: Lang, cle: CleTraduction, params?: Params): string {
  const texte = DICTIONNAIRES[lang][cle] ?? DICTIONNAIRES.fr[cle] ?? cle;
  if (!params) return texte;
  return texte.replace(/\{(\w+)\}/g, (_, nom: string) => (nom in params ? String(params[nom]) : `{${nom}}`));
}

export function langueValide(valeur: string | undefined | null): Lang {
  return valeur === "ar" ? "ar" : "fr";
}
