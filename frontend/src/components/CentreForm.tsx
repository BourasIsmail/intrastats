"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { api, post, put } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useTerritoire } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import type { CentreDetail, Commune } from "@/lib/types";

interface Form {
  code: string;
  nomFr: string;
  nomAr: string;
  categorie: string;
  type: string;
  regionId: string;
  provinceId: string;
  communeId: string;
  milieu: string;
  adresse: string;
  latitude: string;
  longitude: string;
  positionExacte: boolean;
  dateConstruction: string;
  telephone: string;
  responsable: string;
  propriete: string;
  situationFonciere: string;
  proprietaire: string;
  loyerMensuel: string;
  chargesLocatives: string;
  numeroContrat: string;
  dateContrat: string;
  avenant: string;
  eau: boolean;
  electricite: boolean;
  assainissement: boolean;
  telephoneLigne: boolean;
  internet: boolean;
  nbOrdinateurs: string;
  nbImprimantes: string;
}

const VIDE: Form = {
  code: "", nomFr: "", nomAr: "", categorie: "Centre", type: "Centre simple", regionId: "", provinceId: "", communeId: "",
  milieu: "urbain", adresse: "", latitude: "", longitude: "", positionExacte: false, dateConstruction: "", telephone: "",
  responsable: "", propriete: "", situationFonciere: "", proprietaire: "", loyerMensuel: "", chargesLocatives: "",
  numeroContrat: "", dateContrat: "", avenant: "", eau: false, electricite: false, assainissement: false,
  telephoneLigne: false, internet: false, nbOrdinateurs: "0", nbImprimantes: "0",
};

const v0 = (v: string | null | undefined) => v ?? "";
const nombre = (v: string): number | null => (v.trim() === "" ? null : Number(v));

const depuis = (c: CentreDetail): Form => ({
  ...VIDE,
  code: c.code, nomFr: c.nomFr, nomAr: v0(c.nomAr), categorie: v0(c.categorie), type: v0(c.type),
    provinceId: String(c.provinceId), communeId: c.communeId ? String(c.communeId) : "", milieu: v0(c.milieu),
    adresse: v0(c.adresse), latitude: c.latitude === null ? "" : String(c.latitude),
    longitude: c.longitude === null ? "" : String(c.longitude), positionExacte: c.positionExacte,
    dateConstruction: v0(c.dateConstruction), telephone: v0(c.telephone), responsable: v0(c.responsable),
    propriete: v0(c.foncier?.propriete), situationFonciere: v0(c.foncier?.situationFonciere),
    proprietaire: v0(c.foncier?.proprietaire),
    loyerMensuel: c.foncier?.loyerMensuel == null ? "" : String(c.foncier.loyerMensuel),
    chargesLocatives: c.foncier?.chargesLocatives == null ? "" : String(c.foncier.chargesLocatives),
    numeroContrat: v0(c.foncier?.numeroContrat), dateContrat: v0(c.foncier?.dateContrat), avenant: v0(c.foncier?.avenant),
    eau: !!c.equipement?.eau, electricite: !!c.equipement?.electricite, assainissement: !!c.equipement?.assainissement,
    telephoneLigne: !!c.equipement?.telephone, internet: !!c.equipement?.internet,
    nbOrdinateurs: String(c.equipement?.nbOrdinateurs ?? 0), nbImprimantes: String(c.equipement?.nbImprimantes ?? 0),
});

/** Formulaire de création (sans centre) ou de modification d'un centre, destiné à une fenêtre modale. */
export function CentreForm({ centre, onSaved, onCancel }: {
  centre?: CentreDetail;
  onSaved: (c: CentreDetail) => void;
  onCancel: () => void;
}) {
  const id = centre?.id;
  const { user } = useAuth();
  const { regions, provinces } = useTerritoire();
  const { t, nom, erreur: messageErreur } = useI18n();
  const [form, setForm] = useState<Form>(() => (centre ? depuis(centre) : VIDE));
  const [communes, setCommunes] = useState<Commune[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const set = <K extends keyof Form>(cle: K, valeur: Form[K]) => setForm((f) => ({ ...f, [cle]: valeur }));

  // Région déduite de la province ; le provincial n'a qu'une province
  useEffect(() => {
    if (provinces.length === 0) return;
    setForm((f) => {
      let provinceId = f.provinceId;
      if (!provinceId && user?.role === "PROVINCIAL" && user.provinceId) provinceId = String(user.provinceId);
      const province = provinces.find((p) => String(p.id) === provinceId);
      const regionId = province ? String(province.regionId) : f.regionId;
      return provinceId === f.provinceId && regionId === f.regionId ? f : { ...f, provinceId, regionId };
    });
  }, [provinces, user]);

  useEffect(() => {
    if (!form.provinceId) {
      setCommunes([]);
      return;
    }
    api<Commune[]>(`/api/communes?provinceId=${form.provinceId}`).then(setCommunes).catch(() => setCommunes([]));
  }, [form.provinceId]);

  const provincesDeLaRegion = useMemo(
    () => provinces.filter((p) => !form.regionId || String(p.regionId) === form.regionId),
    [provinces, form.regionId],
  );

  function charge(): Record<string, unknown> | string {
    if (!form.code.trim() || !form.nomFr.trim()) return t("centre.erreurObligatoires");
    if (!form.provinceId) return t("centre.erreurProvince");
    const lat = nombre(form.latitude);
    const lng = nombre(form.longitude);
    if (lat !== null && (Number.isNaN(lat) || Math.abs(lat) > 90)) return t("centre.erreurLatitude");
    if (lng !== null && (Number.isNaN(lng) || Math.abs(lng) > 180)) return t("centre.erreurLongitude");
    const pc = Number(form.nbOrdinateurs);
    const imp = Number(form.nbImprimantes);
    if (!Number.isInteger(pc) || pc < 0 || !Number.isInteger(imp) || imp < 0) return t("centre.erreurEquipements");
    const loyer = nombre(form.loyerMensuel);
    const charges = nombre(form.chargesLocatives);
    if ([loyer, charges].some((n) => n !== null && (Number.isNaN(n) || n < 0))) return t("centre.erreurMontants");
    return {
      code: form.code.trim(), nomFr: form.nomFr.trim(), nomAr: form.nomAr.trim() || null,
      categorie: form.categorie || null, type: form.type || null, provinceId: Number(form.provinceId),
      communeId: form.communeId ? Number(form.communeId) : null, milieu: form.milieu || null,
      adresse: form.adresse || null, latitude: lat, longitude: lng, positionExacte: form.positionExacte,
      dateConstruction: form.dateConstruction || null, telephone: form.telephone || null, responsable: form.responsable || null,
      foncier: {
        propriete: form.propriete || null, situationFonciere: form.situationFonciere || null,
        proprietaire: form.proprietaire || null, loyerMensuel: loyer, chargesLocatives: charges,
        numeroContrat: form.numeroContrat || null, dateContrat: form.dateContrat || null, avenant: form.avenant || null,
      },
      equipement: {
        eau: form.eau, electricite: form.electricite, assainissement: form.assainissement,
        telephone: form.telephoneLigne, internet: form.internet, nbOrdinateurs: pc, nbImprimantes: imp,
      },
    };
  }

  async function enregistrer(e: FormEvent) {
    e.preventDefault();
    setErreur(null);
    const corps = charge();
    if (typeof corps === "string") {
      setErreur(corps);
      return;
    }
    setEnvoi(true);
    try {
      const c = id
        ? await put<CentreDetail>(`/api/centres/${id}`, corps)
        : await post<CentreDetail>("/api/centres", corps);
      onSaved(c);
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  }

  const champ = (idc: string, label: string, valeur: string, onChange: (v: string) => void, extra: Partial<{ type: string; dir: string; required: boolean; lang: string; step: string }> = {}) => (
    <div className="field">
      <label htmlFor={`cf-${idc}`}>{label}</label>
      <input id={`cf-${idc}`} className="input" value={valeur} onChange={(e) => onChange(e.target.value)} {...extra} />
    </div>
  );

  return (
    <form onSubmit={enregistrer} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <section className="card form-section">
        <h2>{t("centre.identite")}</h2>
        <div className="form-grid">
          {champ("code", t("centre.code"), form.code, (v) => set("code", v), { required: true })}
          {champ("nomfr", t("centre.nomFr"), form.nomFr, (v) => set("nomFr", v), { required: true })}
          {champ("nomar", t("centre.nomAr"), form.nomAr, (v) => set("nomAr", v), { dir: "rtl", lang: "ar" })}
          {champ("categorie", t("centre.categorie"), form.categorie, (v) => set("categorie", v))}
          {champ("type", t("centre.type"), form.type, (v) => set("type", v))}
          {champ("datec", t("centre.dateConstruction"), form.dateConstruction, (v) => set("dateConstruction", v), { type: "date" })}
          {champ("tel", t("centre.telephone"), form.telephone, (v) => set("telephone", v), { type: "tel" })}
          {champ("resp", t("centre.responsable"), form.responsable, (v) => set("responsable", v))}
        </div>
      </section>

      <section className="card form-section">
        <h2>{t("centre.localisation")}</h2>
        <div className="form-grid">
          <div className="field">
            <label htmlFor="cf-region">{t("centre.region")}</label>
            <select id="cf-region" className="select" value={form.regionId} disabled={user?.role === "PROVINCIAL"}
                    onChange={(e) => setForm((f) => ({ ...f, regionId: e.target.value, provinceId: "", communeId: "" }))}>
              <option value="">{t("centre.choisir")}</option>
              {regions.map((r) => <option key={r.id} value={r.id}>{nom(r.nomFr, r.nomAr)}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="cf-province">{t("centre.province")}</label>
            <select id="cf-province" className="select" value={form.provinceId} required
                    disabled={user?.role === "PROVINCIAL" || (!!id && user?.role !== "ADMIN")}
                    onChange={(e) => setForm((f) => ({ ...f, provinceId: e.target.value, communeId: "" }))}>
              <option value="">{t("centre.choisir")}</option>
              {provincesDeLaRegion.map((p) => <option key={p.id} value={p.id}>{nom(p.nomFr, p.nomAr)}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="cf-commune">{t("centre.commune")}</label>
            <select id="cf-commune" className="select" value={form.communeId} onChange={(e) => set("communeId", e.target.value)}>
              <option value="">{t("centre.choisir")}</option>
              {communes.map((c) => <option key={c.id} value={c.id}>{nom(c.nomFr, c.nomAr)}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="cf-milieu">{t("centre.milieu")}</label>
            <select id="cf-milieu" className="select" value={form.milieu} onChange={(e) => set("milieu", e.target.value)}>
              <option value="urbain">{t("centre.urbain")}</option>
              <option value="rural">{t("centre.rural")}</option>
            </select>
          </div>
          <div className="field wide">
            <label htmlFor="cf-adresse">{t("centre.adresse")}</label>
            <input id="cf-adresse" className="input" value={form.adresse} onChange={(e) => set("adresse", e.target.value)} />
          </div>
          {champ("lat", t("centre.latitude"), form.latitude, (v) => set("latitude", v), { type: "number", step: "any" })}
          {champ("lng", t("centre.longitude"), form.longitude, (v) => set("longitude", v), { type: "number", step: "any" })}
          <label className="check"><input type="checkbox" checked={form.positionExacte} onChange={(e) => set("positionExacte", e.target.checked)} />{t("centre.positionExacte")}</label>
        </div>
      </section>

      <section className="card form-section">
        <h2>{t("centre.foncier")}</h2>
        <div className="form-grid">
          {champ("propriete", t("centre.propriete"), form.propriete, (v) => set("propriete", v))}
          {champ("sf", t("centre.situationFonciere"), form.situationFonciere, (v) => set("situationFonciere", v))}
          {champ("proprio", t("centre.proprietaire"), form.proprietaire, (v) => set("proprietaire", v))}
          {champ("loyer", t("centre.loyer"), form.loyerMensuel, (v) => set("loyerMensuel", v), { type: "number", step: "0.01" })}
          {champ("charges", t("centre.charges"), form.chargesLocatives, (v) => set("chargesLocatives", v), { type: "number", step: "0.01" })}
          {champ("contrat", t("centre.numeroContrat"), form.numeroContrat, (v) => set("numeroContrat", v))}
          {champ("dcontrat", t("centre.dateContrat"), form.dateContrat, (v) => set("dateContrat", v), { type: "date" })}
          {champ("avenant", t("centre.avenant"), form.avenant, (v) => set("avenant", v))}
        </div>
      </section>

      <section className="card form-section">
        <h2>{t("centre.equipements")}</h2>
        <div className="form-grid">
          <label className="check"><input type="checkbox" checked={form.eau} onChange={(e) => set("eau", e.target.checked)} />{t("centre.eau")}</label>
          <label className="check"><input type="checkbox" checked={form.electricite} onChange={(e) => set("electricite", e.target.checked)} />{t("centre.electricite")}</label>
          <label className="check"><input type="checkbox" checked={form.assainissement} onChange={(e) => set("assainissement", e.target.checked)} />{t("centre.assainissement")}</label>
          <label className="check"><input type="checkbox" checked={form.telephoneLigne} onChange={(e) => set("telephoneLigne", e.target.checked)} />{t("centre.telephoneLigne")}</label>
          <label className="check"><input type="checkbox" checked={form.internet} onChange={(e) => set("internet", e.target.checked)} />{t("centre.internet")}</label>
          {champ("pc", t("centre.ordinateurs"), form.nbOrdinateurs, (v) => set("nbOrdinateurs", v), { type: "number" })}
          {champ("imp", t("centre.imprimantes"), form.nbImprimantes, (v) => set("nbImprimantes", v), { type: "number" })}
        </div>
      </section>
      {erreur && <div className="banner bad" role="alert">{erreur}</div>}
      <div className="actions">
        <button className="btn primary" type="submit" disabled={envoi}>{t("action.enregistrer")}</button>
        <button className="btn" type="button" onClick={onCancel}>{t("action.annuler")}</button>
      </div>
    </form>
  );
}
