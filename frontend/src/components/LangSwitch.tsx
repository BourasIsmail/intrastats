"use client";

import { useI18n, type Lang } from "@/lib/i18n";

/** Bascule français / arabe. Le texte de chaque bouton est écrit dans sa propre langue. */
export function LangSwitch({ clair = false }: { clair?: boolean }) {
  const { lang, setLang, t } = useI18n();
  const options: { code: Lang; label: string }[] = [
    { code: "fr", label: t("lang.fr") },
    { code: "ar", label: t("lang.ar") },
  ];
  return (
    <div className={`lang-switch${clair ? " clair" : ""}`} role="group" aria-label={t("lang.label")}>
      {options.map((o) => (
        <button key={o.code} type="button" lang={o.code} aria-pressed={lang === o.code} onClick={() => setLang(o.code)}>
          {o.label}
        </button>
      ))}
    </div>
  );
}
