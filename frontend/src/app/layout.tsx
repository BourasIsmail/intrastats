import type { Metadata } from "next";
import { cookies } from "next/headers";
import type { ReactNode } from "react";
import { AuthProvider } from "@/lib/auth";
import { I18nProvider } from "@/lib/i18n";
import { COOKIE_LANGUE, langueValide, traduire } from "@/lib/i18n/core";
import "./globals.css";

// La langue est lue dans un cookie côté serveur : la page arrive déjà dans le bon sens (RTL en arabe), sans clignotement.
async function langue() {
  return langueValide((await cookies()).get(COOKIE_LANGUE)?.value);
}

export async function generateMetadata(): Promise<Metadata> {
  const lang = await langue();
  return { title: traduire(lang, "app.titre"), description: traduire(lang, "app.description") };
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  const lang = await langue();
  return (
    <html lang={lang} dir={lang === "ar" ? "rtl" : "ltr"}>
      <body>
        <I18nProvider initiale={lang}>
          <AuthProvider>{children}</AuthProvider>
        </I18nProvider>
      </body>
    </html>
  );
}
