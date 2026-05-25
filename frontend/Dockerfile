FROM node:22-slim AS build

USER root

WORKDIR /app

RUN npm config set registry https://registry.npmmirror.com

COPY package.json package-lock.json ./
RUN npm install --unsafe-perm

COPY . .
RUN node ./node_modules/typescript/bin/tsc -b && node ./node_modules/vite/bin/vite.js build

FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
