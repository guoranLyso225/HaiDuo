import PageBoundary from './PageBoundary';
import messages from './i18n/zh-CN';
const AiAssistant=React.lazy(()=>import('./AiAssistant'));
const SyncTasks=React.lazy(()=>import('./SyncTasks'));
const Settings=React.lazy(()=>import('./Settings'));
const Stores=React.lazy(()=>import('./Stores'));
const Shipments=React.lazy(()=>import('./Dashboard').then(module=>({default:module.Shipments})));
const Overview=React.lazy(()=>import('./Overview'));
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import NetworkStatus from './NetworkStatus';
const queryClient=new QueryClient();
const Profits=React.lazy(()=>import('./Profits'));
const Operations=React.lazy(()=>import('./Operations'));
const Products=React.lazy(()=>import('./Products'));
import Auth from './Auth';
import React from 'react';
import {createRoot} from 'react-dom/client';
import {Layout,Menu,Typography,Alert,Card,ConfigProvider,Grid,Drawer,Button,Spin} from 'antd';
import zhCN from 'antd/locale/zh_CN';
import {BrowserRouter,useNavigate,useLocation} from 'react-router-dom';
const pages=messages.pages;
function App(){const [navOpen,setNavOpen]=React.useState(false);const screens=Grid.useBreakpoint();const navigate=useNavigate();const location=useLocation();const index=Number(location.pathname.slice(1))||0;return <ConfigProvider locale={zhCN}><Layout style={{minHeight:'100vh'}}>{screens.lg?<Layout.Sider><Typography.Title level={3} style={{color:'white',padding:20}}>海舵 HaiDuo</Typography.Title><Menu theme="dark" selectedKeys={[String(index)]} items={pages.map((label,key)=>({key:String(key),label}))} onClick={({key})=>navigate('/'+key)}/></Layout.Sider>:<Drawer title="海舵导航" placement="left" open={navOpen} onClose={()=>setNavOpen(false)}><Menu selectedKeys={[String(index)]} items={pages.map((label,key)=>({key:String(key),label}))} onClick={({key})=>{navigate("/"+key);setNavOpen(false);}}/></Drawer>}<Layout><Layout.Header style={{background:'white'}}>{!screens.lg&&<Button onClick={()=>setNavOpen(true)}>打开导航</Button>}跨境经营工作台 · 开发预览</Layout.Header><Layout.Content style={{padding:screens.lg?24:12,minWidth:0}}><Typography.Title level={2}>{pages[index]??pages[0]}</Typography.Title><PageBoundary key={index}><React.Suspense fallback={<div role="status" style={{padding:24}}><Spin/> 正在加载页面…</div>}>{index===7?<AiAssistant/>:index===8?<SyncTasks/>:index===9?<Settings/>:index===1?<Stores/>:index===0?<Overview/>:index===5?<Shipments/>:index===6?<Profits/>:index===2?<Products/>:index===3?<Operations/>:index===4?<Operations inventory/>:<Alert type="warning" showIcon message={index===7?"AI功能未启用：尚未实现模型连接和知识库，不会冒充真实模型调用。":"同步任务未启用：将在阶段二接通后台同步与消息处理。"}/>}</React.Suspense></PageBoundary><Card style={{marginTop:24}} title="实施状态">本地演示：MOCK为固定样例，真实平台联调待凭据。请查阅 docs/IMPLEMENTATION_PLAN.md。</Card></Layout.Content></Layout></Layout></ConfigProvider>}
createRoot(document.getElementById('root')!).render(<QueryClientProvider client={queryClient}><NetworkStatus><BrowserRouter><Auth><App/></Auth></BrowserRouter></NetworkStatus></QueryClientProvider>);

if(import.meta.env.PROD && 'serviceWorker' in navigator){window.addEventListener('load',()=>{void navigator.serviceWorker.register('/sw.js').catch(()=>console.warn('离线资源初始化失败，请保持在线使用'));});}
