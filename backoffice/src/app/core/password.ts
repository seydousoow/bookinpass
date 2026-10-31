const LETTERS = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ';
const DIGITS = '23456789';

/** Mot de passe provisoire lisible (sans l/1, O/0) : 12 caractères, au moins une lettre et un chiffre. */
export function temporaryPassword(random: (n: number) => number = (n) => crypto.getRandomValues(new Uint32Array(1))[0] % n): string {
  const all = LETTERS + DIGITS;
  const chars = Array.from({ length: 10 }, () => all[random(all.length)]);
  chars.push(LETTERS[random(LETTERS.length)], DIGITS[random(DIGITS.length)]);
  return chars.join('');
}
