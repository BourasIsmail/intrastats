"use client";

import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import { api, post } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useTerritoire } from "@/lib/hooks";
import { ActiviteCentreForm } from "@/components/ActiviteCentreForm";
import { CentreForm } from "@/components/CentreForm";
import { Modal } from "@/components/Modal";
import { useI18n } from "@/lib/i18n";
import type { CatalogueItem, CentreDetail, Commune } from "@/lib/types";

const Info = ({ label, children }: { label: string; children: ReactNode }) => (
  <div className="info">
    <dt>{label}</dt>
    <dd>{children === null || children === undefined || children === "" || children === false ? "—" : children}</dd>
  </div>
);

/** Fiche d'un centre : informations en lecture, modification dans une fenêtre modale, activités du centre. */
export function CentreEditor({ id }: { id: number }) {
  const { can } = useAuth();
  const { regions, provinces } = useTerritoire();
  const { t, dir, nom, fmt, erreur: messageErreur } = useI18n();
  const [centre, setCentre] = useState<CentreDetail | null>(null);
  const [communes, setCommunes] = useState<Commune[]>([]);
  const [catalogue, setCatalogue] = useState<CatalogueItem[]>([]);
  const [message, setMessage] = useState<{ ton: "ok" | "bad"; texte: string } | null>(null);
  const [modifier, setModifier] = useState(false);

  const peutModifier = can("CENTRE_GERER");

  useEffect(() => {
    api<CentreDetail>(`/api/centres/${id}`).then(setCentre)
      .catch((e) => setMessage({ ton: "bad", texte: messageErreur(e) }));
  }, [id, messageErreur]);

  useEffect(() => {
    if (centre) api<Commune[]>(`/api/communes?provinceId=${centre.provinceId}`).then(setCommunes).catch(() => setCommunes([]));
  }, [centre?.provinceId]);

  useEffect(() => {
    api<CatalogueItem[]>("/api/activites").then(setCatalogue).catch(() => undefined);
  }, []);

  const province = provinces.find((p) => p.id === centre?.provinceId);
  const region = regions.find((r) => r.id === province?.regionId);
  const commune = communes.find((c) => c.id === centre?.communeId);
  const oui = (b: boolean | undefined) => t(b ? "valeur.oui" : "valeur.non");

  // ---- activités du centre
  const [formActivite, setFormActivite] = useState<"ajout" | number | null>(null);
  const [cesser, setCesser] = useState<{ id: number; date: string; motif: string } | null>(null);
  const [erreurCess, setErreurCess] = useState<string | null>(null);

  function activiteEnregistree(c: CentreDetail) {
    setCentre(c);
    setFormActivite(null);
    setMessage({ ton: "ok", texte: t("act.enregistree") });
  }

  async function cesserActivite() {
    if (!cesser) return;
    setErreurCess(null);
    try {
      const c = await post<CentreDetail>(`/api/centres/${id}/activites/${cesser.id}/cesser`, {
        date: cesser.date || null, motif: cesser.motif || null,
      });
      setCentre(c);
      setCesser(null);
    } catch (err) {
      setErreurCess(messageErreur(err));
    }
  }

  const deja = new Set(centre?.activites.map((a) => a.activiteId));

  return (
    <>
      <div className="page-head">
        <div>
          <Link href="/centres" style={{ fontSize: 13, textDecoration: "none" }}>{dir === "rtl" ? "→" : "←"} {t("action.retourCentres")}</Link>
          <h1 style={{ marginTop: 6 }}>{centre ? nom(centre.nomFr, centre.nomAr) : t("centre.defaut")}</h1>
          {centre && <div className="sub">{centre.code}</div>}
        </div>
        {peutModifier && centre && (
          <button className="btn primary" type="button" onClick={() => { setMessage(null); setModifier(true); }}>{t("centre.modifier")}</button>
        )}
      </div>

      {message && <div className={`banner ${message.ton}`} role="status" aria-live="polite">{message.texte}</div>}

      {centre && (
        <>
          <section className="card form-section">
            <h2>{t("centre.identite")}</h2>
            <dl className="infos">
              <Info label={t("centre.code")}><span dir="ltr">{centre.code}</span></Info>
              <Info label={t("centre.nomFr")}>{centre.nomFr}</Info>
              <Info label={t("centre.nomAr")}>{centre.nomAr && <span lang="ar" dir="rtl">{centre.nomAr}</span>}</Info>
              <Info label={t("centre.categorie")}>{centre.categorie}</Info>
              <Info label={t("centre.type")}>{centre.type}</Info>
              <Info label={t("centre.dateConstruction")}>{centre.dateConstruction}</Info>
              <Info label={t("centre.telephone")}>{centre.telephone && <span dir="ltr">{centre.telephone}</span>}</Info>
              <Info label={t("centre.responsable")}>{centre.responsable}</Info>
            </dl>
          </section>

          <section className="card form-section">
            <h2>{t("centre.localisation")}</h2>
            <dl className="infos">
              <Info label={t("centre.region")}>{region && nom(region.nomFr, region.nomAr)}</Info>
              <Info label={t("centre.province")}>{province && nom(province.nomFr, province.nomAr)}</Info>
              <Info label={t("centre.commune")}>{commune && nom(commune.nomFr, commune.nomAr)}</Info>
              <Info label={t("centre.milieu")}>{centre.milieu && t(centre.milieu === "rural" ? "centre.rural" : "centre.urbain")}</Info>
              <Info label={t("centre.adresse")}>{centre.adresse}</Info>
              <Info label={t("centre.latitude")}>{centre.latitude != null && <span dir="ltr">{centre.latitude}</span>}</Info>
              <Info label={t("centre.longitude")}>{centre.longitude != null && <span dir="ltr">{centre.longitude}</span>}</Info>
              <Info label={t("centre.positionExacte")}>{oui(centre.positionExacte)}</Info>
            </dl>
          </section>

          <section className="card form-section">
            <h2>{t("centre.foncier")}</h2>
            <dl className="infos">
              <Info label={t("centre.propriete")}>{centre.foncier?.propriete}</Info>
              <Info label={t("centre.situationFonciere")}>{centre.foncier?.situationFonciere}</Info>
              <Info label={t("centre.proprietaire")}>{centre.foncier?.proprietaire}</Info>
              <Info label={t("centre.loyer")}>{centre.foncier?.loyerMensuel != null ? fmt(Number(centre.foncier.loyerMensuel)) : null}</Info>
              <Info label={t("centre.charges")}>{centre.foncier?.chargesLocatives != null ? fmt(Number(centre.foncier.chargesLocatives)) : null}</Info>
              <Info label={t("centre.numeroContrat")}>{centre.foncier?.numeroContrat}</Info>
              <Info label={t("centre.dateContrat")}>{centre.foncier?.dateContrat}</Info>
              <Info label={t("centre.avenant")}>{centre.foncier?.avenant}</Info>
            </dl>
          </section>

          <section className="card form-section">
            <h2>{t("centre.equipements")}</h2>
            <dl className="infos">
              <Info label={t("centre.eau")}>{oui(centre.equipement?.eau)}</Info>
              <Info label={t("centre.electricite")}>{oui(centre.equipement?.electricite)}</Info>
              <Info label={t("centre.assainissement")}>{oui(centre.equipement?.assainissement)}</Info>
              <Info label={t("centre.telephoneLigne")}>{oui(centre.equipement?.telephone)}</Info>
              <Info label={t("centre.internet")}>{oui(centre.equipement?.internet)}</Info>
              <Info label={t("centre.ordinateurs")}>{fmt(centre.equipement?.nbOrdinateurs ?? 0)}</Info>
              <Info label={t("centre.imprimantes")}>{fmt(centre.equipement?.nbImprimantes ?? 0)}</Info>
            </dl>
          </section>
        </>
      )}

      {modifier && centre && (
        <Modal titre={t("centre.modifier")} large onClose={() => setModifier(false)}>
          <CentreForm centre={centre} onCancel={() => setModifier(false)}
                      onSaved={(c) => { setCentre(c); setModifier(false); setMessage({ ton: "ok", texte: t("centre.enregistre") }); }} />
        </Modal>
      )}

      {id && (
        <section className="card">
          <div className="row" style={{ justifyContent: "space-between", flexWrap: "wrap", marginBottom: 8 }}>
            <h2>{t("centre.activites")}</h2>
            {peutModifier && formActivite === null && (
              <button className="btn" type="button" onClick={() => { setMessage(null); setFormActivite("ajout"); }}>{t("centre.ajouterActivite")}</button>
            )}
          </div>
          <div className="table-wrap">
            <table className="data">
              <thead><tr><th>{t("centre.colActivite")}</th><th>{t("act.colResponsable")}</th><th>{t("centre.capacite")}</th><th>{t("act.colSuperficie")}</th><th>{t("act.colGestion")}</th><th>{t("centre.colOuverture")}</th><th>{t("centre.colEtat")}</th><th><span className="sr-only">{t("users.colActions")}</span></th></tr></thead>
              <tbody>
                {centre?.activites.map((a) => (
                  <tr key={a.id}>
                    <td><strong>{nom(a.nom, a.nomAr)}</strong></td>
                    <td>{a.responsable ?? ""}</td>
                    <td className="num" style={{ textAlign: "start" }}>{a.capacite ?? ""}</td>
                    <td className="num" style={{ textAlign: "start" }}>{a.superficie ?? ""}</td>
                    <td>
                      {a.gestion ? t(`act.gestion.${a.gestion}`) : ""}
                      {a.gestion === "AUTRE" && a.gestionAutre ? ` : ${a.gestionAutre}` : ""}
                      {a.partenaire && <div className="muted" style={{ fontSize: 12 }}>{a.partenaire}{a.dateConvention ? ` · ${a.dateConvention}` : ""}</div>}
                    </td>
                    <td className="muted">{a.dateOuverture ?? ""}</td>
                    <td>
                      {a.dateCessation
                        ? <span className="chip grey" title={a.motifCessation ?? undefined}>{t("centre.cessee", { date: a.dateCessation })}</span>
                        : <span className="chip ok">{t("centre.ouverte")}</span>}
                    </td>
                    <td style={{ textAlign: "end" }}>
                      {peutModifier && (
                        <button className="btn link" type="button" onClick={() => { setMessage(null); setFormActivite(a.id); }}>{t("action.modifier")}</button>
                      )}{" "}
                      {peutModifier && !a.dateCessation && (
                        <button className="btn link" type="button"
                                onClick={() => setCesser({ id: a.id, date: new Date().toISOString().slice(0, 10), motif: "" })}>{t("centre.cesser")}</button>
                      )}
                    </td>
                  </tr>
                ))}
                {centre && centre.activites.length === 0 && <tr><td colSpan={8} className="muted">{t("centre.aucuneActivite")}</td></tr>}
              </tbody>
            </table>
          </div>
          {peutModifier && formActivite === "ajout" && (
            <Modal titre={t("act.titre")} large onClose={() => setFormActivite(null)}>
              <ActiviteCentreForm centreId={id!} catalogue={catalogue} deja={deja} onSaved={activiteEnregistree}
                                  onCancel={() => setFormActivite(null)} />
            </Modal>
          )}
          {peutModifier && typeof formActivite === "number" && centre?.activites.find((a) => a.id === formActivite) && (() => {
            const edition = centre.activites.find((a) => a.id === formActivite)!;
            return (
              <Modal titre={t("act.modification", { nom: nom(edition.nom, edition.nomAr) })} large onClose={() => setFormActivite(null)}>
                <ActiviteCentreForm key={formActivite} centreId={id!} catalogue={catalogue} deja={deja} edition={edition}
                                    onSaved={activiteEnregistree} onCancel={() => setFormActivite(null)} />
              </Modal>
            );
          })()}
          {cesser && (
            <Modal titre={t("centre.cesser")} onClose={() => { setCesser(null); setErreurCess(null); }}>
              <div className="field"><label htmlFor="dcess">{t("centre.dateCessation")}</label>
                <input id="dcess" className="input" type="date" value={cesser.date} onChange={(e) => setCesser({ ...cesser, date: e.target.value })} /></div>
              <div className="field"><label htmlFor="mcess">{t("centre.motif")}</label>
                <input id="mcess" className="input" value={cesser.motif} onChange={(e) => setCesser({ ...cesser, motif: e.target.value })} /></div>
              {erreurCess && <div className="banner bad" role="alert">{erreurCess}</div>}
              <div className="actions">
                <button className="btn danger" type="button" onClick={cesserActivite}>{t("centre.confirmerCessation")}</button>
                <button className="btn" type="button" onClick={() => { setCesser(null); setErreurCess(null); }}>{t("action.annuler")}</button>
              </div>
            </Modal>
          )}
        </section>
      )}
    </>
  );
}
