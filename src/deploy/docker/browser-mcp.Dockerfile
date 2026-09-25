FROM mcr.microsoft.com/playwright:v1.55.0-noble
WORKDIR /app
COPY workers/browser-mcp/package.json ./
RUN npm install --omit=dev
COPY workers/browser-mcp/server.mjs ./
USER pwuser
EXPOSE 8080
CMD ["node","server.mjs"]
