/* oxlint-disable no-console */

import { tmpdir } from 'node:os';
import { join } from 'node:path';
import process from 'node:process';

// The probe intentionally uses the workspace-level Playwright dependency.
// eslint-disable-next-line n/no-extraneous-import
import { chromium } from 'playwright';

const URL =
  process.env.PROBE_URL ??
  'http://localhost:5777/playground/dock-probe/index.html';
const OUT = join(tmpdir(), 'dock-probe-tab.png');
const OUT2 = join(tmpdir(), 'dock-probe-split.png');
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1200, height: 800 } });
const errors = [];
page.on('pageerror', (e) => errors.push(`PAGEERROR: ${e.message}`));
try {
  await page.goto(URL, { waitUntil: 'networkidle', timeout: 30_000 });
} catch (error) {
  console.log('GOTO_ERROR:', error.message);
}
await page.waitForTimeout(1500);
await page.evaluate(() =>
  document.querySelectorAll('[data-app-loading]').forEach((e) => e.remove()),
);

const snap = () =>
  page.evaluate(() => {
    const q = (sel) => {
      const el = document.querySelector(sel);
      if (!el) return null;
      const r = el.getBoundingClientRect();
      const cs = getComputedStyle(el);
      return {
        w: Math.round(r.width),
        h: Math.round(r.height),
        bg: cs.backgroundColor,
        color: cs.color,
        borderLeft: cs.borderLeftColor,
      };
    };
    return {
      activityItems: document.querySelectorAll('.dock-activity-item').length,
      activityLabels: [
        ...document.querySelectorAll('.dock-activity-label'),
      ].map((e) => e.textContent),
      activityActive: [...document.querySelectorAll('.dock-activity-item')].map(
        (e) => e.classList.contains('active'),
      ),
      panelsVisible:
        !!document.querySelector('.dock-panels') &&
        getComputedStyle(document.querySelector('.dock-panels')).display !==
          'none',
      tabs: [...document.querySelectorAll('.dock-tab')].map(
        (e) => e.textContent,
      ),
      closeBtns: document.querySelectorAll('.dock-icon-btn').length,
      activity: q('.dock-activity'),
      panels: q('.dock-panels'),
      header: q('.dock-header'),
      activeTab: window.__dock ? window.__dock.state.activeTab : null,
      open: window.__dock ? [...window.__dock.state.open] : null,
      arrangement: window.__dock ? window.__dock.state.arrangement : null,
      width: window.__dock ? window.__dock.state.width : null,
    };
  });

console.log('=== ERRORS ===');
console.log(errors.length > 0 ? errors.join('\n') : '(none)');
console.log('=== 初始（都关，只有活动栏+画布）===');
console.log(JSON.stringify(await snap(), null, 2));

// 点配置图标 → 开配置
await page.evaluate(() => window.__dock.togglePanel('config'));
await page.waitForTimeout(300);
console.log('=== 点「配置」→ 开单面板 ===');
console.log(JSON.stringify(await snap(), null, 2));

// 点 AI 图标 → 两面板都开（tab）
await page.evaluate(() => window.__dock.togglePanel('ai'));
await page.waitForTimeout(300);
const s2 = await snap();
console.log('=== 再点「AI 生成」→ 两面板 tab ===');
console.log(JSON.stringify(s2, null, 2));
await page.screenshot({ path: OUT });

// 切上下分屏
await page.evaluate(() => window.__dock.toggleArrangement());
await page.waitForTimeout(300);
console.log('=== 切「上下分屏」===');
console.log(JSON.stringify(await snap(), null, 2));
await page.screenshot({ path: OUT2 });

// 关闭一个
await page.evaluate(() => window.__dock.closePanel('config'));
await page.waitForTimeout(300);
console.log('=== 关闭「配置」→ 剩 AI ===');
console.log(JSON.stringify(await snap(), null, 2));

await browser.close();
