| Petición | Ruta | Resultado |
|---|---|---|
| salud | `GET /api/v1/health/` | igual a Django |
| disponibilidad | `GET /api/v1/ready/` | igual a Django |
| login correcto | `POST /api/v1/auth/login/` | igual a Django |
| login contraseña mala | `POST /api/v1/auth/login/` | igual a Django |
| login sin campos | `POST /api/v1/auth/login/` | igual a Django |
| login campos vacíos | `POST /api/v1/auth/login/` | igual a Django |
| login de un paciente | `POST /api/v1/auth/login/` | igual a Django |
| login usuario inactivo | `POST /api/v1/auth/login/` | igual a Django |
| login método GET | `GET /api/v1/auth/login/` | igual a Django |
| refresco sin campo | `POST /api/v1/auth/token/refresh/` | igual a Django |
| refresco con basura | `POST /api/v1/auth/token/refresh/` | igual a Django |
| perfil | `GET /api/v1/auth/me/` | igual a Django |
| perfil sin token | `GET /api/v1/auth/me/` | igual a Django |
| perfil token basura | `GET /api/v1/auth/me/` | igual a Django |
| cabecera mal formada | `GET /api/v1/auth/me/` | igual a Django |
