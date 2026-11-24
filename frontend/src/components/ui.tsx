"use client";

import { useI18n } from "@/lib/i18n";
import type { CleTraduction } from "@/lib/i18n/fr";
import type { SaisieStatut } from "@/lib/types";

export function StatutChip({ statut }: { statut: SaisieStatut }) {
  const { t } = useI18n();
  const tons: Record<SaisieStatut, string> = {
    NON_COMMENCEE: "grey",
    BROUILLON: "warn",
    SOUMIS: "info",
    VALIDE: "ok",
    A_CORRIGER: "bad",
  };
  return <span className={`chip ${tons[statut]}`}>{t(`statut.${statut}` as CleTraduction)}</span>;
}

export function PeriodeChip({ ouverte }: { ouverte: boolean }) {
  const { t } = useI18n();
  return <span className={`chip ${ouverte ? "ok" : "grey"}`}>{t(ouverte ? "periode.chipOuverte" : "periode.chipCloturee")}</span>;
}

export function Select(props: {
  id: string;
  label: string;
  value: string | number;
  onChange: (v: string) => void;
  options: { value: string | number; label: string }[];
  vide?: string;
}) {
  return (
    <div className="field">
      <label htmlFor={props.id}>{props.label}</label>
      <select id={props.id} className="select" value={props.value} onChange={(e) => props.onChange(e.target.value)}>
        {props.vide !== undefined && <option value="">{props.vide}</option>}
        {props.options.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
    </div>
  );
}
