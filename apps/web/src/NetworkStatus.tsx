import {useEffect,useState,type ReactNode} from 'react';
import {Alert} from 'antd';
export default function NetworkStatus({children}:{children:ReactNode}){const [online,setOnline]=useState(navigator.onLine);useEffect(()=>{const sync=()=>setOnline(navigator.onLine);window.addEventListener('online',sync);window.addEventListener('offline',sync);return()=>{window.removeEventListener('online',sync);window.removeEventListener('offline',sync);};},[]);return <>{!online&&<Alert banner type="warning" message="当前离线：订单与库存写入必须联网，重新联网后请刷新数据。"/>}{children}</>}
