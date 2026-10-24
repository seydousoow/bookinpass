import { temporaryPassword } from './password';

describe('temporaryPassword', () => {
  it('meets the server rule: 10+ characters, a letter and a digit', () => {
    for (let i = 0; i < 50; i++) {
      const password = temporaryPassword();
      expect(password.length).toBeGreaterThanOrEqual(10);
      expect(password).toMatch(/\p{L}/u);
      expect(password).toMatch(/\d/);
      expect(password).not.toMatch(/[01lIO]/);
    }
  });
});
