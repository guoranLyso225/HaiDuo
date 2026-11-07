import {Component,type ReactNode} from 'react';
import {Alert,Button} from 'antd';

export default class PageBoundary extends Component<{children:ReactNode},{failed:boolean}> {
 state={failed:false};
 static getDerivedStateFromError(){return {failed:true};}
 render(){
  if(this.state.failed)return <Alert type="error" showIcon message="页面加载失败" description="网络中断或应用更新可能导致页面资源不可用。请联网后重新加载。" action={<Button onClick={()=>window.location.reload()}>重新加载页面</Button>}/>;
  return this.props.children;
 }
}
