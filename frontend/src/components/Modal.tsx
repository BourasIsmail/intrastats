"use client";

import { useEffect, useRef, type ReactNode } from "react";
import { useI18n } from "@/lib/i18n";

/**
 * Fenêtre modale basée sur l'élément <dialog> natif : le focus reste dans la fenêtre, Échap la ferme,
 * un clic sur le fond la ferme aussi. À monter seulement quand elle doit être ouverte.
 */
export function Modal({ titre, onClose, children, large = false }: {
  titre: string;
  onClose: () => void;
  children: ReactNode;
  large?: boolean;
}) {
  const { t } = useI18n();
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const d = ref.current;
    if (d && !d.open) d.showModal();
    return () => { if (d?.open) d.close(); };
  }, []);

  return (
    <dialog
      ref={ref}
      className={`modal${large ? " large" : ""}`}
      aria-labelledby="modal-titre"
      onCancel={(e) => { e.preventDefault(); onClose(); }}
      onMouseDown={(e) => { if (e.target === ref.current) onClose(); }}
    >
      <div className="modal-head">
        <h2 id="modal-titre">{titre}</h2>
        <button type="button" className="modal-close" aria-label={t("action.fermer")} onClick={onClose}>×</button>
      </div>
      <div className="modal-body">{children}</div>
    </dialog>
  );
}
