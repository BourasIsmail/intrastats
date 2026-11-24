"use client";

import { useState, type FormEvent } from "react";
import { post } from "@/lib/api";
import { Modal } from "@/components/Modal";
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
  const [ouvert, setOuvert] = useState(false);
  const [erreurForm, setErreurForm] = useState<string | null>(null);
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

  async function creer(e: FormEvent) {
    e.preventDefault();
    setErreurForm(null);
    try {
      await post("/api/periodes", { mois, annee, anneeStat: anneeStat || String(annee), compteeEnStats: comptee });
      await reload();
      setOuvert(false);
      setMessage({ ton: "ok", texte: t("periodes.msgCreee") });
    } catch (err) {
      setErreurForm(messageErreur(err));
    }
  }

  return (
    <>
      <div className="page-head">
        <h1>{t("periodes.titre")}</h1>
        <button className="btn primary" type="button" onClick={() => { setMessage(null); setErreurForm(null); setOuvert(true); }}>{t("periodes.nouvelle")}</button>
      </div>
      {message && <div className={`banner ${message.ton}`} role="status">{message.texte}</div>}
      <>
        <section className="card">
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

        {ouvert && (
        <Modal titre={t("periodes.creer")} onClose={() => setOuvert(false)}>
        <form onSubmit={creer} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
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
          {erreurForm && <div className="banner bad" role="alert">{erreurForm}</div>}
          <div className="actions">
            <button className="btn primary" type="submit">{t("periodes.creerOuvrir")}</button>
            <button className="btn" type="button" onClick={() => setOuvert(false)}>{t("action.annuler")}</button>
          </div>
        </form>
        </Modal>
        )}
      </>
    </>
  );
}
