"use client";

import { useState, type FormEvent } from "react";
import { post } from "@/lib/api";
import { usePeriodes } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import type { CleTraduction } from "@/lib/i18n/fr";

export default function PeriodesPage() {
  const { periodes, reload } = usePeriodes();
  const { t, periode, erreur: messageErreur } = useI18n();
  const aujourdhui = new Date();
  const [mois, setMois] = useState(aujourdhui.getMonth() + 1);
  const [annee, setAnnee] = useState(aujourdhui.getFullYear());
  const [anneeStat, setAnneeStat] = useState("");
  const [comptee, setComptee] = useState(true);
  const [message, setMessage] = useState<{ ton: "ok" | "bad"; texte: string } | null>(null);

  async function agir(fn: () => Promise<unknown>, succes: CleTraduction) {
    setMessage(null);
    try {
      await fn();
      await reload();
      setMessage({ ton: "ok", texte: t(succes) });
    } catch (e) {
      setMessage({ ton: "bad", texte: messageErreur(e) });
    }
  }

  function creer(e: FormEvent) {
    e.preventDefault();
    agir(() => post("/api/periodes", { mois, annee, anneeStat: anneeStat || String(annee), compteeEnStats: comptee }), "periodes.msgCreee");
  }

  return (
    <>
      <div className="page-head"><h1>{t("periodes.titre")}</h1></div>
      {message && <div className={`banner ${message.ton}`} role="status">{message.texte}</div>}
      <div style={{ display: "flex", flexWrap: "wrap", gap: 16, alignItems: "flex-start" }}>
        <section className="card" style={{ flex: "999 1 520px", minWidth: 0 }}>
          <h2 style={{ marginBottom: 8 }}>{t("periodes.historique")}</h2>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>{t("periodes.colPeriode")}</th><th>{t("periodes.colAnneeStat")}</th><th>{t("periodes.colComptee")}</th>
                  <th>{t("periodes.colEtat")}</th><th><span className="sr-only">{t("periodes.colAction")}</span></th>
                </tr>
              </thead>
              <tbody>
                {periodes.map((p) => (
                  <tr key={p.id}>
                    <td><strong>{periode(p)}</strong></td>
                    <td className="muted">{p.anneeStat}</td>
                    <td>{t(p.compteeEnStats ? "periodes.oui" : "periodes.non")}</td>
                    <td><span className={`chip ${p.statut === "OUVERTE" ? "ok" : "grey"}`}>{t(p.statut === "OUVERTE" ? "periodes.ouverte" : "periodes.cloturee")}</span></td>
                    <td style={{ textAlign: "end" }}>
                      {p.statut === "OUVERTE" ? (
                        <button className="btn link" type="button" onClick={() => agir(() => post(`/api/periodes/${p.id}/cloturer`), "periodes.msgCloturee")}>{t("periodes.cloturer")}</button>
                      ) : (
                        <button className="btn link" type="button" onClick={() => agir(() => post(`/api/periodes/${p.id}/rouvrir`), "periodes.msgRouverte")}>{t("periodes.rouvrir")}</button>
                      )}
                    </td>
                  </tr>
                ))}
                {periodes.length === 0 && <tr><td colSpan={5} className="muted">{t("periodes.aucune")}</td></tr>}
              </tbody>
            </table>
          </div>
        </section>

        <form className="card" onSubmit={creer} style={{ flex: "1 1 300px", minWidth: 0, display: "flex", flexDirection: "column", gap: 16 }}>
          <h2>{t("periodes.creer")}</h2>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div className="field">
              <label htmlFor="mois">{t("periodes.mois")}</label>
              <select id="mois" className="select" value={mois} onChange={(e) => setMois(Number(e.target.value))}>
                {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                  <option key={m} value={m}>{t(`mois.${m}` as CleTraduction)}</option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="annee">{t("periodes.annee")}</label>
              <input id="annee" className="input" type="number" dir="ltr" value={annee} onChange={(e) => setAnnee(Number(e.target.value))} />
            </div>
          </div>
          <div className="field">
            <label htmlFor="astat">{t("periodes.anneeStat")}</label>
            <input id="astat" className="input" dir="ltr" value={anneeStat} placeholder={String(annee)} onChange={(e) => setAnneeStat(e.target.value)} />
          </div>
          <label className="row"><input type="checkbox" checked={comptee} onChange={(e) => setComptee(e.target.checked)} style={{ width: 20, height: 20 }} />{t("periodes.compter")}</label>
          <button className="btn primary" type="submit">{t("periodes.creerOuvrir")}</button>
        </form>
      </div>
    </>
  );
}
