-- Datos de prueba, con los MISMOS identificadores y los MISMOS hashes que
-- la clínica demo de Django (`manage.py bootstrap` en clinube). Así las
-- respuestas capturadas en `contrato/` se pueden comparar tal cual,
-- incluidos los `id`.
--
-- La contraseña de las cinco cuentas es «OdontoDemo2026» y los hashes los
-- generó Django: ese es justo el punto de la prueba. Son datos locales de
-- una clínica de demostración, nunca de pacientes reales.

insert into common_tenant (id, name, ruc, address, phone, email, is_active, funcionalidades,
                           modulos_clinica, es_prueba, created_at)
values
  ('88a4cbeb-8604-4c10-b8fd-5bb844cd9362', 'Clínica Demo', '', '', '', '', true, '{}', '{}', true,
   '2026-10-21 04:53:46.378048+00'),
  -- Dada de baja: con ella se comprueba que un token vigente deja de servir.
  ('64b02b7e-a748-4395-9ca3-7490a7d8c314', 'Clínica Apagada', '', '', '', '', false, '{}', '{}', false,
   '2026-10-21 04:53:46.378048+00'),
  -- Otra clínica activa, para las pruebas de aislamiento.
  ('11111111-1111-1111-1111-111111111111', 'Clínica Vecina', '', '', '', '', true, '{}', '{}', false,
   '2026-10-21 04:53:46.378048+00');

insert into accounts_user (id, password, last_login, is_superuser, email, phone, role, is_active,
                           is_staff, created_at, tenant_id, full_name, must_change_password,
                           preferencias, funciones)
values
  -- Administración de la clínica demo. El hash es de Django 5.2.17.
  ('a35f7a96-7c8b-4954-8f61-0596bd5118ad',
   'pbkdf2_sha256$1000000$5nzb8mbtEDCsQIIMppAK3o$G1XYgABbEZjed831acNhDO/p6AI6bTWbm+Y9o1vjVns=',
   null, true, 'admin@clinube.local', null, 'admin', true, true,
   '2026-10-21 04:53:46.378048+00', '88a4cbeb-8604-4c10-b8fd-5bb844cd9362', '', false, '{}', '{}'),
  -- Paciente: no entra por el panel, solo con su código.
  ('f34d4a21-4d5b-4cfa-8a9e-9048f36a6e8e',
   'pbkdf2_sha256$1000000$5nzb8mbtEDCsQIIMppAK3o$G1XYgABbEZjed831acNhDO/p6AI6bTWbm+Y9o1vjVns=',
   null, false, 'paciente@clinube.local', '+593999000001', 'patient', true, false,
   '2026-10-21 04:53:46.378048+00', '88a4cbeb-8604-4c10-b8fd-5bb844cd9362', '', false, '{}', '{}'),
  -- Contraseña puesta por otra persona: solo puede cambiarla.
  ('cf2adf14-2921-4f3f-86a2-ae4e1c681284',
   'pbkdf2_sha256$1000000$5nzb8mbtEDCsQIIMppAK3o$G1XYgABbEZjed831acNhDO/p6AI6bTWbm+Y9o1vjVns=',
   null, false, 'temporal@clinube.local', null, 'doctor', true, false,
   '2026-10-21 04:53:46.378048+00', '88a4cbeb-8604-4c10-b8fd-5bb844cd9362', '', true, '{}', '{}'),
  -- Cuenta dada de baja.
  ('8d494297-cc5a-44fd-852e-7e7498b7ce55',
   'pbkdf2_sha256$1000000$5nzb8mbtEDCsQIIMppAK3o$G1XYgABbEZjed831acNhDO/p6AI6bTWbm+Y9o1vjVns=',
   null, false, 'inactivo@clinube.local', null, 'doctor', false, false,
   '2026-10-21 04:53:46.378048+00', '88a4cbeb-8604-4c10-b8fd-5bb844cd9362', '', false, '{}', '{}'),
  -- Administración de la clínica dada de baja.
  ('02f8978b-2a1c-4ec2-ad9f-9737922ffccc',
   'pbkdf2_sha256$1000000$5nzb8mbtEDCsQIIMppAK3o$G1XYgABbEZjed831acNhDO/p6AI6bTWbm+Y9o1vjVns=',
   null, false, 'apagada@clinube.local', null, 'admin', true, false,
   '2026-10-21 04:53:46.378048+00', '64b02b7e-a748-4395-9ca3-7490a7d8c314', '', false, '{}', '{}'),
  -- Recepción de la clínica demo: no puede aceptar las condiciones.
  ('33333333-3333-3333-3333-333333333333',
   'pbkdf2_sha256$1000000$5nzb8mbtEDCsQIIMppAK3o$G1XYgABbEZjed831acNhDO/p6AI6bTWbm+Y9o1vjVns=',
   null, false, 'recepcion@clinube.local', null, 'reception', true, false,
   '2026-10-21 04:53:46.378048+00', '88a4cbeb-8604-4c10-b8fd-5bb844cd9362', 'Laura Paz', false, '{}', '{}'),
  -- Administración de la clínica vecina: nunca debe ver nada de la demo.
  ('22222222-2222-2222-2222-222222222222',
   'pbkdf2_sha256$1000000$5nzb8mbtEDCsQIIMppAK3o$G1XYgABbEZjed831acNhDO/p6AI6bTWbm+Y9o1vjVns=',
   null, false, 'vecina@clinube.local', null, 'admin', true, false,
   '2026-10-21 04:53:46.378048+00', '11111111-1111-1111-1111-111111111111', '', false, '{}', '{}');
