# Imagem de desenvolvimento do frontend: Vite com HMR. As dependências ficam na imagem
# (npm ci) e o código-fonte é montado pelo Compose; Node existe apenas no container.
FROM node:22-alpine

WORKDIR /app
RUN chown node:node /app
# O processo roda como o usuário "node" da imagem, não como root.
USER node

COPY --chown=node:node package.json package-lock.json ./
RUN npm ci

COPY --chown=node:node . .

EXPOSE 5173

CMD ["npm", "run", "dev"]
