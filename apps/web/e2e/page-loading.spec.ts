import {test,expect} from '@playwright/test';
import {readFileSync} from 'node:fs';

test('页面按需加载，资源失败后可重新加载恢复',async({page})=>{
 const assets:string[]=[];
 page.on('request',request=>{if(request.url().includes('/assets/'))assets.push(request.url());});
 await page.goto('/');
 await expect(page.getByLabel('账号',{exact:true})).toBeVisible();
 expect(assets.some(url=>/\/(Overview|Products|Operations|AiAssistant)-/.test(url))).toBe(false);
 await page.getByLabel('账号',{exact:true}).fill('demo1.admin');
 await page.getByLabel('密码',{exact:true}).fill(readFileSync('../../.demo-password','utf8').trim());
 await page.getByRole('button',{name:/登\s*录/,exact:true}).click();
 await page.getByRole('combobox').first().click();
 await page.getByText('演示企业1 · ADMIN',{exact:true}).click();
 await expect(page.getByRole('img',{name:'按UTC日期统计的人民币销售趋势'})).toBeVisible();
 expect(assets.some(url=>/\/Overview-/.test(url))).toBe(true);
 expect(assets.some(url=>/\/Products-/.test(url))).toBe(false);
 await page.route('**/assets/Products-*.js',route=>route.abort());
 await page.getByRole('menuitem',{name:'商品',exact:true}).click();
 await expect(page.getByText('页面加载失败',{exact:true})).toBeVisible();
 await page.unroute('**/assets/Products-*.js');
 await page.getByRole('button',{name:'重新加载页面',exact:true}).click();
 await expect(page.getByRole('heading',{name:'商品',exact:true})).toBeVisible();
 await expect(page.getByText('页面加载失败',{exact:true})).toHaveCount(0);
 await expect(page.getByRole('button',{name:'创建商品 SKU',exact:true})).toBeVisible();
});
