import {useEffect,useState} from 'react';
import {Alert,Button,Card,Space} from 'antd';

type Status={enabled:boolean;chunks:number;basis:string};
export default function KnowledgeIndex(){
 const [status,setStatus]=useState<Status>(),[error,setError]=useState(''),[busy,setBusy]=useState(false),[restricted,setRestricted]=useState(false),[result,setResult]=useState('');
 async function load(){setBusy(true);setError('');try{
  const r=await fetch('/api/ai/knowledge/index');if(r.status===403){setRestricted(true);return;}if(!r.ok)throw Error('索引状态读取失败');setStatus(await r.json());
 }catch(e){setError(e instanceof Error?e.message:'状态读取失败');}finally{setBusy(false);}}
 useEffect(()=>{void load();},[]);
 async function rebuild(){setBusy(true);setError('');setResult('');try{
  const token=await fetch('/api/session/csrf').then(r=>r.json());
  const r=await fetch('/api/ai/knowledge/index/rebuild',{method:'POST',headers:{[token.headerName]:token.token}});
  const data=await r.json();if(!r.ok)throw Error(data.message||'重建失败，请重试');
  setResult('索引已重建：'+data.indexedChunks+' 个片段，模型调用 '+data.modelCalls+' 次。');
 }catch(e){setError(e instanceof Error?e.message:'重建失败');}finally{setBusy(false);}}
 return <Card title="知识库索引维护"><Space direction="vertical">
  {restricted?<Alert message="仅管理员可维护企业向量索引"/>:<>
   <Alert message={status?(status.enabled?'独立向量库已启用，主库共 '+status.chunks+' 个片段':'未配置独立向量库：使用主库检索，无需重建'):'正在读取索引状态'} description="从本企业已保存向量重建索引并清理孤立记录，不调用模型、不改知识正文。更换嵌入模型仍需重新向量化。"/>
   <Space><Button loading={busy} onClick={()=>void load()}>刷新索引状态</Button><Button disabled={busy||!status?.enabled} onClick={()=>void rebuild()}>重建当前企业向量索引</Button></Space>
  </>}
  {error&&<Alert type="error" message={error}/>}{result&&<Alert type="success" message={result}/>}
 </Space></Card>;
}
