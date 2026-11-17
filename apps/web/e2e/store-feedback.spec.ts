import {test,expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
test('店铺空名称提示与正常创建',async({page})=>{
 await page.goto(process.env.E2E_BASE_URL||'http://localhost:8088');
 await page.getByLabel('账号',{exact:true}).fill('demo1.admin');
 await page.getByLabel('密码',{exact:true}).fill(readFileSync('../../.demo-password','utf8').trim());
 await page.getByRole('button',{name:/登\s*录/}).click();
 await page.getByRole('combobox').first().click();await page.getByText('演示企业1 · ADMIN',{exact:true}).click();
 await page.getByRole('menuitem',{name:'店铺',exact:true}).click();
 await page.getByRole('button',{name:/创建店铺/}).click();
 await expect(page.getByText('请输入1至100字的店铺名称，不可包含逗号或换行',{exact:true})).toBeVisible();
 const name='店铺表单验收-'+Date.now();await page.getByPlaceholder('模拟店铺名称').fill(name);
 const response=page.waitForResponse(r=>r.url().endsWith('/api/stores')&&r.request().method()==='POST');
 await page.getByRole('button',{name:/创建店铺/}).click();expect((await response).status()).toBe(201);
 await page.getByPlaceholder('筛选店铺名称').fill(name);await page.getByPlaceholder('筛选店铺名称').press('Enter');
 await expect(page.getByRole('cell',{name,exact:true})).toBeVisible();
});
