package ma.entraide.intranet.statistique;

public enum EtatCentre {
    ACTIF("Actif"),
    FERME_TEMPORAIREMENT("Fermé temporairement"),
    FERME_DEFINITIVEMENT("Fermé définitivement");

    private final String libelle;

    EtatCentre(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
