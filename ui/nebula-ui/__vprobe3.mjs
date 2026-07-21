import { chromium } from 'playwright';
const b = await chromium.launch(); const p = await b.newPage({viewport:{width:1200,height:800}});
await p.goto(process.env.PROBE_URL,{waitUntil:'networkidle',timeout:30000}).catch(()=>{});
await p.waitForTimeout(2500);
const r = await p.evaluate(()=>{
  const bars=[...document.querySelectorAll('.code-layout-activity-bar')];
  return bars.map(el=>({cls:el.className, w:Math.round(el.getBoundingClientRect().width), items:el.querySelectorAll('.item').length, itemsAny:el.querySelectorAll('[class*=item]').length, html:el.innerHTML.slice(0,300)}));
});
console.log(JSON.stringify(r,null,2));
await b.close();
