package ma.entraide.intranet.statistique;

import java.util.ArrayList;
import java.util.List;

/**
 * Règles de validation d'une ligne de statistiques. Les mêmes règles sont appliquées dans le navigateur
 * (retour immédiat) et ici (seule la version serveur fait foi).
 */
public final class Regles {

    public static final int MAX = 99_999;
    public static final int MOTIF_MIN = 5;

    private Regles() {}

    /** Contrôles valables aussi pour un brouillon : bornes des valeurs saisies. */
    public static List<String> bornes(Valeurs v) {
        List<String> erreurs = new ArrayList<>();
        List<Integer> valeurs = v.asList();
        for (int i = 0; i < valeurs.size(); i++) {
            Integer x = valeurs.get(i);
            if (x != null && (x < 0 || x > MAX)) {
                erreurs.add(Valeurs.LIBELLES.get(i) + " : entier entre 0 et " + MAX + ".");
            }
        }
        return erreurs;
    }

    /** Contrôles complets exigés à la soumission. */
    public static List<String> complet(Valeurs v, EtatCentre etat, String motif) {
        List<String> erreurs = new ArrayList<>(bornes(v));
        List<Integer> valeurs = v.asList();
        for (int i = 0; i < valeurs.size(); i++) {
            if (valeurs.get(i) == null) {
                erreurs.add(Valeurs.LIBELLES.get(i) + " : obligatoire.");
            }
        }
        if (v.alphabetisation() != null && v.masculins() != null && v.feminins() != null
                && v.alphabetisation() > v.masculins() + v.feminins()) {
            erreurs.add("Alphabétisation (" + v.alphabetisation() + ") dépasse le total des bénéficiaires ("
                    + (v.masculins() + v.feminins()) + ").");
        }
        if (etat != EtatCentre.ACTIF && (motif == null || motif.trim().length() < MOTIF_MIN)) {
            erreurs.add("Motif obligatoire si le centre n'est pas actif (" + MOTIF_MIN + " caractères minimum).");
        }
        return erreurs;
    }

    /** Écart notable par rapport à la période précédente : au moins 50 % et au moins 5 unités. */
    public static boolean ecartNotable(Integer actuel, Integer precedent) {
        if (actuel == null || precedent == null) {
            return false;
        }
        int diff = Math.abs(actuel - precedent);
        if (diff < 5) {
            return false;
        }
        double pct = precedent == 0 ? 100 : 100.0 * diff / precedent;
        return pct >= 50;
    }
}
