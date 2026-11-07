import {test,expect} from '@playwright/test';
test('PWA静态缓存不含接口且离线不补交业务',async({page,context})=>{
 await page.goto(process.env.E2E_BASE_URL||'http://localhost:5173');
 await page.evaluate(()=>navigator.serviceWorker.ready.then(()=>true));
 const manifest=await page.request.get('/manifest.webmanifest');expect(manifest.status()).toBe(200);expect((await manifest.json()).display).toBe('standalone');
 await page.reload();await page.request.get('/api/session/csrf');
 const keys=await page.evaluate(async()=>{const result:string[]=[];for(const name of await caches.keys()){for(const req of await(await caches.open(name)).keys())result.push(req.url);}return result;});
 expect(keys.some(k=>k.includes('/api/'))).toBe(false);expect(keys.some(k=>k.endsWith('/offline.html'))).toBe(true);
 await context.setOffline(true);await page.goto('/offline-check-'+Date.now());await expect(page.getByRole('heading',{name:'当前处于离线状态'})).toBeVisible();await expect(page.getByText('库存和订单操作必须在线完成，不会在后台自动补交。')).toBeVisible();await context.setOffline(false);
});
