package ma.entraide.intranet.statistique;

/**
 * Etat de la saisie d'une province pour une période.
 * BROUILLON -> SOUMIS -> VALIDE, ou SOUMIS -> A_CORRIGER (renvoi par l'admin) -> SOUMIS.
 * NON_COMMENCEE n'est jamais stocké : c'est l'absence de ligne saisie_province.
 */
public enum SaisieStatut {
    NON_COMMENCEE,
    BROUILLON,
    SOUMIS,
    VALIDE,
    A_CORRIGER;

    /** Le provincial peut modifier tant que la saisie n'est ni soumise ni validée. */
    public boolean modifiable() {
        return this == NON_COMMENCEE || this == BROUILLON || this == A_CORRIGER;
    }
}
