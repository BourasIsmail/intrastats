package ma.entraide.intranet.statistique;

import java.util.List;

/**
 * Les huit indicateurs saisis pour une activité de centre et une période.
 * Un indicateur est null tant qu'il n'a pas été saisi.
 */
public record Valeurs(Integer masculins, Integer feminins, Integer alphabetisation,
                      Integer agentsTitulaires, Integer agentsVacataires, Integer agentsVolontaires,
                      Integer agentsAssociations, Integer agentsAutreDep) {

    public static final List<String> CLES = List.of("masculins", "feminins", "alphabetisation",
            "agentsTitulaires", "agentsVacataires", "agentsVolontaires", "agentsAssociations", "agentsAutreDep");

    public static final List<String> LIBELLES = List.of("Masculins", "Féminins", "Alphabétisation",
            "Agents titulaires", "Agents vacataires", "Agents volontaires", "Agents des associations",
            "Agents autre département");

    public static Valeurs vide() {
        return new Valeurs(null, null, null, null, null, null, null, null);
    }

    public static Valeurs of(Statistique s) {
        if (s == null) {
            return vide();
        }
        return new Valeurs(s.getMasculins(), s.getFeminins(), s.getAlphabetisation(), s.getAgentsTitulaires(),
                s.getAgentsVacataires(), s.getAgentsVolontaires(), s.getAgentsAssociations(), s.getAgentsAutreDep());
    }

    /** Valeurs dans l'ordre de {@link #CLES}. */
    public List<Integer> asList() {
        return java.util.Arrays.asList(masculins, feminins, alphabetisation, agentsTitulaires, agentsVacataires,
                agentsVolontaires, agentsAssociations, agentsAutreDep);
    }

    public void applyTo(Statistique s) {
        s.setMasculins(masculins);
        s.setFeminins(feminins);
        s.setAlphabetisation(alphabetisation);
        s.setAgentsTitulaires(agentsTitulaires);
        s.setAgentsVacataires(agentsVacataires);
        s.setAgentsVolontaires(agentsVolontaires);
        s.setAgentsAssociations(agentsAssociations);
        s.setAgentsAutreDep(agentsAutreDep);
    }
}
