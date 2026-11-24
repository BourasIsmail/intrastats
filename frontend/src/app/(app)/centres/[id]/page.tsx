"use client";

import { useParams } from "next/navigation";
import { CentreEditor } from "@/components/CentreEditor";
import { useI18n } from "@/lib/i18n";

export default function FicheCentrePage() {
  const params = useParams<{ id: string }>();
  const { t } = useI18n();
  const id = Number(params.id);
  if (!Number.isInteger(id)) return <div className="banner bad">{t("centre.inconnu")}</div>;
  return <CentreEditor key={id} id={id} />;
}
