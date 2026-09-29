FROM node:22-alpine
WORKDIR /app

# A committed package-lock.json exists, so `npm ci` installs the exact resolved
# tree. The previous `COPY package.json` + `npm install` ignored the lockfile
# and re-resolved ranges on every build, so the image was not reproducible.
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY src/ src/

# `node` already exists in this image and owns /app after the installs above,
# so no new user is needed -- just drop root before starting.
USER node

EXPOSE 4000

# Exec form, matching infra/docker-compose.yml's govsim healthcheck exactly.
# The previous line was shell form ending in `|| exit 1`, and the JS argument
# needed its own nested quoting inside the shell string; as a single array
# element the quoting problem disappears too. process.exit(r.ok?0:1) already
# gives Docker the non-zero code it needs, so there is no trailing operator.
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD ["node", "-e", "fetch('http://127.0.0.1:4000/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"]

CMD ["node", "src/index.js"]
