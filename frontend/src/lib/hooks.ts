"use client";

import { useEffect, useState } from "react";
import { api } from "./api";
import type { Periode, Province, Region } from "./types";

/** Liste des périodes (la plus récente d'abord) et période choisie par défaut : l'ouverte, sinon la dernière. */
export function usePeriodes() {
  const [periodes, setPeriodes] = useState<Periode[]>([]);
  const [periodeId, setPeriodeId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  const reload = async (keep?: number | null) => {
    const list = await api<Periode[]>("/api/periodes");
    setPeriodes(list);
    setPeriodeId((current) => {
      const wanted = keep ?? current;
      if (wanted && list.some((p) => p.id === wanted)) return wanted;
      return (list.find((p) => p.statut === "OUVERTE") ?? list[0])?.id ?? null;
    });
    setLoading(false);
  };

  useEffect(() => {
    reload().catch(console.error);
  }, []);

  return { periodes, periodeId, setPeriodeId, loading, reload };
}

export function useTerritoire() {
  const [regions, setRegions] = useState<Region[]>([]);
  const [provinces, setProvinces] = useState<Province[]>([]);
  useEffect(() => {
    Promise.all([api<Region[]>("/api/regions"), api<Province[]>("/api/provinces")])
      .then(([r, p]) => {
        setRegions(r);
        setProvinces(p);
      })
      .catch(console.error);
  }, []);
  return { regions, provinces };
}
