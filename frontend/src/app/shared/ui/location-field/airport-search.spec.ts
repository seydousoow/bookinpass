import { Airport } from '@shared/models/place.model';
import { createAirportIndex, normalizeForSearch, searchAirports } from './airport-search';

const airport = (over: Partial<Airport> & Pick<Airport, 'iata'>): Airport => ({
  name: `${over.iata} Airport`,
  cityName: 'Ville',
  cityCode: over.iata,
  state: '',
  hubRank: 4,
  countryName: 'Pays',
  countryCode: 'XX',
  ...over,
});

const DATA: readonly Airport[] = [
  airport({ iata: 'DSS', name: 'Aéroport international Blaise Diagne', cityName: 'Dakar', cityCode: 'DKR', hubRank: 1, countryName: 'Sénégal', countryCode: 'SN' }),
  airport({ iata: 'DKR', name: 'Aéroport Léopold Sédar Senghor', cityName: 'Dakar', cityCode: 'DKR', hubRank: 2, countryName: 'Sénégal', countryCode: 'SN' }),
  airport({ iata: 'ZIG', name: 'Aéroport de Ziguinchor', cityName: 'Ziguinchor', cityCode: 'ZIG', hubRank: 2, countryName: 'Sénégal', countryCode: 'SN' }),
  airport({ iata: 'BXE', name: 'Aéroport de Bakel', cityName: 'Bakel', cityCode: 'BXE', hubRank: 4, countryName: 'Sénégal', countryCode: 'SN' }),
  airport({ iata: 'CDG', name: 'Aéroport Paris-Charles-de-Gaulle', cityName: 'Paris', cityCode: 'PAR', hubRank: 1, countryName: 'France', countryCode: 'FR' }),
  airport({ iata: 'ORY', name: 'Aéroport Paris-Orly', cityName: 'Paris', cityCode: 'PAR', hubRank: 1, countryName: 'France', countryCode: 'FR' }),
  airport({ iata: 'ABJ', name: 'Aéroport Félix-Houphouët-Boigny', cityName: 'Abidjan', cityCode: 'ABJ', hubRank: 1, countryName: "Côte d’Ivoire", countryCode: 'CI' }),
  airport({ iata: 'OGO', name: 'Abengourou Airport', cityName: 'Abengourou', cityCode: 'OGO', hubRank: 4, countryName: "Côte d’Ivoire", countryCode: 'CI' }),
  airport({ iata: 'SPY', name: 'San Pedro Airport', cityName: 'San Pedro', cityCode: 'SPY', hubRank: 2, countryName: "Côte d’Ivoire", countryCode: 'CI' }),
];

describe('searchAirports', () => {
  const index = createAirportIndex(DATA);
  const codes = (q: string) => searchAirports(index, q, 20).map((a) => a.iata);

  it('remonte le code IATA exact en tête', () => {
    expect(codes('DKR')[0]).toBe('DKR');
    expect(codes('CDG')[0]).toBe('CDG');
  });

  it('trouve par nom de ville', () => {
    expect(codes('Dakar')).toEqual(expect.arrayContaining(['DKR', 'DSS']));
    expect(codes('Paris')).toEqual(expect.arrayContaining(['CDG', 'ORY']));
  });

  it('trouve par nom d’aéroport', () => {
    expect(codes('Blaise Diagne')).toContain('DSS');
    expect(codes('Senghor')).toContain('DKR');
  });

  it('trouve par pays', () => {
    const senegal = codes('Sénégal');
    expect(senegal).toEqual(expect.arrayContaining(['DKR', 'DSS', 'ZIG']));
  });

  it('tolère une faute de frappe légère', () => {
    expect(codes('Dakr')).toContain('DKR');
  });

  it('ne remonte rien sur une saisie vide', () => {
    expect(codes('')).toEqual([]);
    expect(codes('   ')).toEqual([]);
  });

  it('respecte la limite demandée', () => {
    expect(searchAirports(index, 'a', 2).length).toBeLessThanOrEqual(2);
  });

  it('écarte le bruit : une saisie sans correspondance ne remonte rien', () => {
    expect(codes('zzzzqqq')).toEqual([]);
  });

  // La recherche est faite sur une forme normalisée ; l'affichage garde les accents.
  describe('insensibilité aux accents', () => {
    it('trouve sans accent ce qui est stocké avec', () => {
      expect(codes('Senegal')).toEqual(expect.arrayContaining(['DKR', 'DSS', 'ZIG']));
      expect(codes('Leopold Sedar Senghor')).toContain('DKR');
      expect(codes('Aeroport')).not.toEqual([]);
    });

    it('trouve aussi avec les accents', () => {
      expect(codes('Sénégal')).toEqual(expect.arrayContaining(['DKR', 'DSS']));
      expect(codes('Aéroport')).not.toEqual([]);
    });

    it('accepte une apostrophe droite là où la donnée en porte une typographique', () => {
      expect(codes("Cote d'Ivoire")).toContain('ABJ');
      expect(codes('Côte d’Ivoire')).toContain('ABJ');
    });

    it('conserve les accents dans le résultat rendu au client', () => {
      const [first] = searchAirports(index, 'Senegal', 5);
      expect(first.countryName).toBe('Sénégal');
      expect(searchAirports(index, 'Blaise Diagne', 1)[0].name).toContain('Aéroport');
    });
  });
});

describe('rang de hub', () => {
  const index = createAirportIndex(DATA);
  const codes = (q: string) => searchAirports(index, q, 20).map((a) => a.iata);

  it('classe le hub principal en tête sur une recherche par pays', () => {
    // Tous les aéroports du pays obtiennent le même score textuel : sans rang,
    // l'ordre serait arbitraire et OGO passait devant ABJ.
    expect(codes("Côte d’Ivoire")[0]).toBe('ABJ');
    expect(codes('Senegal')[0]).toBe('DSS');
  });

  it('classe la porte d’entrée avant l’aéroport secondaire d’une même ville', () => {
    const dakar = codes('Dakar');
    expect(dakar.indexOf('DSS')).toBeLessThan(dakar.indexOf('DKR'));
  });

  it('ne déclasse jamais une correspondance exacte de code', () => {
    // DKR est rang 2, DSS rang 1 : le code exact doit malgré tout rester premier.
    expect(codes('DKR')[0]).toBe('DKR');
    expect(codes('OGO')[0]).toBe('OGO');
  });

  it('ne fait pas remonter un hub devant une correspondance nettement meilleure', () => {
    // BXE est rang 4 ; « Bakel » ne doit pas être supplanté par DSS, rang 1.
    expect(codes('Bakel')[0]).toBe('BXE');
    expect(codes('Ziguinchor')[0]).toBe('ZIG');
  });
});

describe('normalizeForSearch', () => {
  it('retire les accents et met en minuscules', () => {
    expect(normalizeForSearch('Sénégal')).toBe('senegal');
    expect(normalizeForSearch('Aéroport Léopold Sédar Senghor')).toBe(
      'aeroport leopold sedar senghor',
    );
  });

  it('ramène les apostrophes et tirets typographiques à l’ASCII', () => {
    expect(normalizeForSearch('Côte d’Ivoire')).toBe("cote d'ivoire");
    expect(normalizeForSearch('Paris–Orly')).toBe('paris-orly');
  });

  it('décompose les ligatures et lettres barrées', () => {
    expect(normalizeForSearch('Ørsta')).toBe('orsta');
    expect(normalizeForSearch('Łódź')).toBe('lodz');
    expect(normalizeForSearch('Cœur')).toBe('coeur');
    expect(normalizeForSearch('Straße')).toBe('strasse');
  });

  it('compacte les espaces', () => {
    expect(normalizeForSearch('  Dakar   Yoff ')).toBe('dakar yoff');
  });

  it('est idempotente', () => {
    const once = normalizeForSearch('Aéroport Félix-Houphouët-Boigny');
    expect(normalizeForSearch(once)).toBe(once);
  });
});
