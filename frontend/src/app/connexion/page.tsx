"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { LangSwitch } from "@/components/LangSwitch";
import { useAuth } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";

export default function ConnexionPage() {
  const { login } = useAuth();
  const { t, erreur: messageErreur } = useI18n();
  const router = useRouter();
  const [identifiant, setIdentifiant] = useState("");
  const [motDePasse, setMotDePasse] = useState("");
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  async function soumettre(e: FormEvent) {
    e.preventDefault();
    setEnvoi(true);
    setErreur(null);
    try {
      await login(identifiant.trim(), motDePasse);
      router.replace("/");
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <div className="login">
      <section className="login-photo">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/connexion.jpg" alt={t("login.photoAlt")} />
        <div className="tag">
          <strong>{t("app.nom")}</strong>
          <span>{t("app.slogan")}</span>
        </div>
        <span className="foot">{t("login.reserve")}</span>
      </section>
      <section className="login-form-side">
        <div className="login-lang"><LangSwitch clair /></div>
        <form className="login-form" onSubmit={soumettre}>
          <h1>{t("login.titre")}</h1>
          <div className="field">
            <label htmlFor="login">{t("login.identifiant")}</label>
            <input id="login" className="input" autoComplete="username" value={identifiant}
                   onChange={(e) => setIdentifiant(e.target.value)} required dir="ltr" />
          </div>
          <div className="field">
            <label htmlFor="mdp">{t("login.motDePasse")}</label>
            <input id="mdp" type="password" className="input" autoComplete="current-password" value={motDePasse}
                   onChange={(e) => setMotDePasse(e.target.value)} required dir="ltr" />
          </div>
          {erreur && <div className="banner bad" role="alert">{erreur}</div>}
          <button className="btn primary" type="submit" disabled={envoi}>{t("login.submit")}</button>
        </form>
      </section>
    </div>
  );
}
