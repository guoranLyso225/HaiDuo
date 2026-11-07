import {useEffect,useRef} from 'react';
import {init,use} from 'echarts/core';
import {BarChart} from 'echarts/charts';
import {GridComponent,TooltipComponent} from 'echarts/components';
import {CanvasRenderer} from 'echarts/renderers';
use([BarChart,GridComponent,TooltipComponent,CanvasRenderer]);
export default function SalesChart({data}:{data:{day:string;sales_cny:string}[]}){const ref=useRef<HTMLDivElement>(null);useEffect(()=>{if(!ref.current)return;const chart=init(ref.current);chart.setOption({tooltip:{trigger:'axis'},grid:{containLabel:true,left:20,right:20},xAxis:{type:'category',data:data.map(d=>d.day)},yAxis:{type:'value',name:'销售额 CNY'},series:[{type:'bar',data:data.map(d=>Number(d.sales_cny)),itemStyle:{color:'#1677ff'}}]});const observer=new ResizeObserver(()=>chart.resize());observer.observe(ref.current);return()=>{observer.disconnect();chart.dispose();};},[data]);return <div ref={ref} role="img" aria-label="按UTC日期统计的人民币销售趋势" style={{height:300,width:'100%'}}/>}
