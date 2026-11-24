"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api, post } from "@/lib/api";
import { Modal } from "@/components/Modal";
import { useTerritoire } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import type { Role, UtilisateurDto } from "@/lib/types";

interface Created {
  utilisateur: UtilisateurDto;
  motDePasseTemporaire: string;
}

export default function UtilisateursPage() {
  const { regions, provinces } = useTerritoire();
  const { t, nom: nomAffiche, fmt, erreur: messageErreur } = useI18n();
  const [liste, setListe] = useState<UtilisateurDto[]>([]);
  const [message, setMessage] = useState<{ ton: "ok" | "bad"; texte: string } | null>(null);
  const [nom, setNom] = useState("");
  const [login, setLogin] = useState("");
  const [matricule, setMatricule] = useState("");
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<Role>("PROVINCIAL");
  const [provinceId, setProvinceId] = useState("");
  const [regionId, setRegionId] = useState("");
  const [ouvert, setOuvert] = useState(false);
  const [erreurForm, setErreurForm] = useState<string | null>(null);

  const charger = useCallback(() => {
    api<UtilisateurDto[]>("/api/utilisateurs").then(setListe).catch((e) => setMessage({ ton: "bad", texte: messageErreur(e) }));
  }, [messageErreur]);
  useEffect(charger, [charger]);

  async function creer(e: FormEvent) {
    e.preventDefault();
    setMessage(null);
    setErreurForm(null);
    try {
      const r = await post<Created>("/api/utilisateurs", {
        nom, login, matricule: matricule || null, email: email || null, role,
        provinceId: role === "PROVINCIAL" && provinceId ? Number(provinceId) : null,
        regionId: role === "REGIONAL" && regionId ? Number(regionId) : null,
      });
      setMessage({ ton: "ok", texte: t("users.msgCree", { mdp: r.motDePasseTemporaire }) });
      setNom(""); setLogin(""); setMatricule(""); setEmail("");
      setOuvert(false);
      charger();
    } catch (err) {
      setErreurForm(messageErreur(err));
    }
  }

  async function reinitialiser(u: UtilisateurDto) {
    setMessage(null);
    try {
      const r = await post<Created>(`/api/utilisateurs/${u.id}/reinitialiser`);
      setMessage({ ton: "ok", texte: t("users.msgReinit", { login: u.login, mdp: r.motDePasseTemporaire }) });
      charger();
    } catch (err) {
      setMessage({ ton: "bad", texte: messageErreur(err) });
    }
  }

  const perimetre = (u: UtilisateurDto) => {
    if (u.role === "ADMIN") return t("users.toutesRegions");
    if (u.role === "REGIONAL") {
      const r = regions.find((x) => x.id === u.regionId);
      return r ? nomAffiche(r.nomFr, r.nomAr) : "—";
    }
    const p = provinces.find((x) => x.id === u.provinceId);
    return p ? nomAffiche(p.nomFr, p.nomAr) : "—";
  };

  return (
    <>
      <div className="page-head">
        <div><h1>{t("users.titre")}</h1><div className="sub">{t("users.compte", { n: fmt(liste.length) })}</div></div>
        <button className="btn primary" type="button" onClick={() => { setMessage(null); setErreurForm(null); setOuvert(true); }}>{t("users.nouveauBouton")}</button>
      </div>
      {message && <div className={`banner ${message.ton}`} role="status">{message.texte}</div>}

      <>
        <section className="card">
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>{t("users.colUtilisateur")}</th><th>{t("users.colRole")}</th><th>{t("users.colPerimetre")}</th>
                  <th>{t("users.colEtat")}</th><th><span className="sr-only">{t("users.colActions")}</span></th>
                </tr>
              </thead>
              <tbody>
                {liste.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <strong>{u.nom}</strong>
                      <div className="muted ltr" style={{ fontSize: 12 }}>{u.login}{u.matricule ? ` · ${u.matricule}` : ""}</div>
                    </td>
                    <td><span className="chip info">{u.role}</span></td>
                    <td>{perimetre(u)}</td>
                    <td>
                      <span className={`chip ${u.actif ? "ok" : "grey"}`}>{t(u.actif ? "users.actif" : "users.desactive")}</span>
                      {u.motDePasseTemporaire && <div className="error-text" style={{ color: "var(--warn)" }}>{t("users.mdpTemporaire")}</div>}
                    </td>
                    <td style={{ textAlign: "end" }}>
                      <button className="btn link" type="button" onClick={() => reinitialiser(u)}>{t("users.reinitialiser")}</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        {ouvert && (
        <Modal titre={t("users.nouveau")} onClose={() => setOuvert(false)}>
        <form onSubmit={creer} style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <div className="field"><label htmlFor="nom">{t("users.nom")}</label><input id="nom" className="input" value={nom} onChange={(e) => setNom(e.target.value)} required /></div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div className="field"><label htmlFor="login">{t("users.login")}</label><input id="login" className="input" dir="ltr" value={login} onChange={(e) => setLogin(e.target.value)} required pattern="[A-Za-z0-9._-]{3,60}" /></div>
            <div className="field"><label htmlFor="mat">{t("users.matricule")}</label><input id="mat" className="input" dir="ltr" value={matricule} onChange={(e) => setMatricule(e.target.value)} /></div>
          </div>
          <div className="field"><label htmlFor="email">{t("users.email")}</label><input id="email" type="email" className="input" dir="ltr" value={email} onChange={(e) => setEmail(e.target.value)} /></div>
          <div className="field">
            <label htmlFor="role">{t("users.role")}</label>
            <select id="role" className="select" value={role} onChange={(e) => setRole(e.target.value as Role)}>
              <option value="PROVINCIAL">{t("users.roleProvincial")}</option>
              <option value="REGIONAL">{t("users.roleRegional")}</option>
              <option value="ADMIN">{t("users.roleAdmin")}</option>
            </select>
          </div>
          {role === "PROVINCIAL" && (
            <div className="field">
              <label htmlFor="province">{t("users.province")}</label>
              <select id="province" className="select" value={provinceId} onChange={(e) => setProvinceId(e.target.value)} required>
                <option value="">{t("users.choisirProvince")}</option>
                {provinces.map((p) => <option key={p.id} value={p.id}>{nomAffiche(p.nomFr, p.nomAr)}</option>)}
              </select>
            </div>
          )}
          {role === "REGIONAL" && (
            <div className="field">
              <label htmlFor="region">{t("users.region")}</label>
              <select id="region" className="select" value={regionId} onChange={(e) => setRegionId(e.target.value)} required>
                <option value="">{t("users.choisirRegion")}</option>
                {regions.map((r) => <option key={r.id} value={r.id}>{nomAffiche(r.nomFr, r.nomAr)}</option>)}
              </select>
            </div>
          )}
          {erreurForm && <div className="banner bad" role="alert">{erreurForm}</div>}
          <div className="actions">
            <button className="btn primary" type="submit">{t("users.creer")}</button>
            <button className="btn" type="button" onClick={() => setOuvert(false)}>{t("action.annuler")}</button>
          </div>
        </form>
        </Modal>
        )}
      </>
    </>
  );
}
