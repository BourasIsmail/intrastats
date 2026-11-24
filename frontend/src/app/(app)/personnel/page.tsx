"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api, post, put } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useTerritoire } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import { Select } from "@/components/ui";
import type { CentreSummary, Page, PersonnelDto } from "@/lib/types";

const TAILLE = 20;

interface Formulaire {
  matricule: string; nom: string; prenom: string; cin: string; dateNaissance: string; lieuNaissance: string;
  sexe: string; dateRecrutement: string; fonction: string; autreFonction: string; provinceId: string; centreId: string;
  adresse: string; telephone: string; situationFamiliale: string; nbEnfants: string; niveauScolaire: string; actif: boolean;
}

const vide = (provinceId = ""): Formulaire => ({
  matricule: "", nom: "", prenom: "", cin: "", dateNaissance: "", lieuNaissance: "", sexe: "", dateRecrutement: "",
  fonction: "", autreFonction: "", provinceId, centreId: "", adresse: "", telephone: "", situationFamiliale: "",
  nbEnfants: "0", niveauScolaire: "", actif: true,
});

const depuis = (p: PersonnelDto): Formulaire => ({
  matricule: p.matricule ?? "", nom: p.nom, prenom: p.prenom, cin: p.cin ?? "", dateNaissance: p.dateNaissance ?? "",
  lieuNaissance: p.lieuNaissance ?? "", sexe: p.sexe ?? "", dateRecrutement: p.dateRecrutement ?? "",
  fonction: p.fonction ?? "", autreFonction: p.autreFonction ?? "", provinceId: String(p.provinceId),
  centreId: p.centreId ? String(p.centreId) : "", adresse: p.adresse ?? "", telephone: p.telephone ?? "",
  situationFamiliale: p.situationFamiliale ?? "", nbEnfants: String(p.nbEnfants), niveauScolaire: p.niveauScolaire ?? "",
  actif: p.actif,
});

export default function PersonnelPage() {
  const { user, can } = useAuth();
  const { provinces } = useTerritoire();
  const { t, nom, fmt, erreur: messageErreur } = useI18n();
  const peutGerer = can("PERSONNEL_GERER");

  const [q, setQ] = useState("");
  const [recherche, setRecherche] = useState("");
  const [provinceId, setProvinceId] = useState("");
  const [centreId, setCentreId] = useState("");
  const [actifSeulement, setActifSeulement] = useState(false);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<PersonnelDto> | null>(null);
  const [centres, setCentres] = useState<CentreSummary[]>([]);
  const [message, setMessage] = useState<{ ton: "ok" | "bad"; texte: string } | null>(null);
  const [edition, setEdition] = useState<{ id: number | null; form: Formulaire } | null>(null);
  const [envoi, setEnvoi] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => { setRecherche(q); setPage(0); }, 300);
    return () => clearTimeout(timer);
  }, [q]);

  // Liste des centres du périmètre (filtre et rattachement).
  useEffect(() => {
    api<Page<CentreSummary>>("/api/centres?size=100").then((p) => setCentres(p.content)).catch(() => setCentres([]));
  }, []);

  const charger = useCallback(() => {
    const params = new URLSearchParams({ page: String(page), size: String(TAILLE), actifSeulement: String(actifSeulement) });
    if (recherche) params.set("q", recherche);
    if (provinceId) params.set("provinceId", provinceId);
    if (centreId) params.set("centreId", centreId);
    return api<Page<PersonnelDto>>(`/api/personnel?${params}`)
      .then((d) => setData(d))
      .catch((e) => setMessage({ ton: "bad", texte: messageErreur(e) }));
  }, [recherche, provinceId, centreId, actifSeulement, page, messageErreur]);

  useEffect(() => { void charger(); }, [charger]);

  const total = data?.total ?? 0;
  const debut = total === 0 ? 0 : page * TAILLE + 1;
  const fin = Math.min((page + 1) * TAILLE, total);
  const centresFiltres = centres.filter((c) => !provinceId || String(c.provinceId) === provinceId);

  function nouveau() {
    setMessage(null);
    setEdition({ id: null, form: vide(user?.role === "PROVINCIAL" && user.provinceId ? String(user.provinceId) : "") });
  }

  async function enregistrer(e: FormEvent) {
    e.preventDefault();
    if (!edition) return;
    const f = edition.form;
    if (!f.nom.trim() || !f.prenom.trim()) return setMessage({ ton: "bad", texte: t("pers.erreurObligatoires") });
    if (!f.centreId && !f.provinceId) return setMessage({ ton: "bad", texte: t("pers.erreurProvince") });
    const corps = {
      matricule: f.matricule, nom: f.nom, prenom: f.prenom, cin: f.cin, dateNaissance: f.dateNaissance || null,
      lieuNaissance: f.lieuNaissance, sexe: f.sexe || null, dateRecrutement: f.dateRecrutement || null,
      fonction: f.fonction, autreFonction: f.autreFonction, provinceId: f.provinceId ? Number(f.provinceId) : null,
      centreId: f.centreId ? Number(f.centreId) : null, adresse: f.adresse, telephone: f.telephone,
      situationFamiliale: f.situationFamiliale, nbEnfants: Number(f.nbEnfants) || 0, niveauScolaire: f.niveauScolaire,
      actif: f.actif,
    };
    setEnvoi(true);
    setMessage(null);
    try {
      if (edition.id) await put<PersonnelDto>(`/api/personnel/${edition.id}`, corps);
      else await post<PersonnelDto>("/api/personnel", corps);
      await charger();
      setEdition(null);
      setMessage({ ton: "ok", texte: t("pers.enregistre") });
    } catch (err) {
      setMessage({ ton: "bad", texte: messageErreur(err) });
    } finally {
      setEnvoi(false);
    }
  }

  const maj = (patch: Partial<Formulaire>) => edition && setEdition({ ...edition, form: { ...edition.form, ...patch } });
  const champ = (id: string, label: string, valeur: string, cle: keyof Formulaire, extra: { type?: string; dir?: string; maxLength?: number } = {}) => (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} className="input" value={valeur} onChange={(e) => maj({ [cle]: e.target.value } as Partial<Formulaire>)} {...extra} />
    </div>
  );

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{t("pers.titre")}</h1>
          <div className="sub">{t("pers.compte", { n: fmt(total) })}</div>
        </div>
        {peutGerer && !edition && <button className="btn primary" type="button" onClick={nouveau}>{t("pers.ajouter")}</button>}
      </div>
      {message && <div className={`banner ${message.ton}`} role={message.ton === "bad" ? "alert" : "status"}>{message.texte}</div>}

      {edition && (
        <form className="card" onSubmit={enregistrer} noValidate>
          <h2>{edition.id ? t("pers.titreModif") : t("pers.titreNouveau")}</h2>
          <div className="filters-grid">
            {champ("p-nom", t("pers.nom"), edition.form.nom, "nom", { maxLength: 60 })}
            {champ("p-prenom", t("pers.prenom"), edition.form.prenom, "prenom", { maxLength: 60 })}
            {champ("p-mat", t("pers.matricule"), edition.form.matricule, "matricule", { dir: "ltr", maxLength: 20 })}
            {champ("p-cin", t("pers.cin"), edition.form.cin, "cin", { dir: "ltr", maxLength: 20 })}
            {champ("p-dn", t("pers.dateNaissance"), edition.form.dateNaissance, "dateNaissance", { type: "date" })}
            {champ("p-ln", t("pers.lieuNaissance"), edition.form.lieuNaissance, "lieuNaissance", { maxLength: 100 })}
            <Select id="p-sexe" label={t("pers.sexe")} value={edition.form.sexe} vide=""
                    onChange={(v) => maj({ sexe: v })}
                    options={[{ value: "M", label: t("pers.sexeM") }, { value: "F", label: t("pers.sexeF") }]} />
            {champ("p-dr", t("pers.dateRecrutement"), edition.form.dateRecrutement, "dateRecrutement", { type: "date" })}
            {champ("p-fn", t("pers.fonction"), edition.form.fonction, "fonction", { maxLength: 60 })}
            {champ("p-af", t("pers.autreFonction"), edition.form.autreFonction, "autreFonction", { maxLength: 60 })}
            {user?.role === "ADMIN" && !edition.form.centreId && (
              <Select id="p-prov" label={t("pers.province")} value={edition.form.provinceId} vide={t("act.choisir")}
                      onChange={(v) => maj({ provinceId: v })}
                      options={provinces.map((p) => ({ value: p.id, label: nom(p.nomFr, p.nomAr) }))} />
            )}
            <Select id="p-centre" label={t("pers.rattachement")} value={edition.form.centreId} vide={t("pers.aucunCentre")}
                    onChange={(v) => maj({ centreId: v })}
                    options={centres.map((c) => ({ value: c.id, label: nom(c.nomFr, c.nomAr) }))} />
            {champ("p-tel", t("pers.telephone"), edition.form.telephone, "telephone", { dir: "ltr", maxLength: 50 })}
            {champ("p-sf", t("pers.situationFamiliale"), edition.form.situationFamiliale, "situationFamiliale", { maxLength: 20 })}
            {champ("p-ne", t("pers.nbEnfants"), edition.form.nbEnfants, "nbEnfants", { type: "number", dir: "ltr" })}
            {champ("p-ns", t("pers.niveauScolaire"), edition.form.niveauScolaire, "niveauScolaire", { maxLength: 40 })}
          </div>
          <div className="field" style={{ marginTop: 12 }}>
            <label htmlFor="p-adr">{t("pers.adresse")}</label>
            <input id="p-adr" className="input" value={edition.form.adresse} maxLength={400} onChange={(e) => maj({ adresse: e.target.value })} />
          </div>
          <label className="row" style={{ gap: 8, marginTop: 12 }}>
            <input type="checkbox" checked={edition.form.actif} onChange={(e) => maj({ actif: e.target.checked })} />
            {t("pers.enActivite")}
          </label>
          <div className="actions" style={{ marginTop: 12 }}>
            <button className="btn primary" type="submit" disabled={envoi}>{t("action.enregistrer")}</button>
            <button className="btn" type="button" onClick={() => setEdition(null)}>{t("action.annuler")}</button>
          </div>
        </form>
      )}

      <section className="card">
        <div className="filters-grid">
          <div className="field">
            <label htmlFor="pq">{t("pers.recherche")}</label>
            <input id="pq" type="search" className="input" placeholder={t("pers.recherchePlaceholder")} value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          {user?.role === "ADMIN" && (
            <Select id="pf-prov" label={t("pers.province")} value={provinceId} vide={t("pers.tous")}
                    onChange={(v) => { setProvinceId(v); setCentreId(""); setPage(0); }}
                    options={provinces.map((p) => ({ value: p.id, label: nom(p.nomFr, p.nomAr) }))} />
          )}
          <Select id="pf-centre" label={t("pers.centre")} value={centreId} vide={t("pers.tous")}
                  onChange={(v) => { setCentreId(v); setPage(0); }}
                  options={centresFiltres.map((c) => ({ value: c.id, label: nom(c.nomFr, c.nomAr) }))} />
          <label className="row" style={{ gap: 8, alignSelf: "end" }}>
            <input type="checkbox" checked={actifSeulement} onChange={(e) => { setActifSeulement(e.target.checked); setPage(0); }} />
            {t("pers.actifsSeulement")}
          </label>
        </div>
      </section>

      <section className="card">
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t("pers.colNom")}</th><th>{t("pers.colMatricule")}</th><th>{t("pers.colFonction")}</th>
                <th>{t("pers.colCentre")}</th><th>{t("pers.colTelephone")}</th><th>{t("pers.colEtat")}</th>
                <th><span className="sr-only">{t("users.colActions")}</span></th>
              </tr>
            </thead>
            <tbody>
              {data?.content.map((p) => (
                <tr key={p.id}>
                  <td><strong>{p.nom} {p.prenom}</strong></td>
                  <td className="muted" dir="ltr" style={{ textAlign: "start" }}>{p.matricule}</td>
                  <td>{p.fonction}</td>
                  <td>{p.centreNom}</td>
                  <td className="muted" dir="ltr" style={{ textAlign: "start" }}>{p.telephone}</td>
                  <td><span className={`chip ${p.actif ? "ok" : "grey"}`}>{t(p.actif ? "pers.actif" : "pers.inactif")}</span></td>
                  <td style={{ textAlign: "end" }}>
                    {peutGerer && <button className="btn link" type="button"
                      onClick={() => { setMessage(null); setEdition({ id: p.id, form: depuis(p) }); window.scrollTo({ top: 0 }); }}>{t("action.modifier")}</button>}
                  </td>
                </tr>
              ))}
              {data && data.content.length === 0 && <tr><td colSpan={7} className="muted">{t("pers.aucun")}</td></tr>}
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
