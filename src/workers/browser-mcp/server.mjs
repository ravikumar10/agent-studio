import http from "node:http";
import dns from "node:dns/promises";
import net from "node:net";
import { chromium } from "playwright";

const port = Number(process.env.PORT || 8080);
const browser = await chromium.launch({ headless: true });
const json = (response, status, value) => {
  response.writeHead(status, { "content-type": "application/json" });
  response.end(JSON.stringify(value));
};
const body = request => new Promise((resolve, reject) => {
  let value = "";
  request.on("data", chunk => {
    value += chunk;
    if (value.length > 1_000_000) reject(new Error("request exceeds 1 MB"));
  });
  request.on("end", () => resolve(value ? JSON.parse(value) : {}));
  request.on("error", reject);
});
const list = value => String(value || "").toLowerCase().split(",").map(item => item.trim()).filter(Boolean);
const privateAddress = address => address === "::1" || address.startsWith("127.") || address.startsWith("10.") || address.startsWith("192.168.") || /^172\.(1[6-9]|2\d|3[01])\./.test(address) || address.startsWith("169.254.") || address === "0.0.0.0";
async function validateUrl(raw, integration) {
  const url = new URL(raw);
  if (url.protocol !== "https:" && url.protocol !== "http:") throw new Error("browser URL must use HTTP(S)");
  const allowed = list(integration.allowedHosts || process.env.BROWSER_ALLOWED_HOSTS || "example.com,www.example.com");
  if (!allowed.includes(url.hostname.toLowerCase())) throw new Error("browser target host is not allow-listed");
  const addresses = await dns.lookup(url.hostname, { all: true });
  if (addresses.some(item => net.isIP(item.address) && privateAddress(item.address))) throw new Error("private network targets are blocked");
  return url;
}
async function run(input, operation) {
  const integration = input._integration || {};
  const url = await validateUrl(input.url, integration);
  const context = await browser.newContext({ javaScriptEnabled: true, acceptDownloads: false });
  await context.route("**/*", async route => {
    if (route.request().resourceType() !== "document") return route.continue();
    try { await validateUrl(route.request().url(), integration); return route.continue(); }
    catch { return route.abort("blockedbyclient"); }
  });
  const page = await context.newPage();
  const timeout = Math.min(60, Math.max(2, Number(integration.navigationTimeoutSeconds || 20))) * 1000;
  try {
    await page.goto(url.toString(), { waitUntil: "domcontentloaded", timeout });
    const actions = operation === "act" ? (Array.isArray(input.actions) ? input.actions : []) : [];
    const maxActions = Math.min(12, Math.max(1, Number(integration.maxActions || 8)));
    if (actions.length > maxActions) throw new Error(`browser action limit is ${maxActions}`);
    for (const action of actions) {
      const selector = String(action.selector || "");
      if (!selector || selector.length > 300) throw new Error("a bounded selector is required");
      if (action.type === "click") await page.locator(selector).click({ timeout: 5000 });
      else if (action.type === "fill") await page.locator(selector).fill(String(action.value || ""), { timeout: 5000 });
      else if (action.type === "select") await page.locator(selector).selectOption(String(action.value || ""), { timeout: 5000 });
      else throw new Error(`unsupported browser action: ${action.type}`);
    }
    const text = (await page.locator("body").innerText()).replace(/\s+/g, " ").trim().slice(0, 20_000);
    const extracted = {};
    if (operation === "extract" && input.selectors && typeof input.selectors === "object") {
      for (const [name, selectorValue] of Object.entries(input.selectors).slice(0, 30)) {
        const selector = String(selectorValue || "");
        if (!selector || selector.length > 300) throw new Error("extraction selectors must be bounded CSS selectors");
        const locator = page.locator(selector).first();
        extracted[name] = await locator.count() ? (await locator.innerText()).replace(/\s+/g, " ").trim().slice(0, 4000) : null;
      }
    }
    return { source: page.url(), title: await page.title(), text, extracted, actionCount: actions.length, rendered: true };
  } finally { await context.close(); }
}

const server = http.createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/health") return json(response, 200, { status: "UP" });
  if (request.method !== "POST") return json(response, 405, { message: "method not allowed" });
  try {
    const input = await body(request);
    if (request.url === "/tools/browser.navigate") return json(response, 200, await run(input, "navigate"));
    if (request.url === "/tools/browser.extract") return json(response, 200, await run(input, "extract"));
    if (request.url === "/tools/browser.act") return json(response, 200, await run(input, "act"));
    return json(response, 404, { message: "tool not found" });
  } catch (error) { return json(response, 400, { message: error instanceof Error ? error.message : String(error) }); }
});
server.listen(port, "0.0.0.0");
for (const signal of ["SIGTERM", "SIGINT"]) process.on(signal, async () => { await browser.close(); server.close(() => process.exit(0)); });
