package ma.entraide.intranet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours complets sur base H2 (mode MySQL) avec les données d'exemple.
 * Chaque test est annulé à la fin (@Transactional).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiIntegrationTest {

    private static final long OCTOBRE = 2;
    private static final long SALE = 2;
    private static final long KENITRA = 3;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    // ------------------------------------------------------------ outils

    private Cookie login(String login, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").header("X-Requested-With", "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"motDePasse\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie c = r.getResponse().getCookie("access_token");
        assertThat(c).isNotNull();
        assertThat(c.isHttpOnly()).isTrue();
        return c;
    }

    private MockHttpServletRequestBuilder with(MockHttpServletRequestBuilder b, Cookie c) {
        return b.cookie(c).header("X-Requested-With", "test");
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode saisie(Cookie c, long province) throws Exception {
        return body(mvc.perform(with(get("/api/statistiques?periodeId=" + OCTOBRE + "&provinceId=" + province), c))
                .andExpect(status().isOk()).andReturn());
    }

    private String ligne(long centreActiviteId, int m, int f, int alpha, String etat, String motif) {
        return "{\"centreActiviteId\":" + centreActiviteId + ",\"valeurs\":{\"masculins\":" + m + ",\"feminins\":" + f
                + ",\"alphabetisation\":" + alpha + ",\"agentsTitulaires\":1,\"agentsVacataires\":0,"
                + "\"agentsVolontaires\":1,\"agentsAssociations\":0,\"agentsAutreDep\":0},\"etat\":\"" + etat
                + "\",\"motif\":" + (motif == null ? "null" : "\"" + motif + "\"") + "}";
    }

    /** Remplit correctement les 5 activités de Salé (ids 2 à 6). */
    private String saisieComplete() {
        return "{\"lignes\":[" + ligne(2, 20, 78, 60, "ACTIF", null) + "," + ligne(3, 14, 11, 0, "ACTIF", null) + ","
                + ligne(4, 22, 19, 0, "ACTIF", null) + ","
                + ligne(5, 0, 0, 0, "FERME_TEMPORAIREMENT", "Travaux de réfection") + ","
                + ligne(6, 18, 45, 30, "ACTIF", null) + "]}";
    }

    // ------------------------------------------------------------ authentification

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/periodes")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").header("X-Requested-With", "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"admin\",\"motDePasse\":\"mauvais\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyMysqlPasswordIsUpgradedToBcryptOnFirstLogin() throws Exception {
        String before = jdbc.queryForObject("SELECT mot_de_passe FROM utilisateur WHERE login = 'admin'", String.class);
        assertThat(before).startsWith("{mysql}");

        login("admin", "admin2026");
        em.flush();   // le test est transactionnel : on force l'écriture avant de relire en SQL

        String after = jdbc.queryForObject("SELECT mot_de_passe FROM utilisateur WHERE login = 'admin'", String.class);
        assertThat(after).startsWith("{bcrypt}");
        // le même mot de passe fonctionne toujours avec le nouveau hash
        login("admin", "admin2026");
    }

    @Test
    void loginIsBlockedAfterTooManyFailures() throws Exception {
        // Identifiant dédié : le compteur est en mémoire et partagé entre les tests, il ne doit pas bloquer les autres.
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").header("X-Requested-With", "test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"cible.bloquee\",\"motDePasse\":\"x" + i + "\"}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").header("X-Requested-With", "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"cible.bloquee\",\"motDePasse\":\"x\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void mutatingRequestWithoutCustomHeaderIsRefused() throws Exception {
        Cookie admin = login("admin", "admin2026");
        mvc.perform(post("/api/periodes").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mois\":11,\"annee\":2026}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ périodes

    @Test
    void onlyOnePeriodCanBeOpenAndOnlyAdminManagesPeriods() throws Exception {
        Cookie admin = login("admin", "admin2026");
        Cookie prov = login("prov.sale", "prov2026");

        // une période est déjà ouverte (octobre) : refus
        mvc.perform(with(post("/api/periodes"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mois\":11,\"annee\":2026}")).andExpect(status().isConflict());
        // un provincial ne gère pas les périodes
        mvc.perform(with(post("/api/periodes/" + OCTOBRE + "/cloturer"), prov)).andExpect(status().isForbidden());

        mvc.perform(with(post("/api/periodes/" + OCTOBRE + "/cloturer"), admin)).andExpect(status().isOk());
        mvc.perform(with(post("/api/periodes"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mois\":11,\"annee\":2026}")).andExpect(status().isOk());
        // octobre ne peut plus être rouverte tant que novembre est ouverte
        mvc.perform(with(post("/api/periodes/" + OCTOBRE + "/rouvrir"), admin)).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------ périmètre

    @Test
    void provincialSeesOnlyOwnProvince() throws Exception {
        Cookie prov = login("prov.sale", "prov2026");
        mvc.perform(with(get("/api/statistiques?periodeId=" + OCTOBRE + "&provinceId=" + KENITRA), prov))
                .andExpect(status().isForbidden());
        JsonNode provinces = body(mvc.perform(with(get("/api/provinces"), prov)).andExpect(status().isOk()).andReturn());
        assertThat(provinces).hasSize(1);
        assertThat(provinces.get(0).get("nomFr").asText()).isEqualTo("Salé");
    }

    @Test
    void regionalSeesProvincesOfOwnRegionInReadOnly() throws Exception {
        Cookie reg = login("reg.rsk", "reg2026");
        JsonNode provinces = body(mvc.perform(with(get("/api/provinces"), reg)).andExpect(status().isOk()).andReturn());
        assertThat(provinces).hasSize(3);               // Rabat, Salé, Kénitra ; pas Fès

        JsonNode s = saisie(reg, SALE);
        assertThat(s.get("modifiable").asBoolean()).isFalse();
        mvc.perform(with(get("/api/statistiques?periodeId=" + OCTOBRE + "&provinceId=4"), reg))
                .andExpect(status().isForbidden());      // Fès : autre région
        mvc.perform(with(put("/api/statistiques/lignes?periodeId=" + OCTOBRE + "&provinceId=" + SALE), reg)
                .contentType(MediaType.APPLICATION_JSON).content(saisieComplete())).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ saisie

    @Test
    void tableListsOneRowPerActivityWithPreviousMonthValues() throws Exception {
        Cookie prov = login("prov.sale", "prov2026");
        JsonNode s = saisie(prov, SALE);
        assertThat(s.get("statut").asText()).isEqualTo("NON_COMMENCEE");
        assertThat(s.get("modifiable").asBoolean()).isTrue();
        JsonNode lignes = s.get("lignes");
        assertThat(lignes).hasSize(5);
        // Centre Bouknadel · Alphabétisation : septembre = 21 masculins, 40 féminins
        JsonNode bouknadel = null;
        for (JsonNode l : lignes) {
            if (l.get("centreNom").asText().equals("Centre Bouknadel")) {
                bouknadel = l;
            }
        }
        assertThat(bouknadel).isNotNull();
        assertThat(bouknadel.get("precedentes").get("masculins").asInt()).isEqualTo(21);
        assertThat(bouknadel.get("precedentes").get("feminins").asInt()).isEqualTo(40);
    }

    @Test
    void fullWorkflowDraftSubmitValidateAndLock() throws Exception {
        Cookie prov = login("prov.sale", "prov2026");
        Cookie admin = login("admin", "admin2026");

        // 1. brouillon incomplet : enregistré, mais refusé à la soumission
        mvc.perform(with(put("/api/statistiques/lignes?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lignes\":[" + ligne(2, 20, 78, 60, "ACTIF", null) + "]}")).andExpect(status().isOk());
        assertThat(saisie(prov, SALE).get("statut").asText()).isEqualTo("BROUILLON");
        MvcResult refused = mvc.perform(with(post("/api/statistiques/soumettre?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov))
                .andExpect(status().isUnprocessableEntity()).andReturn();
        assertThat(body(refused).get("details").size()).isGreaterThan(0);

        // 2. saisie complète puis soumission
        mvc.perform(with(put("/api/statistiques/lignes?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov)
                .contentType(MediaType.APPLICATION_JSON).content(saisieComplete())).andExpect(status().isOk());
        JsonNode soumis = body(mvc.perform(with(post("/api/statistiques/soumettre?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov))
                .andExpect(status().isOk()).andReturn());
        assertThat(soumis.get("statut").asText()).isEqualTo("SOUMIS");
        assertThat(soumis.get("modifiable").asBoolean()).isFalse();

        // 3. soumise : plus modifiable, mais le provincial peut la reprendre
        mvc.perform(with(put("/api/statistiques/lignes?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov)
                .contentType(MediaType.APPLICATION_JSON).content(saisieComplete())).andExpect(status().isConflict());
        mvc.perform(with(post("/api/statistiques/brouillon?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov))
                .andExpect(status().isOk());
        mvc.perform(with(post("/api/statistiques/soumettre?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov))
                .andExpect(status().isOk());

        // 4. l'admin vérifie : le provincial ne peut pas valider ; l'admin détecte les écarts
        mvc.perform(with(post("/api/statistiques/valider?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov))
                .andExpect(status().isForbidden());
        JsonNode vue = saisie(admin, SALE);
        boolean ecartTrouve = false;
        for (JsonNode l : vue.get("lignes")) {
            if (l.get("activiteNom").asText().equals("Alphabétisation") && l.get("centreNom").asText().equals("Centre Tabriquet")) {
                ecartTrouve = l.get("ecarts").toString().contains("feminins");
            }
        }
        assertThat(ecartTrouve).as("féminins 30 -> 78 doit être signalé").isTrue();

        JsonNode valide = body(mvc.perform(with(post("/api/statistiques/valider?periodeId=" + OCTOBRE + "&provinceId=" + SALE), admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(valide.get("statut").asText()).isEqualTo("VALIDE");

        // 5. validée : verrouillée
        mvc.perform(with(post("/api/statistiques/brouillon?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov))
                .andExpect(status().isConflict());
        Integer audits = jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'SAISIE_VALIDEE'", Integer.class);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void returnedSubmissionMustBeCorrectedAndResubmitted() throws Exception {
        Cookie prov = login("prov.sale", "prov2026");
        Cookie admin = login("admin", "admin2026");
        mvc.perform(with(put("/api/statistiques/lignes?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov)
                .contentType(MediaType.APPLICATION_JSON).content(saisieComplete())).andExpect(status().isOk());
        mvc.perform(with(post("/api/statistiques/soumettre?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov))
                .andExpect(status().isOk());

        // renvoi sans motif : refusé
        mvc.perform(with(post("/api/statistiques/renvoyer?periodeId=" + OCTOBRE + "&provinceId=" + SALE), admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(with(post("/api/statistiques/renvoyer?periodeId=" + OCTOBRE + "&provinceId=" + SALE), admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Vérifier les féminins de Tabriquet\"}"))
                .andExpect(status().isOk());

        JsonNode s = saisie(prov, SALE);
        assertThat(s.get("statut").asText()).isEqualTo("A_CORRIGER");
        assertThat(s.get("motifRenvoi").asText()).contains("Tabriquet");
        assertThat(s.get("modifiable").asBoolean()).isTrue();
        mvc.perform(with(post("/api/statistiques/soumettre?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov))
                .andExpect(status().isOk());
    }

    @Test
    void invalidValuesAreRejectedEvenAsDraft() throws Exception {
        Cookie prov = login("prov.sale", "prov2026");
        mvc.perform(with(put("/api/statistiques/lignes?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lignes\":[" + ligne(2, -5, 10, 0, "ACTIF", null) + "]}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cannotWriteToAnActivityOfAnotherProvince() throws Exception {
        Cookie prov = login("prov.sale", "prov2026");
        // activité 7 = Centre Maamora (Kénitra)
        mvc.perform(with(put("/api/statistiques/lignes?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lignes\":[" + ligne(7, 1, 1, 0, "ACTIF", null) + "]}")).andExpect(status().isUnprocessableEntity());
        // et pas dans une autre province non plus
        mvc.perform(with(put("/api/statistiques/lignes?periodeId=" + OCTOBRE + "&provinceId=" + KENITRA), prov)
                .contentType(MediaType.APPLICATION_JSON).content(saisieComplete())).andExpect(status().isForbidden());
    }

    @Test
    void closedPeriodLocksEntry() throws Exception {
        Cookie prov = login("prov.sale", "prov2026");
        Cookie admin = login("admin", "admin2026");
        mvc.perform(with(post("/api/periodes/" + OCTOBRE + "/cloturer"), admin)).andExpect(status().isOk());
        mvc.perform(with(put("/api/statistiques/lignes?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov)
                .contentType(MediaType.APPLICATION_JSON).content(saisieComplete())).andExpect(status().isConflict());
        assertThat(saisie(prov, SALE).get("modifiable").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------ listes, export, centres, comptes

    @Test
    void dashboardAndVerificationQueueReflectStatuses() throws Exception {
        Cookie prov = login("prov.sale", "prov2026");
        Cookie admin = login("admin", "admin2026");
        mvc.perform(with(put("/api/statistiques/lignes?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov)
                .contentType(MediaType.APPLICATION_JSON).content(saisieComplete())).andExpect(status().isOk());
        mvc.perform(with(post("/api/statistiques/soumettre?periodeId=" + OCTOBRE + "&provinceId=" + SALE), prov))
                .andExpect(status().isOk());

        JsonNode d = body(mvc.perform(with(get("/api/tableau-de-bord?periodeId=" + OCTOBRE), admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(d.get("provinces").asInt()).isEqualTo(4);
        assertThat(d.get("soumises").asInt()).isEqualTo(1);
        assertThat(d.get("nonCommencees").asInt()).isEqualTo(3);

        JsonNode file = body(mvc.perform(with(get("/api/saisies?periodeId=" + OCTOBRE + "&statut=SOUMIS"), admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(file).hasSize(1);
        assertThat(file.get(0).get("provinceNom").asText()).isEqualTo("Salé");
    }

    @Test
    void excelExportRespectsScope() throws Exception {
        Cookie prov = login("prov.sale", "prov2026");
        MvcResult ok = mvc.perform(with(get("/api/statistiques/export.xlsx?periodeId=" + OCTOBRE), prov))
                .andExpect(status().isOk()).andReturn();
        assertThat(ok.getResponse().getContentAsByteArray()).startsWith('P', 'K');   // fichier zip (xlsx)
        mvc.perform(with(get("/api/statistiques/export.xlsx?periodeId=" + OCTOBRE + "&provinceId=" + KENITRA), prov))
                .andExpect(status().isForbidden());
    }

    @Test
    void pdfExportContainsTheTableAndRespectsScope() throws Exception {
        Cookie prov = login("prov.sale", "prov2026");
        MvcResult ok = mvc.perform(with(get("/api/statistiques/export.pdf?periodeId=" + OCTOBRE), prov))
                .andExpect(status().isOk()).andReturn();
        byte[] data = ok.getResponse().getContentAsByteArray();
        assertThat(new String(data, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(data);
        assertThat(reader.getNumberOfPages()).isEqualTo(1);               // une seule province dans le périmètre
        String texte = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(texte).contains("Salé").contains("Centre Bouknadel").contains("Total province");
        assertThat(texte).doesNotContain("Kénitra");

        mvc.perform(with(get("/api/statistiques/export.pdf?periodeId=" + OCTOBRE + "&provinceId=" + KENITRA), prov))
                .andExpect(status().isForbidden());

        // l'admin obtient une section par province (4 provinces d'exemple)
        Cookie admin = login("admin", "admin2026");
        byte[] tout = mvc.perform(with(get("/api/statistiques/export.pdf?periodeId=" + OCTOBRE), admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new com.lowagie.text.pdf.PdfReader(tout).getNumberOfPages()).isGreaterThanOrEqualTo(4);
        // le régional exporte sa région seulement (pas Fès)
        Cookie reg = login("reg.rsk", "reg2026");
        byte[] region = mvc.perform(with(get("/api/statistiques/export.pdf?periodeId=" + OCTOBRE), reg))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        com.lowagie.text.pdf.PdfReader r = new com.lowagie.text.pdf.PdfReader(region);
        assertThat(r.getNumberOfPages()).isEqualTo(3);
    }

    @Test
    void closedActivityStaysVisibleInPeriodsWhereItHasFigures() throws Exception {
        Cookie admin = login("admin", "admin2026");
        Cookie prov = login("prov.sale", "prov2026");
        // Couture et broderie (centre Hay Salam, activité 5) cesse le 1er août 2026 : elle a pourtant des chiffres en septembre.
        jdbc.update("UPDATE centre_activite SET date_cessation = DATE '2026-08-01' WHERE id = 5");
        JsonNode sept = body(mvc.perform(with(get("/api/statistiques?periodeId=1&provinceId=" + SALE), admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(sept.get("lignes")).hasSize(5);           // septembre : 5 lignes malgré la cessation
        JsonNode oct = saisie(prov, SALE);
        assertThat(oct.get("lignes")).hasSize(4);            // octobre : plus de chiffres, plus de ligne
    }

    @Test
    void centreFormCarriesLandRegistryAndEquipment() throws Exception {
        Cookie prov = login("prov.sale", "prov2026");
        String form = "{\"code\":\"SL-200\",\"nomFr\":\"Centre complet\",\"nomAr\":\"مركز كامل\",\"communeId\":2,"
                + "\"latitude\":34.05,\"longitude\":-6.8,\"positionExacte\":true,"
                + "\"foncier\":{\"propriete\":\"Location\",\"loyerMensuel\":2500.00,\"numeroContrat\":\"C-1\",\"dateContrat\":\"2024-01-15\"},"
                + "\"equipement\":{\"eau\":true,\"electricite\":true,\"assainissement\":false,\"telephone\":false,"
                + "\"internet\":true,\"nbOrdinateurs\":8,\"nbImprimantes\":2}}";
        JsonNode cree = body(mvc.perform(with(post("/api/centres"), prov).contentType(MediaType.APPLICATION_JSON).content(form))
                .andExpect(status().isOk()).andReturn());
        assertThat(cree.get("foncier").get("loyerMensuel").decimalValue()).isEqualByComparingTo("2500");
        assertThat(cree.get("equipement").get("nbOrdinateurs").asInt()).isEqualTo(8);
        assertThat(cree.get("nomAr").asText()).isEqualTo("مركز كامل");

        long id = cree.get("id").asLong();
        String modif = form.replace("\"nbOrdinateurs\":8", "\"nbOrdinateurs\":12");
        JsonNode maj = body(mvc.perform(with(put("/api/centres/" + id), prov).contentType(MediaType.APPLICATION_JSON).content(modif))
                .andExpect(status().isOk()).andReturn());
        assertThat(maj.get("equipement").get("nbOrdinateurs").asInt()).isEqualTo(12);

        // un nombre négatif est refusé
        mvc.perform(with(put("/api/centres/" + id), prov).contentType(MediaType.APPLICATION_JSON)
                .content(form.replace("\"nbOrdinateurs\":8", "\"nbOrdinateurs\":-1"))).andExpect(status().isUnprocessableEntity());

        // ouvrir puis cesser une activité du catalogue
        JsonNode avecActivite = body(mvc.perform(with(post("/api/centres/" + id + "/activites"), prov)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activiteId\":1,\"capacite\":30}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(avecActivite.get("activites")).hasSize(1);
        long caId = avecActivite.get("activites").get(0).get("id").asLong();
        JsonNode cessee = body(mvc.perform(with(post("/api/centres/" + id + "/activites/" + caId + "/cesser"), prov)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-10-31\",\"motif\":\"Fin de convention\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(cessee.get("activites").get(0).get("dateCessation").asText()).isEqualTo("2026-10-31");
    }

    @Test
    void provincialManagesCentresOnlyInOwnProvince() throws Exception {
        Cookie prov = login("prov.sale", "prov2026");
        JsonNode liste = body(mvc.perform(with(get("/api/centres"), prov)).andExpect(status().isOk()).andReturn());
        assertThat(liste.get("total").asInt()).isEqualTo(3);

        String form = "{\"code\":\"SL-100\",\"nomFr\":\"Centre Test\",\"type\":\"Centre simple\",\"provinceId\":" + KENITRA + "}";
        JsonNode cree = body(mvc.perform(with(post("/api/centres"), prov).contentType(MediaType.APPLICATION_JSON).content(form))
                .andExpect(status().isOk()).andReturn());
        // le provincial ne peut pas choisir une autre province : le centre est créé à Salé
        assertThat(cree.get("provinceId").asLong()).isEqualTo(SALE);
        mvc.perform(with(post("/api/centres"), prov).contentType(MediaType.APPLICATION_JSON).content(form))
                .andExpect(status().isConflict());                                    // code déjà utilisé

        Cookie reg = login("reg.rsk", "reg2026");
        mvc.perform(with(post("/api/centres"), reg).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"X-1\",\"nomFr\":\"X\",\"provinceId\":" + SALE + "}")).andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminManagesUsers() throws Exception {
        Cookie admin = login("admin", "admin2026");
        Cookie prov = login("prov.sale", "prov2026");
        mvc.perform(with(get("/api/utilisateurs"), prov)).andExpect(status().isForbidden());

        String form = "{\"nom\":\"Nouveau\",\"login\":\"nouveau.prov\",\"role\":\"PROVINCIAL\",\"provinceId\":" + KENITRA + "}";
        JsonNode created = body(mvc.perform(with(post("/api/utilisateurs"), admin).contentType(MediaType.APPLICATION_JSON).content(form))
                .andExpect(status().isOk()).andReturn());
        String temporaire = created.get("motDePasseTemporaire").asText();
        assertThat(temporaire).hasSize(14);
        // le nouveau compte se connecte avec le mot de passe temporaire
        login("nouveau.prov", temporaire);
        // un compte provincial sans province est refusé
        mvc.perform(with(post("/api/utilisateurs"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"X\",\"login\":\"x.prov\",\"role\":\"PROVINCIAL\"}")).andExpect(status().isUnprocessableEntity());
    }
}
