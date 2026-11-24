export type Role = "ADMIN" | "PROVINCIAL" | "REGIONAL";

export interface User {
  id: number;
  nom: string;
  login: string;
  role: Role;
  regionId: number | null;
  provinceId: number | null;
  motDePasseTemporaire: boolean;
  droits: string[];
}

export interface Periode {
  id: number;
  mois: number;
  annee: number;
  anneeStat: string;
  statut: "OUVERTE" | "CLOTUREE";
  compteeEnStats: boolean;
}

export interface Region {
  id: number;
  nomFr: string;
  nomAr: string | null;
}

export interface Province {
  id: number;
  nomFr: string;
  nomAr: string | null;
  regionId: number;
}

/** Les huit indicateurs d'une activité de centre. null = pas encore saisi. */
export interface Valeurs {
  masculins: number | null;
  feminins: number | null;
  alphabetisation: number | null;
  agentsTitulaires: number | null;
  agentsVacataires: number | null;
  agentsVolontaires: number | null;
  agentsAssociations: number | null;
  agentsAutreDep: number | null;
}

export type CleValeur = keyof Valeurs;

export type EtatCentre = "ACTIF" | "FERME_TEMPORAIREMENT" | "FERME_DEFINITIVEMENT";

export type SaisieStatut = "NON_COMMENCEE" | "BROUILLON" | "SOUMIS" | "VALIDE" | "A_CORRIGER";

export interface Ligne {
  centreActiviteId: number;
  centreId: number;
  centreNom: string;
  centreNomAr: string | null;
  activiteNom: string;
  activiteNomAr: string | null;
  valeurs: Valeurs;
  precedentes: Valeurs;
  ecarts: CleValeur[];
  etat: EtatCentre;
  motif: string | null;
}

export interface Saisie {
  periodeId: number;
  periodeLibelle: string;
  periodeOuverte: boolean;
  provinceId: number;
  provinceNom: string;
  provinceNomAr: string | null;
  regionId: number;
  statut: SaisieStatut;
  motifRenvoi: string | null;
  modifiable: boolean;
  soumiseLe: string | null;
  lignes: Ligne[];
}

export interface Resume {
  provinceId: number;
  provinceNom: string;
  provinceNomAr: string | null;
  regionId: number;
  regionNom: string;
  regionNomAr: string | null;
  statut: SaisieStatut;
  soumiseLe: string | null;
  nbLignes: number;
}

export interface TableauDeBord {
  periodeId: number;
  periodeLibelle: string;
  periodeOuverte: boolean;
  provinces: number;
  nonCommencees: number;
  brouillons: number;
  soumises: number;
  validees: number;
  aCorriger: number;
  centres: number;
  activites: number;
  beneficiaires: number;
}

export interface CentreSummary {
  id: number;
  code: string;
  nomFr: string;
  nomAr: string | null;
  provinceId: number;
  provinceNom: string;
  type: string | null;
  nbActivites: number;
}

export interface Page<T> {
  content: T[];
  total: number;
  page: number;
  size: number;
}

export interface UtilisateurDto {
  id: number;
  matricule: string | null;
  nom: string;
  login: string;
  role: Role;
  regionId: number | null;
  provinceId: number | null;
  email: string | null;
  actif: boolean;
  motDePasseTemporaire: boolean;
}

export interface FoncierDto {
  propriete: string | null;
  situationFonciere: string | null;
  proprietaire: string | null;
  loyerMensuel: number | null;
  chargesLocatives: number | null;
  numeroContrat: string | null;
  dateContrat: string | null;
  avenant: string | null;
}

export interface EquipementDto {
  eau: boolean;
  electricite: boolean;
  assainissement: boolean;
  telephone: boolean;
  internet: boolean;
  nbOrdinateurs: number;
  nbImprimantes: number;
}

export interface ActiviteDto {
  id: number;
  activiteId: number;
  nom: string;
  nomAr: string | null;
  capacite: number | null;
  responsable: string | null;
  dateOuverture: string | null;
  dateCessation: string | null;
  motifCessation: string | null;
  responsableId: number | null;
  superficie: number | null;
  gestion: Gestion | null;
  gestionAutre: string | null;
  partenaire: string | null;
  dateConvention: string | null;
}

export type Gestion = "ENTRAIDE_NATIONALE" | "ASSOCIATION" | "COGESTION" | "AUTRE";
export const GESTIONS: Gestion[] = ["ENTRAIDE_NATIONALE", "ASSOCIATION", "COGESTION", "AUTRE"];

export interface PersonnelDto {
  id: number;
  matricule: string | null;
  nom: string;
  prenom: string;
  cin: string | null;
  dateNaissance: string | null;
  lieuNaissance: string | null;
  sexe: "M" | "F" | null;
  dateRecrutement: string | null;
  fonction: string | null;
  autreFonction: string | null;
  provinceId: number;
  centreId: number | null;
  centreNom: string | null;
  adresse: string | null;
  telephone: string | null;
  situationFamiliale: string | null;
  nbEnfants: number;
  niveauScolaire: string | null;
  actif: boolean;
}

export interface ActiviteCatalogue {
  id: number;
  nom: string;
  nomAr: string | null;
  abreviation: string | null;
  description: string | null;
  categorieId: number;
  categorie: string | null;
  typeActivite: string;
  personnesCibles: string | null;
  priorite: number | null;
  actif: boolean;
  nbCentres: number;
}

export interface CategorieActivite {
  id: number;
  libelle: string;
}

export interface CentreDetail {
  id: number;
  code: string;
  nomFr: string;
  nomAr: string | null;
  categorie: string | null;
  type: string | null;
  provinceId: number;
  communeId: number | null;
  milieu: string | null;
  adresse: string | null;
  latitude: number | null;
  longitude: number | null;
  positionExacte: boolean;
  dateConstruction: string | null;
  telephone: string | null;
  responsable: string | null;
  foncier: FoncierDto | null;
  equipement: EquipementDto | null;
  activites: ActiviteDto[];
}

export interface CatalogueItem {
  id: number;
  nom: string;
  nomAr: string | null;
  abreviation: string | null;
}

export interface Commune {
  id: number;
  nomFr: string;
  nomAr: string | null;
  provinceId: number;
}
