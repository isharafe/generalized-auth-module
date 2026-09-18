# syntax=docker/dockerfile:1.7

FROM node:22.20-bookworm-slim AS build

WORKDIR /workspace
COPY integrations/authorization-nuxt ./integrations/authorization-nuxt
COPY examples/authorization-nuxt-demo/frontend ./examples/authorization-nuxt-demo/frontend

WORKDIR /workspace/examples/authorization-nuxt-demo/frontend
RUN --mount=type=cache,target=/root/.npm \
    npm ci --legacy-peer-deps --no-audit --no-fund
RUN npm run build

FROM node:22.20-bookworm-slim

ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=3000

WORKDIR /app
COPY --from=build --chown=node:node \
  /workspace/examples/authorization-nuxt-demo/frontend/.output \
  /app/.output

USER node
EXPOSE 3000

CMD ["node", "/app/.output/server/index.mjs"]
