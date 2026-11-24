import { describe, expect, it } from "vitest";
import { traduire, type Lang, type Translate } from "./i18n/core";
import { fr } from "./i18n/fr";
import { ar } from "./i18n/ar";
import { CLES, ecartNotable, parseEntier, verifier, versSaisi, versValeurs, type Saisi } from "./rules";

const tFr: Translate = (cle, params) => traduire("fr", cle, params);
const tAr: Translate = (cle, params) => traduire("ar", cle, params);

const complet = (): Saisi => ({
  masculins: "10",
  feminins: "20",
  alphabetisation: "15",
  agentsTitulaires: "1",
  agentsVacataires: "0",
  agentsVolontaires: "2",
  agentsAssociations: "0",
  agentsAutreDep: "0",
});

describe("parseEntier", () => {
  it("accepte les entiers de 0 à 99 999", () => {
    expect(parseEntier("0").valeur).toBe(0);
    expect(parseEntier(" 42 ").valeur).toBe(42);
    expect(parseEntier("99999").valeur).toBe(99999);
  });
  it("refuse décimaux, négatifs, texte et trop grand", () => {
    expect(parseEntier("1.5").erreur).toBeDefined();
    expect(parseEntier("-3").erreur).toBeDefined();
    expect(parseEntier("abc").erreur).toBeDefined();
    expect(parseEntier("100000").erreur).toBeDefined();
  });
  it("signale le champ vide sans erreur", () => {
    expect(parseEntier("").vide).toBe(true);
  });
});

describe("verifier", () => {
  it("valide une ligne complète", () => {
    expect(verifier(complet(), "ACTIF", "", true, tFr).erreurs).toEqual([]);
  });

  it("n'exige les champs vides qu'après un essai de soumission", () => {
    const s = { ...complet(), agentsAutreDep: "" };
    expect(verifier(s, "ACTIF", "", false, tFr).erreurs).toEqual([]);
    const strict = verifier(s, "ACTIF", "", true, tFr);
    expect(strict.erreurs).toHaveLength(1);
    expect(strict.champs.agentsAutreDep).toBe("obligatoire");
  });

  it("refuse une alphabétisation supérieure au total des bénéficiaires", () => {
    const v = verifier({ ...complet(), alphabetisation: "31" }, "ACTIF", "", true, tFr);
    expect(v.champs.alphabetisation).toBeDefined();
    expect(v.erreurs.join(" ")).toContain("dépasse");
  });

  it("exige un motif si le centre n'est pas actif", () => {
    expect(verifier(complet(), "FERME_TEMPORAIREMENT", "ab", true, tFr).motif).toBeDefined();
    expect(verifier(complet(), "FERME_TEMPORAIREMENT", "Travaux de réfection", true, tFr).motif).toBeUndefined();
  });

  it("avertit sans bloquer quand un centre actif n'a aucun bénéficiaire", () => {
    const v = verifier({ ...complet(), masculins: "0", feminins: "0", alphabetisation: "0" }, "ACTIF", "", true, tFr);
    expect(v.erreurs).toEqual([]);
    expect(v.avertissements).toHaveLength(1);
  });

  it("produit les mêmes erreurs en arabe, avec les libellés arabes", () => {
    const v = verifier({ ...complet(), alphabetisation: "31", agentsAutreDep: "" }, "FERME_TEMPORAIREMENT", "", true, tAr);
    expect(v.erreurs).toHaveLength(3);
    expect(v.erreurs.join(" ")).toContain(ar["ind.agentsAutreDep"]);
    expect(v.erreurs.join(" ")).not.toContain("dépasse");
    expect(v.champs.alphabetisation).toBe(ar["regle.alphaCourt"]);
  });
});

describe("ecartNotable", () => {
  it("demande au moins 50 % et au moins 5 unités", () => {
    expect(ecartNotable(78, 30)).toBe(true);
    expect(ecartNotable(0, 21)).toBe(true);
    expect(ecartNotable(3, 1)).toBe(false);
    expect(ecartNotable(21, 20)).toBe(false);
    expect(ecartNotable(null, 20)).toBe(false);
  });
});

describe("conversions", () => {
  it("fait l'aller-retour entre texte et nombres", () => {
    const valeurs = versValeurs(complet());
    expect(valeurs.masculins).toBe(10);
    expect(versSaisi(valeurs)).toEqual(complet());
    expect(CLES).toHaveLength(8);
  });
});

describe("traductions", () => {
  const clesFr = Object.keys(fr).sort();
  const clesAr = Object.keys(ar).sort();

  it("l'arabe définit exactement les mêmes clés que le français", () => {
    expect(clesAr).toEqual(clesFr);
  });

  it("aucune traduction n'est vide", () => {
    for (const cle of clesFr) {
      expect((ar as Record<string, string>)[cle].trim(), `ar: ${cle}`).not.toBe("");
      expect((fr as Record<string, string>)[cle].trim(), `fr: ${cle}`).not.toBe("");
    }
  });

  it("les paramètres {nom} sont les mêmes dans les deux langues", () => {
    const params = (s: string) => (s.match(/\{\w+\}/g) ?? []).sort().join(",");
    for (const cle of clesFr) {
      expect(params((ar as Record<string, string>)[cle]), cle).toBe(params((fr as Record<string, string>)[cle]));
    }
  });

  it("la traduction remplace les paramètres et garde les inconnus", () => {
    expect(traduire("fr" as Lang, "dash.noteValidees", { validees: 3, aCorriger: 1 })).toBe("3 validées · 1 à corriger");
    expect(traduire("ar" as Lang, "centres.compte", { n: 5 })).toContain("5");
    expect(traduire("fr", "centres.affichage", { debut: 1 })).toContain("{fin}");
  });

  it("les textes arabes contiennent de l'arabe (pas de français recopié)", () => {
    const arabe = /[؀-ۿ]/;
    // Gabarits sans texte (« {libelle}: {message}. ») et libellés volontairement latins (nom de la langue, formats de fichier).
    const exceptions = new Set(["lang.fr", "lang.ar", "stats.exportPdf", "stats.exportExcel", "regle.champ"]);
    for (const cle of clesAr) {
      if (exceptions.has(cle)) continue;
      expect(arabe.test((ar as Record<string, string>)[cle]), cle).toBe(true);
    }
  });
});
