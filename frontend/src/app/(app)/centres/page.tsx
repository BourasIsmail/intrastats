"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useTerritoire } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import type { CentreSummary, Page } from "@/lib/types";
import { Select } from "@/components/ui";

const TAILLE = 20;

export default function CentresPage() {
  const { provinces } = useTerritoire();
  const { can } = useAuth();
  const { t, nom, fmt, erreur: messageErreur } = useI18n();
  const [q, setQ] = useState("");
  const [recherche, setRecherche] = useState("");
  const [provinceId, setProvinceId] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<CentreSummary> | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  // La recherche part 300 ms après la dernière frappe.
  useEffect(() => {
    const timer = setTimeout(() => {
      setRecherche(q);
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [q]);

  useEffect(() => {
    const params = new URLSearchParams({ page: String(page), size: String(TAILLE) });
    if (recherche) params.set("q", recherche);
    if (provinceId) params.set("provinceId", provinceId);
    api<Page<CentreSummary>>(`/api/centres?${params}`)
      .then((d) => {
        setData(d);
        setErreur(null);
      })
      .catch((e) => setErreur(messageErreur(e)));
  }, [recherche, provinceId, page, messageErreur]);

  const total = data?.total ?? 0;
  const debut = total === 0 ? 0 : page * TAILLE + 1;
  const fin = Math.min((page + 1) * TAILLE, total);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{t("centres.titre")}</h1>
          <div className="sub">{t("centres.compte", { n: fmt(total) })}</div>
        </div>
        {can("CENTRE_GERER") && <Link className="btn primary" href="/centres/nouveau">{t("centres.ajouter")}</Link>}
      </div>
      {erreur && <div className="banner bad" role="alert">{erreur}</div>}

      <section className="card">
        <div className="filters-grid">
          <div className="field">
            <label htmlFor="q">{t("centres.recherche")}</label>
            <input id="q" type="search" className="input" placeholder={t("centres.recherchePlaceholder")} value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <Select id="province" label={t("centres.province")} value={provinceId} vide={t("centres.toutes")}
                  onChange={(v) => { setProvinceId(v); setPage(0); }}
                  options={provinces.map((p) => ({ value: p.id, label: nom(p.nomFr, p.nomAr) }))} />
        </div>
      </section>

      <section className="card">
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t("centres.colCode")}</th><th>{t("centres.colNomFr")}</th><th>{t("centres.colNomAr")}</th>
                <th>{t("centres.colProvince")}</th><th>{t("centres.colType")}</th><th>{t("centres.colActivites")}</th>
              </tr>
            </thead>
            <tbody>
              {data?.content.map((c) => (
                <tr key={c.id}>
                  <td className="muted num" style={{ textAlign: "start" }} dir="ltr">{c.code}</td>
                  <td><Link href={`/centres/${c.id}`} style={{ fontWeight: 700, textDecoration: "none" }}>{c.nomFr}</Link></td>
                  <td lang="ar" dir="rtl" style={{ fontSize: 16 }}>{c.nomAr}</td>
                  <td>{c.provinceNom}</td>
                  <td className="muted">{c.type}</td>
                  <td className="num" style={{ textAlign: "start" }}>{fmt(c.nbActivites)}</td>
                </tr>
              ))}
              {data && data.content.length === 0 && <tr><td colSpan={6} className="muted">{t("centres.aucun")}</td></tr>}
            </tbody>
          </table>
        </div>
        <div className="row" style={{ justifyContent: "space-between", paddingTop: 14, flexWrap: "wrap" }}>
          <span className="muted">{t("centres.affichage", { debut: fmt(debut), fin: fmt(fin), total: fmt(total) })}</span>
          <div className="actions">
            <button className="btn" type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>{t("action.precedent")}</button>
            <button className="btn" type="button" disabled={fin >= total} onClick={() => setPage(page + 1)}>{t("action.suivant")}</button>
          </div>
        </div>
      </section>
    </>
  );
}
