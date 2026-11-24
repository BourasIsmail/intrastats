-- Nom arabe des activités (interface bilingue). Optionnel : l'ancienne base n'en avait pas, on retombe sur le nom français.
ALTER TABLE activite ADD COLUMN nom_ar VARCHAR(250);
