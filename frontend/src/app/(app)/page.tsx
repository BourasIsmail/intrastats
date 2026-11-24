"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { usePeriodes } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import type { Resume, TableauDeBord } from "@/lib/types";
import { PeriodeChip, Select, StatutChip } from "@/components/ui";

export default function TableauDeBordPage() {
  const { user } = useAuth();
  const { t, nom, fmt, periode, periodeComplete, erreur: messageErreur } = useI18n();
  const { periodes, periodeId, setPeriodeId } = usePeriodes();
  const [bord, setBord] = useState<TableauDeBord | null>(null);
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    if (!periodeId) return;
    setErreur(null);
    Promise.all([
      api<TableauDeBord>(`/api/tableau-de-bord?periodeId=${periodeId}`),
      api<Resume[]>(`/api/saisies?periodeId=${periodeId}`),
    ])
      .then(([b, r]) => {
        setBord(b);
        setResumes(r);
      })
      .catch((e) => setErreur(messageErreur(e)));
  }, [periodeId, messageErreur]);

  const periodeCourante = periodes.find((p) => p.id === periodeId);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{t("nav.tableauDeBord")}</h1>
          <div className="sub">
            {bord && <PeriodeChip ouverte={bord.periodeOuverte} />}
            {periodeCourante && <span>{periode(periodeCourante)}</span>}
          </div>
        </div>
        <Select id="periode" label={t("stats.periode")} value={periodeId ?? ""} onChange={(v) => setPeriodeId(Number(v))}
                options={periodes.map((p) => ({ value: p.id, label: periodeComplete(p) }))} />
      </div>

      {erreur && <div className="banner bad" role="alert">{erreur}</div>}
      {periodes.length === 0 && (
        <div className="banner info">{t("dash.aucunePeriode")}{user?.role === "ADMIN" ? ` ${t("dash.creerPeriode")}` : ""}</div>
      )}

      {bord && (
        <section className="grid-kpi">
          <div className="card kpi"><span className="label">{t("dash.provincesSoumises")}</span>
            <span className="value">{fmt(bord.soumises + bord.validees)} / {fmt(bord.provinces)}</span>
            <span className="note">{t("dash.noteValidees", { validees: bord.validees, aCorriger: bord.aCorriger })}</span></div>
          <div className="card kpi"><span className="label">{t("dash.centres")}</span><span className="value">{fmt(bord.centres)}</span></div>
          <div className="card kpi"><span className="label">{t("dash.activitesASaisir")}</span><span className="value">{fmt(bord.activites)}</span></div>
          <div className="card kpi"><span className="label">{t("dash.beneficiaires")}</span>
            <span className="value">{fmt(bord.beneficiaires)}</span>
            {periodeCourante && <span className="note">{periode(periodeCourante)}</span>}</div>
        </section>
      )}

      <section className="card">
        <h2 style={{ marginBottom: 8 }}>{t("dash.avancement")}</h2>
        <div className="table-wrap">
          <table className="data">
            <thead><tr><th>{t("dash.colProvince")}</th><th>{t("dash.colRegion")}</th><th>{t("dash.colActivites")}</th><th>{t("dash.colStatut")}</th></tr></thead>
            <tbody>
              {resumes.map((r) => (
                <tr key={r.provinceId}>
                  <td><strong>{nom(r.provinceNom, r.provinceNomAr)}</strong></td>
                  <td className="muted">{nom(r.regionNom, r.regionNomAr)}</td>
                  <td className="num">{fmt(r.nbLignes)}</td>
                  <td><StatutChip statut={r.statut} /></td>
                </tr>
              ))}
              {resumes.length === 0 && <tr><td colSpan={4} className="muted">{t("dash.aucuneProvince")}</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
