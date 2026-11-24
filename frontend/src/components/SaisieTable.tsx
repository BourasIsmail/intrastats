"use client";

import { Fragment } from "react";
import { useI18n } from "@/lib/i18n";
import type { CleTraduction } from "@/lib/i18n/fr";
import { CLES, parseEntier, type Saisi, type Verdict } from "@/lib/rules";
import type { CleValeur, EtatCentre, Ligne } from "@/lib/types";

export interface RowState {
  saisi: Saisi;
  etat: EtatCentre;
  motif: string;
}

interface Props {
  lignes: Ligne[];
  etats: Record<number, RowState>;
  verdicts: Record<number, Verdict>;
  editable: boolean;
  /** Met en évidence les écarts notables par rapport à la période précédente (vérification). */
  showEcarts: boolean;
  onCell?: (id: number, cle: CleValeur, valeur: string) => void;
  onEtat?: (id: number, etat: EtatCentre) => void;
  onMotif?: (id: number, motif: string) => void;
}

const ETATS: EtatCentre[] = ["ACTIF", "FERME_TEMPORAIREMENT", "FERME_DEFINITIVEMENT"];

/**
 * Tableau commun à la saisie (provincial), à la vérification (admin) et à la consultation (régional) :
 * le centre n'apparaît qu'une fois, ses activités sont groupées dessous.
 */
export function SaisieTable({ lignes, etats, verdicts, editable, showEcarts, onCell, onEtat, onMotif }: Props) {
  const { t, nom, fmt } = useI18n();
  const parCentre = new Map<number, number>();
  lignes.forEach((l) => parCentre.set(l.centreId, (parCentre.get(l.centreId) ?? 0) + 1));
  const vus = new Set<number>();
  const libelle = (cle: CleValeur) => t(`ind.${cle}` as CleTraduction);

  const totaux = CLES.map((cle) =>
    lignes.reduce((somme, l) => {
      const p = parseEntier(etats[l.centreActiviteId]?.saisi[cle] ?? "");
      return somme + (p.valeur ?? 0);
    }, 0),
  );

  return (
    <div className="table-wrap">
      <table className="saisie">
        <thead>
          <tr>
            <th rowSpan={2} className="left">{t("table.centre")}</th>
            <th rowSpan={2} className="left">{t("table.activite")}</th>
            <th colSpan={3}>{t("table.beneficiaires")}</th>
            <th colSpan={3}>{t("table.personnel")}</th>
            <th colSpan={2}>{t("table.partenaires")}</th>
            <th rowSpan={2}>{t("table.etat")}</th>
            <th rowSpan={2}>{t("table.motif")}</th>
          </tr>
          <tr>
            {CLES.map((cle) => (
              <th key={cle}>{libelle(cle)}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {lignes.map((l) => {
            const id = l.centreActiviteId;
            const etat = etats[id];
            if (!etat) return null;
            const verdict = verdicts[id];
            const premier = !vus.has(l.centreId);
            vus.add(l.centreId);
            const n = parCentre.get(l.centreId) ?? 1;
            const nomActivite = nom(l.activiteNom, l.activiteNomAr);
            const nomCentre = nom(l.centreNom, l.centreNomAr);
            return (
              <tr key={id}>
                {premier && (
                  <td rowSpan={n} className="centre">
                    <strong>{nomCentre}</strong>
                    <div className="muted" style={{ fontSize: 12 }}>{fmt(n)} {t(n > 1 ? "table.activitesPl" : "table.activiteUne")}</div>
                  </td>
                )}
                <td className="activite">
                  {nomActivite}
                  {editable && verdict && (verdict.erreurs.length > 0 || verdict.avertissements.length > 0) && (
                    <div className="ligne-erreurs">
                      {verdict.erreurs.map((m) => (
                        <span key={m} className="e"><span aria-hidden="true">✕ </span>{m}</span>
                      ))}
                      {verdict.avertissements.map((m) => (
                        <span key={m} className="w"><span aria-hidden="true">! </span>{m}</span>
                      ))}
                    </div>
                  )}
                </td>
                {CLES.map((cle) => {
                  const ecart = showEcarts && l.ecarts.includes(cle);
                  const invalide = editable && !!verdict?.champs[cle];
                  const classes = ["cell", invalide ? "invalid" : "", ecart ? "ecart" : ""].join(" ").trim();
                  const precedent = l.precedentes[cle];
                  return (
                    <td key={cle}>
                      <input
                        type={editable ? "number" : "text"}
                        inputMode="numeric"
                        min={0}
                        step={1}
                        dir="ltr"
                        className={classes}
                        readOnly={!editable}
                        aria-invalid={invalide}
                        aria-label={`${libelle(cle)} : ${nomActivite}, ${nomCentre}${ecart ? `, ${t("table.ecartAria")}` : ""}`}
                        title={showEcarts && precedent !== null ? t("table.periodePrecedente", { valeur: precedent }) : undefined}
                        value={etat.saisi[cle]}
                        onChange={(e) => onCell?.(id, cle, e.target.value)}
                      />
                    </td>
                  );
                })}
                <td>
                  <select
                    className="cell"
                    disabled={!editable}
                    aria-label={`${t("table.etat")} : ${nomActivite}, ${nomCentre}`}
                    value={etat.etat}
                    onChange={(e) => onEtat?.(id, e.target.value as EtatCentre)}
                  >
                    {ETATS.map((v) => (
                      <option key={v} value={v}>{t(`etat.${v}` as CleTraduction)}</option>
                    ))}
                  </select>
                </td>
                <td>
                  <input
                    type="text"
                    className={`cell motif${editable && verdict?.motif ? " invalid" : ""}`}
                    readOnly={!editable}
                    aria-invalid={editable && !!verdict?.motif}
                    aria-label={`${t("table.motif")} : ${nomActivite}, ${nomCentre}`}
                    value={etat.motif}
                    onChange={(e) => onMotif?.(id, e.target.value)}
                  />
                </td>
              </tr>
            );
          })}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={2} style={{ textAlign: "start" }}>{t("table.total")}</td>
            {totaux.map((total, i) => (
              <Fragment key={CLES[i]}><td>{fmt(total)}</td></Fragment>
            ))}
            <td colSpan={2} />
          </tr>
        </tfoot>
      </table>
    </div>
  );
}
