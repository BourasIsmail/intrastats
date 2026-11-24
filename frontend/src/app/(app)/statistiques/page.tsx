"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { api, post, put } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { usePeriodes, useTerritoire } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import type { CleTraduction } from "@/lib/i18n/fr";
import { verifier, versSaisi, versValeurs, type Verdict } from "@/lib/rules";
import type { CleValeur, EtatCentre, Resume, Saisie, SaisieStatut } from "@/lib/types";
import { SaisieTable, type RowState } from "@/components/SaisieTable";
import { PeriodeChip, Select, StatutChip } from "@/components/ui";

const STATUTS: SaisieStatut[] = ["SOUMIS", "A_CORRIGER", "VALIDE", "BROUILLON", "NON_COMMENCEE"];

function lignesVersEtats(s: Saisie): Record<number, RowState> {
  const etats: Record<number, RowState> = {};
  for (const l of s.lignes) {
    etats[l.centreActiviteId] = { saisi: versSaisi(l.valeurs), etat: l.etat, motif: l.motif ?? "" };
  }
  return etats;
}

/**
 * Page « Statistiques » : trois usages du même tableau selon le rôle.
 * PROVINCIAL : saisie ; ADMIN : vérification (valider / renvoyer) ; REGIONAL : consultation en lecture seule.
 */
export default function StatistiquesPage() {
  const { user } = useAuth();
  const { t, nom, periode, periodeComplete, erreur: messageErreur } = useI18n();
  const { periodes, periodeId, setPeriodeId } = usePeriodes();
  const { regions, provinces } = useTerritoire();

  const [regionId, setRegionId] = useState("");
  const [statut, setStatut] = useState("");
  const [provinceId, setProvinceId] = useState<number | null>(null);
  const [file, setFile] = useState<Resume[]>([]);

  const [saisie, setSaisie] = useState<Saisie | null>(null);
  const [etats, setEtats] = useState<Record<number, RowState>>({});
  const [modifie, setModifie] = useState(false);
  const [exigerTout, setExigerTout] = useState(false);
  const [motifDecision, setMotifDecision] = useState("");
  const [message, setMessage] = useState<{ ton: "ok" | "bad"; texte: string } | null>(null);
  const [occupe, setOccupe] = useState(false);

  const estAdmin = user?.role === "ADMIN";
  const estProvincial = user?.role === "PROVINCIAL";
  // Export de toute une région : le régional pour sa région, l'admin quand il a filtré sur une région.
  const exportRegion = estProvincial ? null : estAdmin ? regionId || null : user?.regionId ?? null;

  // Provinces proposées : l'admin filtre par région et statut (file de vérification), les autres voient leur périmètre.
  useEffect(() => {
    if (!user || !periodeId || !estAdmin) return;
    const q = new URLSearchParams({ periodeId: String(periodeId) });
    if (regionId) q.set("regionId", regionId);
    if (statut) q.set("statut", statut);
    api<Resume[]>(`/api/saisies?${q}`).then(setFile).catch((e) => setMessage({ ton: "bad", texte: messageErreur(e) }));
  }, [user, estAdmin, periodeId, regionId, statut, messageErreur]);

  const optionsProvinces = useMemo(() => {
    if (estAdmin) return file.map((r) => ({ value: r.provinceId, label: nom(r.provinceNom, r.provinceNomAr) }));
    return provinces.map((p) => ({ value: p.id, label: nom(p.nomFr, p.nomAr) }));
  }, [estAdmin, file, provinces, nom]);

  // Province courante : celle du provincial, sinon la première de la liste si l'actuelle n'y est plus.
  useEffect(() => {
    if (estProvincial) {
      setProvinceId(user?.provinceId ?? null);
    } else if (optionsProvinces.length === 0) {
      setProvinceId(null);
    } else if (!optionsProvinces.some((o) => o.value === provinceId)) {
      setProvinceId(optionsProvinces[0].value);
    }
  }, [estProvincial, user, optionsProvinces, provinceId]);

  const charger = useCallback(async () => {
    if (!periodeId || !provinceId) {
      setSaisie(null);
      return;
    }
    try {
      const s = await api<Saisie>(`/api/statistiques?periodeId=${periodeId}&provinceId=${provinceId}`);
      setSaisie(s);
      setEtats(lignesVersEtats(s));
      setModifie(false);
      setExigerTout(false);
      setMotifDecision("");
    } catch (e) {
      setSaisie(null);
      setMessage({ ton: "bad", texte: messageErreur(e) });
    }
  }, [periodeId, provinceId, messageErreur]);

  useEffect(() => {
    setMessage(null);
    charger();
  }, [charger]);

  const editable = !!saisie?.modifiable;

  const verdicts = useMemo(() => {
    const v: Record<number, Verdict> = {};
    if (!saisie) return v;
    for (const l of saisie.lignes) {
      const e = etats[l.centreActiviteId];
      if (e) v[l.centreActiviteId] = verifier(e.saisi, e.etat, e.motif, exigerTout, t);
    }
    return v;
  }, [saisie, etats, exigerTout, t]);

  const nbErreurs = Object.values(verdicts).reduce((n, v) => n + v.erreurs.length, 0);
  const nbAvertissements = Object.values(verdicts).reduce((n, v) => n + v.avertissements.length, 0);

  function modifier(id: number, patch: Partial<RowState>) {
    setEtats((courant) => ({ ...courant, [id]: { ...courant[id], ...patch } }));
    setModifie(true);
  }

  const onCell = (id: number, cle: CleValeur, valeur: string) =>
    modifier(id, { saisi: { ...etats[id].saisi, [cle]: valeur } });
  const onEtat = (id: number, etat: EtatCentre) => modifier(id, { etat });
  const onMotif = (id: number, motif: string) => modifier(id, { motif });

  /** Nombre d'erreurs par ligne, calculé hors du state pour décider avant l'appel réseau. */
  function compter(exigerChamps: boolean, seulementBornes: boolean) {
    return Object.values(etats).map((row) => {
      const v = verifier(row.saisi, row.etat, row.motif, exigerChamps, t);
      return seulementBornes ? Object.keys(v.champs).length : v.erreurs.length;
    });
  }

  async function enregistrer(): Promise<boolean> {
    if (!saisie) return false;
    // Une valeur hors bornes ne s'enregistre pas, même en brouillon.
    if (compter(false, true).some((n) => n > 0)) {
      setMessage({ ton: "bad", texte: t("stats.erreursABloquer") });
      return false;
    }
    const lignes = saisie.lignes.map((l) => {
      const e = etats[l.centreActiviteId];
      return { centreActiviteId: l.centreActiviteId, valeurs: versValeurs(e.saisi), etat: e.etat, motif: e.motif || null };
    });
    await put(`/api/statistiques/lignes?periodeId=${saisie.periodeId}&provinceId=${saisie.provinceId}`, { lignes });
    return true;
  }

  async function action(fn: () => Promise<unknown>, succes: CleTraduction) {
    setOccupe(true);
    setMessage(null);
    try {
      await fn();
      await charger();
      setMessage({ ton: "ok", texte: t(succes) });
    } catch (e) {
      setMessage({ ton: "bad", texte: messageErreur(e) });
    } finally {
      setOccupe(false);
    }
  }

  const brouillon = () => action(async () => { await enregistrer(); }, "stats.msgBrouillon");

  async function soumettre() {
    setExigerTout(true);
    if (compter(true, false).some((n) => n > 0)) {
      setMessage({ ton: "bad", texte: t("stats.erreursSoumission") });
      return;
    }
    await action(async () => {
      if (await enregistrer()) {
        await post(`/api/statistiques/soumettre?periodeId=${saisie!.periodeId}&provinceId=${saisie!.provinceId}`);
      } else {
        throw new Error(t("stats.enregistrementImpossible"));
      }
    }, "stats.msgSoumise");
  }

  const cible = () => `periodeId=${saisie!.periodeId}&provinceId=${saisie!.provinceId}`;
  const reprendre = () => action(() => post(`/api/statistiques/brouillon?${cible()}`), "stats.msgReprise");
  const valider = () => action(() => post(`/api/statistiques/valider?${cible()}`), "stats.msgValidee");
  const renvoyer = () => action(() => post(`/api/statistiques/renvoyer?${cible()}`, { motif: motifDecision }), "stats.msgRenvoyee");

  const indexProvince = optionsProvinces.findIndex((o) => o.value === provinceId);
  const aller = (delta: number) => {
    const suivante = optionsProvinces[indexProvince + delta];
    if (suivante) setProvinceId(suivante.value);
  };

  const titre = t(estProvincial ? "stats.titreSaisie" : estAdmin ? "stats.titreVerification" : "stats.titreConsultation");
  const periodeCourante = periodes.find((p) => p.id === saisie?.periodeId);
  const nomProvince = saisie ? nom(saisie.provinceNom, saisie.provinceNomAr) : "";
  const num = (cle: "f1" | "f2" | "f3" | "f4", simple: CleTraduction) => t(estAdmin ? (`stats.${cle}` as CleTraduction) : simple);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{titre}</h1>
          {saisie && (
            <div className="sub">
              <span>
                {estProvincial ? t("stats.provinceDe", { nom: nomProvince }) : nomProvince}
                {periodeCourante ? ` · ${periode(periodeCourante)}` : ""}
              </span>
              <PeriodeChip ouverte={saisie.periodeOuverte} />
              <StatutChip statut={saisie.statut} />
            </div>
          )}
        </div>
        <div className="actions">
          {saisie && (
            <>
              <a className="btn" href={`/api/statistiques/export.pdf?${cible()}`}>{t("stats.exportPdf")}</a>
              <a className="btn" href={`/api/statistiques/export.xlsx?${cible()}`}>{t("stats.exportExcel")}</a>
              {exportRegion && (
                <>
                  <a className="btn" href={`/api/statistiques/export.pdf?periodeId=${saisie.periodeId}&regionId=${exportRegion}`}>{t("stats.regionPdf")}</a>
                  <a className="btn" href={`/api/statistiques/export.xlsx?periodeId=${saisie.periodeId}&regionId=${exportRegion}`}>{t("stats.regionExcel")}</a>
                </>
              )}
            </>
          )}
          {estProvincial && editable && (
            <>
              <button className="btn" type="button" onClick={brouillon} disabled={occupe || !modifie}>{t("stats.brouillon")}</button>
              <button className="btn primary" type="button" onClick={soumettre} disabled={occupe}>{t("stats.soumettre")}</button>
            </>
          )}
          {estProvincial && saisie?.statut === "SOUMIS" && saisie.periodeOuverte && (
            <button className="btn" type="button" onClick={reprendre} disabled={occupe}>{t("stats.reprendre")}</button>
          )}
        </div>
      </div>

      {message && <div className={`banner ${message.ton === "ok" ? "ok" : "bad"}`} role="status" aria-live="polite">{message.texte}</div>}

      {saisie?.statut === "A_CORRIGER" && saisie.motifRenvoi && (
        <div className="banner warn"><strong>{t("stats.renvoyeeMotif")}</strong> {saisie.motifRenvoi}</div>
      )}

      {editable && (
        <div className={`banner ${nbErreurs > 0 ? "bad" : nbAvertissements > 0 ? "warn" : "ok"}`} role="status" aria-live="polite">
          <strong>
            {nbErreurs > 0
              ? nbErreurs > 1 ? t("stats.bannerErreurs", { n: nbErreurs }) : t("stats.bannerErreur1")
              : nbAvertissements > 0 ? t("stats.bannerAucuneErreur") : t("stats.bannerValide")}
          </strong>
          <span>
            {nbErreurs > 0
              ? t("stats.bannerBloque")
              : nbAvertissements > 0
                ? t("stats.bannerAvertissements", { n: nbAvertissements })
                : t("stats.bannerToutCorrect")}
          </span>
        </div>
      )}

      <section className="card tight">
        <div className="filters" style={{ padding: "4px 4px 12px" }}>
          <Select id="periode" label={num("f1", "stats.periode")} value={periodeId ?? ""}
                  onChange={(v) => setPeriodeId(Number(v))}
                  options={periodes.map((p) => ({ value: p.id, label: periodeComplete(p) }))} />
          {estAdmin && (
            <Select id="region" label={t("stats.f2")} value={regionId} onChange={setRegionId} vide={t("stats.toutesRegions")}
                    options={regions.map((r) => ({ value: r.id, label: nom(r.nomFr, r.nomAr) }))} />
          )}
          {!estProvincial && (
            <Select id="province" label={num("f3", "stats.province")} value={provinceId ?? ""}
                    onChange={(v) => setProvinceId(Number(v))} options={optionsProvinces} />
          )}
          {estAdmin && (
            <Select id="statut" label={t("stats.f4")} value={statut} onChange={setStatut} vide={t("stats.tousStatuts")}
                    options={STATUTS.map((s) => ({ value: s, label: t(`statut.${s}` as CleTraduction) }))} />
          )}
          {!estProvincial && (
            <div className="actions">
              <button className="btn" type="button" onClick={() => aller(-1)} disabled={indexProvince <= 0}>{t("stats.precedente")}</button>
              <button className="btn" type="button" onClick={() => aller(1)} disabled={indexProvince < 0 || indexProvince >= optionsProvinces.length - 1}>{t("stats.suivante")}</button>
            </div>
          )}
        </div>

        {saisie && saisie.lignes.length > 0 ? (
          <SaisieTable lignes={saisie.lignes} etats={etats} verdicts={verdicts} editable={editable}
                       showEcarts={!estProvincial} onCell={onCell} onEtat={onEtat} onMotif={onMotif} />
        ) : (
          <p className="muted" style={{ padding: 12 }}>{saisie ? t("stats.vide") : t("stats.choisir")}</p>
        )}
      </section>

      {estAdmin && saisie?.statut === "SOUMIS" && (
        <section className="card" style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <h2>{t("stats.decision")}</h2>
          <div className="field">
            <label htmlFor="motif">{t("stats.motifRenvoi")}</label>
            <textarea id="motif" className="textarea" rows={3} value={motifDecision} onChange={(e) => setMotifDecision(e.target.value)} />
          </div>
          <div className="actions" style={{ justifyContent: "flex-end" }}>
            <button className="btn danger" type="button" onClick={renvoyer} disabled={occupe || motifDecision.trim().length < 5}>{t("stats.renvoyer")}</button>
            <button className="btn primary" type="button" onClick={valider} disabled={occupe}>{t("stats.valider")}</button>
          </div>
        </section>
      )}
    </>
  );
}
