# Public demo deployment with free tiers

This project can run as a public demo on Render Free, with Neon Free for PostgreSQL and Qdrant Cloud Free for RAG vectors. The application image is built from this repository's Dockerfile. Never commit API keys, database passwords, or JWT secrets.

## Free-tier limits to plan around

- Render Free web services sleep after 15 minutes without requests and need about a minute to wake. The free container has limited memory, so the Docker image caps JVM memory.
- Neon Free currently includes 100 compute hours per project per month and 1 GB of database storage; compute scales to zero while idle.
- Qdrant Cloud Free currently includes up to 1 GB RAM and 4 GB disk. Keep the RAG corpus small and monitor its cluster usage.
- Gemini API usage is governed by the Google AI Studio quota for the selected key and model. A free hosting plan does not make Gemini usage unlimited.
- This arrangement is for a demo. Free services can sleep, restart, enforce quotas, or change their limits. Do not use it for real customer orders or sensitive data without backups and a production review.

## Local development with Neon

This repository is a Java 17 Spring Boot project built with Maven; it has no `npm run build` script. After installing the Neon CLI and linking the project branch, pull the database connection settings and start the app:

```powershell
neon env pull --service postgres
.\mvnw.cmd spring-boot:run
```

Spring imports `.env.local` when present. It accepts Neon's standard `DATABASE_URL` format and converts it to JDBC. If `SPRING_DATASOURCE_URL` and the split username/password variables are also set, those take precedence for compatibility with existing hosting configurations. `.env.local` is ignored by Git.

## 1. Create the PostgreSQL database

1. Create a free project in Neon and a database for the application.
2. Copy the connection details from Neon's dashboard.
3. For local runs, the Neon CLI can pull `DATABASE_URL` into `.env.local`. The app imports this ignored file and converts Neon's standard PostgreSQL URL to the JDBC form Spring Boot needs. For Render, either set the same `DATABASE_URL` secret or continue using the existing split variables. If both are set, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` take precedence.

   If setting the split variables, use a JDBC URL in this shape:

   `jdbc:postgresql://HOST/DB_NAME?sslmode=require`

4. Set `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` to the separate Neon database username and password.

The app uses the direct Neon connection URL by default with a small Hikari pool, suitable for this long-running Spring Boot service. Do not commit `.env.local` or paste connection secrets into GitHub.

The application creates its tables and inserts demo catalog, branch, and knowledge-guide data at startup. The bootstrap admin account is created only when all three `BOOTSTRAP_ADMIN_*` values are set and no matching username/email already exists.

## 2. Create the vector database

1. Create a Qdrant Cloud Free cluster.
2. Copy the cluster hostname, gRPC port, and API key. Use the hostname alone (no `https://` prefix).
3. In Render, set `QDRANT_HOST`, `QDRANT_GRPC_PORT` (normally `6334`), `QDRANT_API_KEY`, and `QDRANT_USE_TLS=true`.

When Gemini and Qdrant are configured, the application indexes the role-specific knowledge guides during startup. Wait for indexing to finish before checking the chatbot status.

## 3. Configure Gemini and application secrets

In Render's environment settings, add:

- `GEMINI_API_KEY`: the Gemini API key from Google AI Studio.
- `JWT_SECRET`: a unique random value of at least 32 bytes.
- `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`: a unique admin login; use a password of at least 12 characters.
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`: SMTP access for registration and password-reset OTPs. Render Free blocks outbound SMTP ports 25, 465, and 587, so use an email provider/port Render allows and verify email delivery before sharing registration. Without working mail delivery, customers cannot activate accounts or reset passwords.

Enter these in the provider's private environment-variable editor. Do not put secret values in `render.yaml`, GitHub, screenshots, or chat messages. After the first deployment and successful admin login, retain the password securely; the bootstrap account is not recreated if the user already exists.

## 4. Create the public service

1. Push this repository to GitHub.
2. Sign in to Render, choose **New → Blueprint**, and connect this repository.
3. Review the `techtra-beauty` service from `render.yaml`. Choose the Free plan and provide all required environment values.
4. Deploy. Render assigns a public `https://...onrender.com` URL. The first build and startup may take several minutes; later requests may wait for a sleeping service to wake.
5. Open `/api/assistant/context` while signed out to confirm the public customer chatbot status. After signing in, confirm the relevant role reports ready. If the vector guides remain pending, inspect Render logs for the provider or Qdrant connection error.

## Before sharing the URL

- Use unique production credentials; do not reuse the local demo password.
- Keep the `JWT_SECRET` stable between deploys so sessions/tokens are not invalidated unexpectedly.
- Confirm admin, warehouse, and store-staff demo accounts are not exposed with shared default passwords.
- Confirm database, Gemini, and Qdrant limits in their dashboards. Add billing/spend limits only if the provider requires billing for the selected features.
- The app's uploaded files and generated local files are ephemeral on Render Free. Configure a persistent object-storage integration before relying on uploaded content.
