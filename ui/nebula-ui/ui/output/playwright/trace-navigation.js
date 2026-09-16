async (page) => {
  await page.evaluate(()=>{
    const router=document.querySelector('#app').__vue_app__.config.globalProperties.$router;
    window.__layoutTrace=[];
    const push=router.push.bind(router);
    router.push=(to)=>{window.__layoutTrace.push({to,stack:new Error().stack});return push(to);};
    router.afterEach((to,from)=>window.__layoutTrace.push({to:to.fullPath,from:from.fullPath}));
  });
  await page.getByRole('menuitem',{name:'知识库',exact:true}).click();
  await page.waitForTimeout(800);
  return await page.evaluate(()=>({url:location.href,trace:window.__layoutTrace}));
}
