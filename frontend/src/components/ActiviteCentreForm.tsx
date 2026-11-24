"use client";

import { useEffect, useState, type FormEvent } from "react";
import { api, post, put } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { GESTIONS, type ActiviteDto, type CatalogueItem, type CentreDetail, type Gestion, type Page, type PersonnelDto } from "@/lib/types";

interface Props {
  centreId: number;
  catalogue: CatalogueItem[];
  /** Activités déjà ouvertes dans le centre (non proposées à l'ajout). */
  deja: Set<number>;
  /** Activité à modifier ; absent = ajout. */
  edition?: ActiviteDto;
  onSaved: (c: CentreDetail) => void;
  onCancel?: () => void;
}

const entierOuNull = (v: string): number | null | undefined => {
  if (v.trim() === "") return null;
  return /^\d{1,6}$/.test(v.trim()) && Number(v.trim()) > 0 ? Number(v.trim()) : undefined; // undefined = invalide (entier > 0 exigé)
};

/** Formulaire « Données relatives à l'activité dans le centre » (ajout et modification). */
export function ActiviteCentreForm({ centreId, catalogue, deja, edition, onSaved, onCancel }: Props) {
  const { t, nom, erreur: messageErreur } = useI18n();
  const { can } = useAuth();
  const [agents, setAgents] = useState<PersonnelDto[]>([]);
  const [activiteId, setActiviteId] = useState(edition ? String(edition.activiteId) : "");
  const [dateOuverture, setDateOuverture] = useState(edition?.dateOuverture ?? new Date().toISOString().slice(0, 10));
  const [responsableId, setResponsableId] = useState(edition?.responsableId ? String(edition.responsableId) : "");
  const [capacite, setCapacite] = useState(edition?.capacite != null ? String(edition.capacite) : "");
  const [superficie, setSuperficie] = useState(edition?.superficie != null ? String(edition.superficie) : "");
  const [gestion, setGestion] = useState<Gestion | "">(edition?.gestion ?? "");
  const [gestionAutre, setGestionAutre] = useState(edition?.gestionAutre ?? "");
  const [partenaire, setPartenaire] = useState(edition?.partenaire ?? "");
  const [dateConvention, setDateConvention] = useState(edition?.dateConvention ?? "");
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  useEffect(() => {
    if (!can("PERSONNEL_LIRE")) return;
    api<Page<PersonnelDto>>(`/api/personnel?centreId=${centreId}&actifSeulement=true&size=200`)
      .then((p) => setAgents(p.content))
      .catch(() => setAgents([]));
  }, [centreId, can]);

  // Le responsable actuel reste affiché même s'il est devenu inactif.
  const options = agents.some((a) => a.id === edition?.responsableId) || !edition?.responsableId
    ? agents
    : [...agents, { id: edition.responsableId, nom: edition.responsable ?? "", prenom: "" } as PersonnelDto];

  async function soumettre(e: FormEvent) {
    e.preventDefault();
    setErreur(null);
    if (!edition && !activiteId) return setErreur(t("act.erreurActivite"));
    const cap = entierOuNull(capacite);
    const sup = entierOuNull(superficie);
    if (cap === undefined || sup === undefined) return setErreur(t("act.erreurNombre"));
    if (gestion === "AUTRE" && !gestionAutre.trim()) return setErreur(t("act.erreurAutre"));
    if (dateConvention && !partenaire.trim()) return setErreur(t("act.erreurConvention"));
    const corps = {
      activiteId: Number(activiteId) || null,
      dateOuverture: dateOuverture || null,
      responsableId: responsableId ? Number(responsableId) : null,
      capacite: cap,
      superficie: sup,
      gestion: gestion || null,
      gestionAutre: gestion === "AUTRE" ? gestionAutre.trim() : null,
      partenaire: partenaire.trim() || null,
      dateConvention: partenaire.trim() ? dateConvention || null : null,
    };
    setEnvoi(true);
    try {
      const c = edition
        ? await put<CentreDetail>(`/api/centres/${centreId}/activites/${edition.id}`, corps)
        : await post<CentreDetail>(`/api/centres/${centreId}/activites`, corps);
      onSaved(c);
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  }

  const ligne = (label: string, htmlFor: string, champ: React.ReactNode) => (
    <div className="field"><label htmlFor={htmlFor}>{label}</label>{champ}</div>
  );

  return (
    <form onSubmit={soumettre} className="card" style={{ marginTop: 12 }} noValidate>
      <h3 style={{ marginTop: 0 }}>
        {edition ? t("act.modification", { nom: nom(edition.nom, edition.nomAr) }) : t("act.titre")}
      </h3>
      <div className="filters-grid">
        {ligne(t("act.activite"), "fa-act",
          <select id="fa-act" className="select" value={activiteId} disabled={!!edition} onChange={(e) => setActiviteId(e.target.value)}>
            <option value="">{t("act.choisir")}</option>
            {catalogue.filter((a) => edition ? a.id === edition.activiteId : !deja.has(a.id))
              .map((a) => <option key={a.id} value={a.id}>{nom(a.nom, a.nomAr)}</option>)}
            {edition && !catalogue.some((a) => a.id === edition.activiteId) &&
              <option value={edition.activiteId}>{nom(edition.nom, edition.nomAr)}</option>}
          </select>)}
        {ligne(t("act.dateOuverture"), "fa-date",
          <input id="fa-date" className="input" type="date" value={dateOuverture} onChange={(e) => setDateOuverture(e.target.value)} />)}
        {ligne(t("act.responsable"), "fa-resp",
          <select id="fa-resp" className="select" value={responsableId} onChange={(e) => setResponsableId(e.target.value)}>
            <option value="">{t("act.aucunResponsable")}</option>
            {options.map((a) => <option key={a.id} value={a.id}>{`${a.nom} ${a.prenom}`.trim()}</option>)}
          </select>)}
        {ligne(t("act.capacite"), "fa-cap",
          <input id="fa-cap" className="input" type="number" min={1} step={1} max={99999} value={capacite} onChange={(e) => setCapacite(e.target.value)} dir="ltr" />)}
        {ligne(t("act.superficie"), "fa-sup",
          <input id="fa-sup" className="input" type="number" min={1} step={1} max={999999} value={superficie} onChange={(e) => setSuperficie(e.target.value)} dir="ltr" />)}
        {ligne(t("act.gestion"), "fa-gest",
          <select id="fa-gest" className="select" value={gestion} onChange={(e) => setGestion(e.target.value as Gestion | "")}>
            <option value="">{t("act.choisir")}</option>
            {GESTIONS.map((g) => <option key={g} value={g}>{t(`act.gestion.${g}`)}</option>)}
          </select>)}
        {gestion === "AUTRE" && ligne(t("act.gestionAutre"), "fa-autre",
          <input id="fa-autre" className="input" value={gestionAutre} onChange={(e) => setGestionAutre(e.target.value)} maxLength={200} />)}
        {ligne(t("act.partenaire"), "fa-part",
          <input id="fa-part" className="input" value={partenaire} onChange={(e) => setPartenaire(e.target.value)} maxLength={200} />)}
        {ligne(t("act.dateConvention"), "fa-conv",
          <input id="fa-conv" className="input" type="date" value={dateConvention} onChange={(e) => setDateConvention(e.target.value)} />)}
      </div>
      {can("PERSONNEL_LIRE") && agents.length === 0 && <p className="muted" style={{ marginBottom: 0 }}>{t("act.aucunAgent")}</p>}
      {erreur && <div className="banner bad" role="alert" style={{ marginTop: 12 }}>{erreur}</div>}
      <div className="actions" style={{ marginTop: 12 }}>
        <button className="btn primary" type="submit" disabled={envoi}>{edition ? t("act.enregistrer") : t("act.ajouter")}</button>
        {onCancel && <button className="btn" type="button" onClick={onCancel}>{t("action.annuler")}</button>}
      </div>
    </form>
  );
}
