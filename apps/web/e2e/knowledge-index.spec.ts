import {test,expect,type APIRequestContext} from '@playwright/test';
import {readFileSync} from 'node:fs';

async function login(request:APIRequestContext,username:string){
 let csrf=await(await request.get('/api/session/csrf')).json();
 await request.post('/api/session/logout',{headers:{[csrf.headerName]:csrf.token}});
 csrf=await(await request.get('/api/session/csrf')).json();
 expect((await request.post('/api/session/login',{headers:{[csrf.headerName]:csrf.token},form:{username,password:readFileSync('../../.demo-password','utf8').trim()}})).ok()).toBe(true);
 const me=await(await request.get('/api/session')).json();
 csrf=await(await request.get('/api/session/csrf')).json();
 expect((await request.post('/api/session/tenant/'+me.memberships[0].tenant_id,{headers:{[csrf.headerName]:csrf.token}})).ok()).toBe(true);
 return (await request.get('/api/session/csrf')).json();
}

test('向量索引重建无需模型调用，企业隔离且仅管理员可操作',async({page})=>{
 const csrf=await login(page.request,'demo1.admin');
 const title='IndexMaintenance-'+Date.now();
 expect((await page.request.post('/api/ai/knowledge',{headers:{[csrf.headerName]:csrf.token},data:{title,text:title+'库存不足禁止预占，取消订单释放库存。'}})).ok()).toBe(true);
 const status=await(await page.request.get('/api/ai/knowledge/index')).json();
 expect(status.enabled).toBe(true);
 await page.goto('/7');
 await page.getByRole('button',{name:'重建当前企业向量索引',exact:true}).click();
 await expect(page.getByText('索引已重建：'+status.chunks+' 个片段，模型调用 0 次。',{exact:true})).toBeVisible();
 const result=await page.request.post('/api/ai/knowledge/index/rebuild',{headers:{[csrf.headerName]:csrf.token}});
 expect(result.ok()).toBe(true);expect((await result.json()).indexedChunks).toBe(status.chunks);
 const answer=await(await page.request.post('/api/ai/ask',{headers:{[csrf.headerName]:csrf.token},data:{question:title}})).json();
 expect(answer.sources.some((source:{title:string})=>source.title===title)).toBe(true);
 const foreign=await login(page.request,'demo2.admin');
 const documents=await(await page.request.get('/api/ai/knowledge')).json();
 expect(documents.some((doc:{title:string})=>doc.title===title)).toBe(false);
 expect((await page.request.post('/api/ai/knowledge/index/rebuild',{headers:{[foreign.headerName]:foreign.token}})).ok()).toBe(true);
 const ops=await login(page.request,'demo1.operations');
 expect((await page.request.get('/api/ai/knowledge/index')).status()).toBe(403);
 expect((await page.request.post('/api/ai/knowledge/index/rebuild',{headers:{[ops.headerName]:ops.token}})).status()).toBe(403);
 await page.goto('/7');
 await expect(page.getByText('仅管理员可维护企业向量索引',{exact:true})).toBeVisible();
});
