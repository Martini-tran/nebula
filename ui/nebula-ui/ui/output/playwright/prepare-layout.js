async (page) => {
  const routes = [
    {path:'/dashboard',name:'Dashboard',meta:{title:'工作台',icon:'lucide:house'},children:[{path:'/analytics',name:'Analytics',component:'/dashboard/analytics/index',meta:{title:'数据概览',icon:'lucide:chart-no-axes-combined'}}]},
    {path:'/ai-flow',name:'AiFlow',meta:{title:'AI 编排',icon:'lucide:workflow'},children:[
      {path:'/ai-flow/list',name:'AiFlowList',component:'/ai-flow/index',meta:{title:'流程列表',icon:'lucide:list-tree',disableTransition:true}},
      {path:'/ai-flow/chat',name:'AiChat',component:'/ai-flow/chat/index',meta:{title:'AI 对话',icon:'lucide:message-circle'}},
      {path:'/ai-flow/create',name:'AiFlowCreate',component:'/ai-flow/create/index',meta:{title:'新建流程',hideInMenu:true,activePath:'/ai-flow/list',disableTransition:true}},
      {path:'/ai-flow/editor',name:'AiFlowEditor',component:'/ai-flow/editor/index',meta:{title:'流程编辑器',hideInMenu:true,activePath:'/ai-flow/list',disableTransition:true}}
    ]},
    {path:'/ai-knowledge',name:'AiKnowledge',meta:{title:'知识库'},children:[{path:'/ai-knowledge/list',name:'AiKnowledgeList',component:'/ai-knowledge/index',meta:{title:'知识库',icon:'lucide:book-open'}}]},
    {path:'/ai-tool',name:'AiTool',meta:{title:'工具管理'},children:[{path:'/ai-tool/list',name:'AiToolList',component:'/ai-tool/index',meta:{title:'工具管理',icon:'lucide:wrench'}}]},
    {path:'/ai-relay',name:'AiRelay',meta:{title:'AI 中转'},children:[{path:'/ai-relay/model',name:'AiRelayModel',component:'/ai-relay/model/index',meta:{title:'模型管理',icon:'lucide:boxes'}}]},
    {path:'/blog',name:'Blog',meta:{title:'博客',icon:'lucide:file-text'},children:[{path:'/blog/tag',name:'BlogTag',component:'/blog/tag/index',meta:{title:'标签管理',icon:'lucide:tag',query:{scope:'team'}}}]},
    {path:'/space',name:'Space',meta:{title:'空间'},children:[{path:'/space/folder',name:'SpaceFolder',component:'/space/folder/index',meta:{title:'文件夹',icon:'lucide:folder'}}]},
    {path:'/system',name:'System',meta:{title:'系统'},children:[{path:'/system/user',name:'SystemUser',component:'/system/user/index',meta:{title:'用户管理',icon:'lucide:users'}}]},
    {path:'/docs',name:'CustomDocs',meta:{title:'使用文档',icon:'lucide:book-open',link:'https://example.test/docs'}}
  ];
  const flowRecords = [
    {flowCode:'weekly-report',name:'每周数据分析报告',description:'自动汇总业务数据，生成分析周报。',version:3,nodeCount:6,status:1,createTime:'2026-09-01 09:00:00',updateTime:'2026-09-15 09:20:00'},
    {flowCode:'customer-feedback',name:'客户反馈智能归档',description:'识别反馈意图并归档到知识库。',version:2,nodeCount:4,status:1,createTime:'2026-09-02 09:00:00',updateTime:'2026-09-15 08:46:00'}
  ];
  await page.unrouteAll({behavior:'ignoreErrors'});
  await page.route('http://127.0.0.1:5185/api/**', async route => {
    const path = route.request().url().split('?')[0];
    let data=[];
    if(path.endsWith('/menu/routes')) data=routes;
    else if(path.endsWith('/menu/perms')) data=['manager:ai-flow:save','manager:ai-flow:query','manager:ai-flow:delete','manager:ai-flow:run'];
    else if(path.includes('/flows/page')) data={records:flowRecords,total:2,current:1,size:10,pages:1};
    else if(path.includes('/flows/') && !path.endsWith('/page')) data={flowCode:'weekly-report',name:'每周数据分析报告',engineType:'DAG',nodes:[],edges:[]};
    else if(path.endsWith('/page')) data={records:[],total:0,current:1,size:10,pages:0};
    else if(path.endsWith('/captcha/config')) data={enabled:false};
    await route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,message:'OK',data})});
  });
  await page.goto('http://127.0.0.1:5185/auth/login');
  await page.waitForSelector('#app');
  await page.evaluate(async()=>{
    const globals=document.querySelector('#app').__vue_app__.config.globalProperties;
    const pinia=globals.$pinia;
    pinia._s.get('core-access').setAccessToken('local-layout-smoke-token');
    pinia._s.get('core-user').setUserInfo({avatar:'',desc:'',homePath:'/ai-flow/list',realName:'布局验证',roles:[],token:'local-layout-smoke-token',userId:'layout-smoke',username:'layout-smoke'});
    const router=globals.$router;
    await router.push('/ai-flow/list');
  });
  await page.waitForSelector('.dual-layout');
  await page.setViewportSize({width:1440,height:1000});
  return {url:page.url(),domains:await page.locator('.domain-button').allTextContents()};
}
