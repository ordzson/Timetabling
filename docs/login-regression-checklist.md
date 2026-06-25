# Checklist regresion Login

UIO01 congela `Login` como contrato. Antes de cambios UI post-login, verificar:

- JSX de `Login` en `horarios-web/src/main.tsx` sin cambios intencionales.
- `SESSION_KEY` sigue siendo `horarios.session`.
- Submit sigue llamando `POST /api/auth/login`.
- `localStorage` guarda/remueve la sesion igual.
- Clases `.login-*` en `horarios-web/src/styles.css` sin cambios intencionales.
- Textos, placeholders, errores, colores y layout visual iguales.
