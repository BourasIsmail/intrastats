import type { Translate } from "./i18n/core";
import type { CleValeur, EtatCentre, Valeurs } from "./types";

/** Règles identiques à celles du serveur (Regles.java) : ici pour un retour immédiat, le serveur fait foi. */

export const MAX = 99_999;
export const MOTIF_MIN = 5;

export const CLES: CleValeur[] = [
  "masculins",
  "feminins",
  "alphabetisation",
  "agentsTitulaires",
  "agentsVacataires",
  "agentsVolontaires",
  "agentsAssociations",
  "agentsAutreDep",
];

/** Valeur saisie (texte). */
export type Saisi = Record<CleValeur, string>;

export interface Verdict {
  /** Message d'erreur par champ (absent = valide). */
  champs: Partial<Record<CleValeur, string>>;
  /** Erreur sur le motif. */
  motif?: string;
  /** Erreurs qui empêchent de soumettre. */
  erreurs: string[];
  /** Avertissements qui n'empêchent pas de soumettre. */
  avertissements: string[];
}

type CleErreur = "regle.entier" | "regle.negatif" | "regle.max";

export function parseEntier(texte: string): { vide: boolean; valeur?: number; erreur?: CleErreur } {
  const t = texte.trim();
  if (t === "") return { vide: true };
  if (!/^-?\d+$/.test(t)) return { vide: false, erreur: "regle.entier" };
  const n = Number(t);
  if (n < 0) return { vide: false, erreur: "regle.negatif" };
  if (n > MAX) return { vide: false, erreur: "regle.max" };
  return { vide: false, valeur: n };
}

/**
 * Contrôle une ligne. Les champs vides ne sont signalés que si `exigerTout` est vrai
 * (après un essai de soumission) ; les autres règles sont toujours appliquées.
 */
export function verifier(saisi: Saisi, etat: EtatCentre, motif: string, exigerTout: boolean, t: Translate): Verdict {
  const champs: Verdict["champs"] = {};
  const erreurs: string[] = [];
  const avertissements: string[] = [];
  const nombres: Partial<Record<CleValeur, number>> = {};
  const libelle = (cle: CleValeur) => t(`ind.${cle}`);
  const ligne = (cle: CleValeur, message: string) => t("regle.champ", { libelle: libelle(cle), message });

  for (const cle of CLES) {
    const p = parseEntier(saisi[cle]);
    if (p.erreur) {
      const message = t(p.erreur, { max: MAX.toLocaleString("fr-FR") });
      champs[cle] = message;
      erreurs.push(ligne(cle, message));
    } else if (p.vide) {
      if (exigerTout) {
        const message = t("regle.obligatoire");
        champs[cle] = message;
        erreurs.push(ligne(cle, message));
      }
    } else {
      nombres[cle] = p.valeur;
    }
  }

  const { masculins, feminins, alphabetisation } = nombres;
  if (alphabetisation !== undefined && masculins !== undefined && feminins !== undefined) {
    if (alphabetisation > masculins + feminins) {
      champs.alphabetisation = t("regle.alphaCourt");
      erreurs.push(t("regle.alphaLong", { alpha: alphabetisation, total: masculins + feminins }));
    }
  }

  let motifErreur: string | undefined;
  if (etat !== "ACTIF" && motif.trim().length < MOTIF_MIN) {
    motifErreur = t("regle.motifCourt", { min: MOTIF_MIN });
    erreurs.push(t("regle.motifLong"));
  }

  if (etat === "ACTIF" && masculins !== undefined && feminins !== undefined && masculins + feminins === 0) {
    avertissements.push(t("regle.aucunBeneficiaire"));
  }
  return { champs, motif: motifErreur, erreurs, avertissements };
}

/** Écart notable par rapport à la période précédente : au moins 50 % et au moins 5 unités. */
export function ecartNotable(actuel: number | null, precedent: number | null): boolean {
  if (actuel === null || precedent === null) return false;
  const diff = Math.abs(actuel - precedent);
  if (diff < 5) return false;
  const pct = precedent === 0 ? 100 : (100 * diff) / precedent;
  return pct >= 50;
}

export function versTexte(v: number | null): string {
  return v === null || v === undefined ? "" : String(v);
}

export function versSaisi(v: Valeurs): Saisi {
  const s = {} as Saisi;
  for (const cle of CLES) s[cle] = versTexte(v[cle]);
  return s;
}

export function versValeurs(s: Saisi): Valeurs {
  const v = {} as Valeurs;
  for (const cle of CLES) {
    const p = parseEntier(s[cle]);
    v[cle] = p.valeur ?? null;
  }
  return v;
}
