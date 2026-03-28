# ── Build stage ───────────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci --frozen-lockfile

COPY . .
RUN npx vite build

# ── Runtime stage (Nginx) ─────────────────────────────────────────────────────
FROM nginx:alpine AS runtime

COPY --from=build /app/dist /usr/share/nginx/html

# SPA routing: redirect all requests to index.html
RUN printf 'server {\n\
    listen 80;\n\
    root /usr/share/nginx/html;\n\
    index index.html;\n\
    location / {\n\
        try_files $uri $uri/ /index.html;\n\
    }\n\
    location /api {\n\
        proxy_pass http://backend:8080;\n\
    }\n\
}\n' > /etc/nginx/conf.d/default.conf

EXPOSE 80
