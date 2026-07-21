import { chromium } from 'playwright';
const URL = process.env.PROBE_URL;
const OUT = 'C:/Users/tran/AppData/Local/Temp/claude/d--dingjian-api-dts-nebula/0e8ad010-0c44-41b2-b51c-a9e3f906b037/scratchpad/clv-shot.png';
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1200, height: 800 } });
const errors = [];
page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message));
try { await page.goto(URL, { waitUntil: 'networkidle', timeout: 30000 }); } catch (e) { console.log('GOTO_ERROR:', e.message); }
await page.waitForTimeout(2500); await page.evaluate(() => document.querySelectorAll("[data-app-loading],.loading,#__app-loading__").forEach(e=>e.remove()));
const diag = await page.evaluate(() => window.__diag ? window.__diag() : 'NO_DIAG');
await page.screenshot({ path: OUT });
console.log('=== ERRORS ==='); console.log(errors.length ? errors.join('\n') : '(none)');
console.log('=== DIAG ==='); console.log(JSON.stringify(diag, null, 2));
await browser.close();
