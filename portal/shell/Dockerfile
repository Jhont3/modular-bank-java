FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .

ARG NOTIFICATIONS_MFE_URL=http://localhost:8085/mfe/notifications
ARG TRANSFERS_MFE_URL=http://localhost:8085/mfe/transfers
ENV NOTIFICATIONS_MFE_URL=$NOTIFICATIONS_MFE_URL
ENV TRANSFERS_MFE_URL=$TRANSFERS_MFE_URL

RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
