import {TenantScope} from './TenantScope';
import {useEffect,useState,type ReactNode} from 'react';
import {Alert,Button,Card,Form,Input,Select,Space,Spin} from 'antd';
type Session={username:string;activeTenant:string;memberships:{tenant_id:string;name:string;role:string}[]};
async function mutate(path:string,body?:URLSearchParams){const token=await fetch('/api/session/csrf').then(r=>r.json());const r=await fetch(path,{method:'POST',headers:{[token.headerName]:token.token},body});if(!r.ok)throw Error(r.status===401?'账号或密码错误':r.status===403?'无操作权限':'操作失败，请重试');}
export default function Auth({children}:{children:ReactNode}){
 const [session,setSession]=useState<Session|null>(null),[loading,setLoading]=useState(true),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 async function load(){setLoading(true);try{const r=await fetch('/api/session');if(r.status===401){setSession(null);return;}if(!r.ok)throw Error('读取会话失败');setSession(await r.json());}catch(e){setError(String(e));}finally{setLoading(false);}}
 useEffect(()=>{void load();},[]);
 async function action(work:()=>Promise<void>){setBusy(true);setError('');try{await work();await load();}catch(e){setError(e instanceof Error?e.message:'请求失败');}finally{setBusy(false);}}
 if(loading)return <Spin fullscreen/>;
 if(!session)return <Card title="海舵 HaiDuo · 登录" style={{maxWidth:420,margin:'10vh auto'}}>{error&&<Alert type="error" message={error}/>}<Form onFinish={values=>void action(()=>mutate('/api/session/login',new URLSearchParams(values)))} layout="vertical"><Form.Item name="username" label="账号" rules={[{required:true}]}><Input autoComplete="username"/></Form.Item><Form.Item name="password" label="密码" rules={[{required:true}]}><Input.Password autoComplete="current-password"/></Form.Item><Button htmlType="submit" type="primary" loading={busy}>登录</Button><Button onClick={()=>void load()}>重试</Button></Form></Card>;
 return <><Card size="small"><Space wrap><span>{session.username}</span><Select placeholder="选择企业" style={{minWidth:220}} value={session.activeTenant||undefined} options={session.memberships.map(m=>({value:m.tenant_id,label:`${m.name} · ${m.role}`}))} onChange={id=>void action(()=>mutate('/api/session/tenant/'+id))} disabled={busy}/><Button onClick={()=>void action(()=>mutate('/api/session/logout'))}>退出</Button></Space>{error&&<Alert type="error" message={error}/>}</Card>{session.activeTenant?<TenantScope.Provider value={session.username+":"+session.activeTenant}><div key={session.activeTenant}>{children}</div></TenantScope.Provider>:<Alert message="请先选择有权访问的企业"/>}</>;
}
