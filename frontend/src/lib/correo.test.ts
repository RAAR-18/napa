import { validarCorreo } from './correo';

describe('validarCorreo', () => {
  it.each(['ana@gmail,com', 'ana@gmail', 'ana@@gmail.com', 'ana gmail.com', 'ana@gmail..com'])(
    'rejects the malformed address %s',
    (correo) => {
      expect(validarCorreo(correo)).toMatch(/formato válido/);
    },
  );

  it('suggests the intended common domain', () => {
    expect(validarCorreo('ana@gmial.com')).toBe(
      'El dominio "gmial.com" no coincide con ningún correo conocido. ¿Quisiste decir ana@gmail.com?',
    );
  });

  it('accepts common and institutional addresses', () => {
    expect(validarCorreo('Ana.Perez@Gmail.com')).toBeNull();
    expect(validarCorreo('estudiante@unimagdalena.edu.co')).toBeNull();
    expect(validarCorreo('')).toBeNull();
  });
});
