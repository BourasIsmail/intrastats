package ma.entraide.intranet;

import ma.entraide.intranet.security.LegacyMysqlPasswordEncoder;
import ma.entraide.intranet.statistique.EtatCentre;
import ma.entraide.intranet.statistique.Regles;
import ma.entraide.intranet.statistique.Valeurs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnitTests {

    private final LegacyMysqlPasswordEncoder legacy = new LegacyMysqlPasswordEncoder();

    @Test
    void legacyHashMatchesMysqlPasswordFunction() {
        // SELECT PASSWORD('test') en MySQL donne cette valeur.
        String hash = "*94BDCEBE19083CE2A1F959FD02F964C7AF4CFC29";
        assertThat(legacy.matches("test", hash)).isTrue();
        assertThat(legacy.matches("test", hash.toLowerCase())).isTrue();
        assertThat(legacy.matches("autre", hash)).isFalse();
        assertThat(legacy.matches("test", "pas-un-hash")).isFalse();
    }

    @Test
    void completeRowIsValid() {
        Valeurs v = new Valeurs(10, 20, 15, 1, 0, 2, 0, 0);
        assertThat(Regles.complet(v, EtatCentre.ACTIF, null)).isEmpty();
    }

    @Test
    void allIndicatorsAreRequiredOnSubmit() {
        Valeurs v = new Valeurs(10, 20, 15, 1, 0, 2, 0, null);
        assertThat(Regles.complet(v, EtatCentre.ACTIF, null)).hasSize(1).first().asString().contains("autre département");
    }

    @Test
    void literacyCannotExceedBeneficiaries() {
        Valeurs v = new Valeurs(10, 20, 31, 1, 0, 2, 0, 0);
        assertThat(Regles.complet(v, EtatCentre.ACTIF, null)).hasSize(1).first().asString().contains("dépasse");
    }

    @Test
    void closedCentreNeedsReason() {
        Valeurs v = new Valeurs(0, 0, 0, 1, 0, 0, 0, 0);
        assertThat(Regles.complet(v, EtatCentre.FERME_TEMPORAIREMENT, " ab ")).hasSize(1);
        assertThat(Regles.complet(v, EtatCentre.FERME_TEMPORAIREMENT, "Travaux de réfection")).isEmpty();
    }

    @Test
    void boundsRejectNegativeAndTooLarge() {
        assertThat(Regles.bornes(new Valeurs(-1, 100_000, null, null, null, null, null, null))).hasSize(2);
        assertThat(Regles.bornes(Valeurs.vide())).isEmpty();
    }

    @Test
    void notableGapNeedsAtLeast50PercentAndFiveUnits() {
        assertThat(Regles.ecartNotable(78, 30)).isTrue();
        assertThat(Regles.ecartNotable(0, 21)).isTrue();
        assertThat(Regles.ecartNotable(3, 1)).isFalse();      // +200 % mais seulement 2 unités
        assertThat(Regles.ecartNotable(21, 20)).isFalse();
        assertThat(Regles.ecartNotable(null, 20)).isFalse();
    }
}
