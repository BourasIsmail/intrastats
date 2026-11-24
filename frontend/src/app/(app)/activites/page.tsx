"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api, post, put } from "@/lib/api";
import { useI18n } from "@/lib/i18n";
import { Select } from "@/components/ui";
import type { ActiviteCatalogue, CategorieActivite } from "@/lib/types";

interface Formulaire {
  nom: string; nomAr: string; abreviation: string; description: string; categorieId: string;
  typeActivite: string; personnesCibles: string; priorite: string; actif: boolean;
}

const vide = (): Formulaire => ({
  nom: "", nomAr: "", abreviation: "", description: "", categorieId: "", typeActivite: "PRINCIPALE",
  personnesCibles: "", priorite: "", actif: true,
});

export default function CataloguePage() {
  const { t, nom, fmt, erreur: messageErreur } = useI18n();
  const [liste, setListe] = useState<ActiviteCatalogue[] | null>(null);
  const [categories, setCategories] = useState<CategorieActivite[]>([]);
  const [message, setMessage] = useState<{ ton: "ok" | "bad"; texte: string } | null>(null);
  const [edition, setEdition] = useState<{ id: number | null; form: Formulaire } | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const charger = useCallback(
    () => api<ActiviteCatalogue[]>("/api/admin/activites").then(setListe)
      .catch((e) => setMessage({ ton: "bad", texte: messageErreur(e) })),
    [messageErreur]);

  useEffect(() => { void charger(); }, [charger]);
  useEffect(() => {
    api<CategorieActivite[]>("/api/admin/activites/categories").then(setCategories).catch(() => setCategories([]));
  }, []);

  async function enregistrer(e: FormEvent) {
    e.preventDefault();
    if (!edition) return;
    const f = edition.form;
    if (!f.nom.trim() || !f.categorieId) return setMessage({ ton: "bad", texte: t("cat.erreurNom") });
    const corps = {
      nom: f.nom, nomAr: f.nomAr, abreviation: f.abreviation, description: f.description,
      categorieId: Number(f.categorieId), typeActivite: f.typeActivite, personnesCibles: f.personnesCibles,
      priorite: f.priorite.trim() === "" ? null : Number(f.priorite), actif: f.actif,
    };
    setEnvoi(true);
    setMessage(null);
    try {
      if (edition.id) await put(`/api/admin/activites/${edition.id}`, corps);
      else await post("/api/admin/activites", corps);
      await charger();
      setEdition(null);
      setMessage({ ton: "ok", texte: t("cat.enregistree") });
    } catch (err) {
      setMessage({ ton: "bad", texte: messageErreur(err) });
    } finally {
      setEnvoi(false);
    }
  }

  const maj = (patch: Partial<Formulaire>) => edition && setEdition({ ...edition, form: { ...edition.form, ...patch } });
  const champ = (id: string, label: string, valeur: string, cle: keyof Formulaire, extra: { type?: string; dir?: string; maxLength?: number; lang?: string } = {}) => (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} className="input" value={valeur} onChange={(e) => maj({ [cle]: e.target.value } as Partial<Formulaire>)} {...extra} />
    </div>
  );

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{t("cat.titre")}</h1>
          <div className="sub">{t("cat.compte", { n: fmt(liste?.length ?? 0) })}</div>
        </div>
        {!edition && <button className="btn primary" type="button" onClick={() => { setMessage(null); setEdition({ id: null, form: vide() }); }}>{t("cat.ajouter")}</button>}
      </div>
      {message && <div className={`banner ${message.ton}`} role={message.ton === "bad" ? "alert" : "status"}>{message.texte}</div>}

      {edition && (
        <form className="card" onSubmit={enregistrer} noValidate>
          <h2>{edition.id ? t("cat.titreModif") : t("cat.titreNouveau")}</h2>
          <div className="filters-grid">
            {champ("c-nom", t("cat.nom"), edition.form.nom, "nom", { maxLength: 250 })}
            {champ("c-ar", t("cat.nomAr"), edition.form.nomAr, "nomAr", { maxLength: 250, dir: "rtl", lang: "ar" })}
            {champ("c-abr", t("cat.abreviation"), edition.form.abreviation, "abreviation", { maxLength: 20, dir: "ltr" })}
            <Select id="c-cat" label={t("cat.categorie")} value={edition.form.categorieId} vide={t("act.choisir")}
                    onChange={(v) => maj({ categorieId: v })}
                    options={categories.map((c) => ({ value: c.id, label: c.libelle }))} />
            <Select id="c-type" label={t("cat.type")} value={edition.form.typeActivite}
                    onChange={(v) => maj({ typeActivite: v })}
                    options={[{ value: "PRINCIPALE", label: t("cat.PRINCIPALE") }, { value: "SECONDAIRE", label: t("cat.SECONDAIRE") }]} />
            {champ("c-cib", t("cat.personnesCibles"), edition.form.personnesCibles, "personnesCibles", { maxLength: 60 })}
            {champ("c-pri", t("cat.priorite"), edition.form.priorite, "priorite", { type: "number", dir: "ltr" })}
            {champ("c-desc", t("cat.description"), edition.form.description, "description", { maxLength: 255 })}
          </div>
          <label className="row" style={{ gap: 8, marginTop: 12 }}>
            <input type="checkbox" checked={edition.form.actif} onChange={(e) => maj({ actif: e.target.checked })} />
            {t("cat.proposee")}
          </label>
          <div className="actions" style={{ marginTop: 12 }}>
            <button className="btn primary" type="submit" disabled={envoi}>{t("action.enregistrer")}</button>
            <button className="btn" type="button" onClick={() => setEdition(null)}>{t("action.annuler")}</button>
          </div>
        </form>
      )}

      <section className="card">
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t("cat.colNom")}</th><th>{t("cat.colCategorie")}</th><th>{t("cat.colType")}</th>
                <th>{t("cat.colCentres")}</th><th>{t("cat.colEtat")}</th>
                <th><span className="sr-only">{t("users.colActions")}</span></th>
              </tr>
            </thead>
            <tbody>
              {liste?.map((a) => (
                <tr key={a.id}>
                  <td><strong>{nom(a.nom, a.nomAr)}</strong>{a.abreviation && <span className="muted"> · {a.abreviation}</span>}</td>
                  <td className="muted">{a.categorie}</td>
                  <td>{a.typeActivite === "SECONDAIRE" ? t("cat.SECONDAIRE") : t("cat.PRINCIPALE")}</td>
                  <td className="num" style={{ textAlign: "start" }}>{fmt(a.nbCentres)}</td>
                  <td><span className={`chip ${a.actif ? "ok" : "grey"}`}>{t(a.actif ? "cat.active" : "cat.retiree")}</span></td>
                  <td style={{ textAlign: "end" }}>
                    <button className="btn link" type="button" onClick={() => {
                      setMessage(null);
                      setEdition({ id: a.id, form: {
                        nom: a.nom, nomAr: a.nomAr ?? "", abreviation: a.abreviation ?? "", description: a.description ?? "",
                        categorieId: String(a.categorieId), typeActivite: a.typeActivite, personnesCibles: a.personnesCibles ?? "",
                        priorite: a.priorite != null ? String(a.priorite) : "", actif: a.actif } });
                      window.scrollTo({ top: 0 });
                    }}>{t("action.modifier")}</button>
                  </td>
                </tr>
              ))}
              {liste && liste.length === 0 && <tr><td colSpan={6} className="muted">{t("cat.aucune")}</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
