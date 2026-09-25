import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import embed, {type Result as VegaResult} from 'vega-embed';

type Agent = { id:string; displayName:string; interactionMode?:'TASK'|'CHAT'|'TASK_AND_CHAT'; topology?:'SINGLE_AGENT'|'MULTI_AGENT' };
type Run = { runId:string; agentId:string; agentVersion:string; status:string; createdAt:string; output:Record<string,unknown>; error?:string };
type RunEvent = { runId:string; type:string; occurredAt:string; attributes:Record<string,unknown> };
type Message = { role:'user'|'assistant'; text:string; output?:Record<string,unknown> };

function chartSpecs(value:unknown):any[] {
  if(value&&typeof value==='object'){
    const response=(value as Record<string,unknown>).response as Record<string,unknown>|undefined;const blocks=Array.isArray(response?.blocks)?response.blocks as Record<string,unknown>[]:[];
    if(blocks.length)return blocks.filter(block=>block.type==='chart').map(block=>(block.content as Record<string,unknown>)?.spec).filter(Boolean);
  }
  const found:any[]=[];const signatures=new Set<string>();
  const visit=(candidate:unknown)=>{if(!candidate||typeof candidate!=='object')return;const record=candidate as Record<string,unknown>;
    if(record.generated===true&&record.format==='VEGA_LITE'&&record.spec)add(record.spec);
    if(record.format==='VEGA_LITE'&&record.spec)add(record.spec);
    Object.values(record).forEach(nested=>Array.isArray(nested)?nested.forEach(visit):visit(nested));
  };const add=(spec:unknown)=>{const signature=JSON.stringify(spec);if(!signatures.has(signature)){signatures.add(signature);found.push(spec)}};visit(value);return found;
}
function responseText(output:Record<string,unknown>|undefined,fallback:string){const response=output?.response as Record<string,unknown>|undefined;const blocks=Array.isArray(response?.blocks)?response.blocks as Record<string,unknown>[]:[];const text=blocks.find(block=>block.type==='markdown')?.content;return typeof text==='string'?text:fallback}
function chartSpec(value:unknown):any|null {
  if(!value||typeof value!=='object')return null;
  const record=value as Record<string,unknown>;
  if(record.$schema&&record.data&&record.mark)return record;
  return null;
}
function ChartPreview({value}:{value:unknown}){
  const spec=chartSpec(value);const host=React.useRef<HTMLDivElement>(null);const rendered=React.useRef<VegaResult|null>(null);const [error,setError]=React.useState('');
  React.useEffect(()=>{if(!spec||!host.current)return;let active=true;embed(host.current,spec,{actions:false,renderer:'svg',theme:'latimes'}).then(result=>{if(active)rendered.current=result}).catch(reason=>{if(active)setError(reason instanceof Error?reason.message:String(reason))});return()=>{active=false;rendered.current?.finalize();rendered.current=null}},[spec]);
  if(!spec)return null;
  const save=(content:string,type:string,name:string)=>{const url=URL.createObjectURL(new Blob([content],{type}));const link=document.createElement('a');link.href=url;link.download=name;link.click();URL.revokeObjectURL(url)};
  async function download(format:'svg'|'png'|'json'|'csv'){
    if(format==='json')return save(JSON.stringify(spec,null,2),'application/json','agent-chart.vega-lite.json');
    if(format==='csv'){
      const rows=Array.isArray(spec?.data?.values)?spec.data.values as Record<string,unknown>[]:[];const columns=[...new Set(rows.flatMap(row=>Object.keys(row)))];
      const quote=(cell:unknown)=>`"${String(cell??'').replaceAll('"','""')}"`;const csv=[columns.map(quote).join(','),...rows.map(row=>columns.map(column=>quote(row[column])).join(','))].join('\n');
      return save(csv,'text/csv;charset=utf-8','agent-chart-data.csv');
    }
    if(!rendered.current)return;if(format==='svg')return save(await rendered.current.view.toSVG(),'image/svg+xml','agent-chart.svg');const url=(await rendered.current.view.toCanvas(2)).toDataURL('image/png');const link=document.createElement('a');link.href=url;link.download='agent-chart.png';link.click();
  }
  return <div className="chart-preview advanced"><div className="chart-actions"><b>{String(spec.title||'Generated chart')}</b><span><button onClick={()=>void download('png')}>Download PNG</button><button onClick={()=>void download('svg')}>Download SVG</button><button onClick={()=>void download('csv')}>Download CSV</button><button onClick={()=>void download('json')}>Download spec</button></span></div>{error?<p className="notice error">Chart could not render: {error}</p>:<div className="vega-chart" ref={host}/>}</div>;
}
function ResponseBlock({block}:{block:Record<string,unknown>}){const type=String(block.type||'');const content=block.content as any;
  if(type==='markdown')return <div className="response-markdown"><ReactMarkdown remarkPlugins={[remarkGfm]}>{String(content||'')}</ReactMarkdown></div>;
  if(type==='chart')return <ChartPreview value={content?.spec}/>;
  if(type==='image')return <figure className="response-image"><img src={String(content?.url||content?.src||'')} alt={String(content?.alt||content?.title||'Agent-generated image')}/>{(content?.caption||content?.title)&&<figcaption>{String(content.caption||content.title)}</figcaption>}</figure>;
  if(type==='table'){const columns=Array.isArray(content?.columns)?content.columns:[];const rows=Array.isArray(content?.rows)?content.rows:[];return <div className="response-table"><table><thead><tr>{columns.map((column:string)=><th key={column}>{column}</th>)}</tr></thead><tbody>{rows.map((row:any,index:number)=><tr key={index}>{columns.map((column:string)=><td key={column}>{String(row?.[column]??'')}</td>)}</tr>)}</tbody></table></div>}
  if(type==='code')return <pre className="response-code"><code>{String(content?.code||content||'')}</code></pre>;
  if(type==='file')return <a className="response-file" href={String(content?.url||'#')} download={content?.filename}>Download {String(content?.filename||content?.label||'file')}</a>;
  if(type==='notice')return <aside className={`response-notice ${String(content?.level||'info')}`}>{String(content?.text||content||'')}</aside>;
  return null;
}
function ProgressEvent({event}:{event:RunEvent}){const progress=event.attributes?.progress as Record<string,unknown>|undefined;if(!progress)return <><b>{event.type}</b><time>{new Date(event.occurredAt).toLocaleTimeString()}</time><pre>{Object.keys(event.attributes||{}).length?JSON.stringify(event.attributes,null,2):''}</pre></>;const telemetry={...event.attributes};delete telemetry.progress;return <><b>{String(progress.title)}</b><time>{new Date(event.occurredAt).toLocaleTimeString()}</time><p>{String(progress.summary)}</p>{Object.keys(telemetry).length>0&&<details><summary>Technical details</summary><pre>{JSON.stringify(telemetry,null,2)}</pre></details>}</>}
function RichResponse({output,text}:{output?:Record<string,unknown>;text:string}){const response=output?.response as Record<string,unknown>|undefined;const blocks=Array.isArray(response?.blocks)?response.blocks as Record<string,unknown>[]:[];if(blocks.length)return <div className="rich-response">{blocks.map((block,index)=><ResponseBlock key={String(block.id||index)} block={block}/>)}</div>;const specs=output?chartSpecs(output):[];return <div className="rich-response"><div className="response-markdown"><ReactMarkdown remarkPlugins={[remarkGfm]}>{text}</ReactMarkdown></div>{specs.length>0&&<div className="chart-gallery">{specs.map((spec,index)=><ChartPreview key={`${String(spec.title||'chart')}-${index}`} value={spec}/>)}</div>}</div>}

export default function PlaygroundPanel({agent,tenantId,subjectId,close,onCatalogChanged}:{agent:Agent;tenantId:string;subjectId:string;close:()=>void;onCatalogChanged?:()=>void}) {
  const headers={'Content-Type':'application/json','X-Tenant-Id':tenantId,'X-User-Id':subjectId};
  const isWebsite=agent.id==='website-reader';
  const isDatabase=agent.id==='database-reader';
  const taskOnly=agent.interactionMode==='TASK';
  const [prompt,setPrompt]=React.useState('');
  const [messages,setMessages]=React.useState<Message[]>([]);
  const [run,setRun]=React.useState<Run|null>(null);
  const [events,setEvents]=React.useState<RunEvent[]>([]);
  const [streamState,setStreamState]=React.useState<'connecting'|'live'|'retrying'>('connecting');
  const [busy,setBusy]=React.useState(false);
  const [failure,setFailure]=React.useState('');
  const runIdRef=React.useRef('');

  React.useEffect(()=>{
    const source=new EventSource(`/api/v1/runs/stream?tenantId=${encodeURIComponent(tenantId)}`);
    source.onopen=()=>setStreamState('live');
    source.onerror=()=>setStreamState('retrying');
    source.addEventListener('run',event=>{
      const next=(JSON.parse((event as MessageEvent).data) as {run:Run}).run;
      if(next.runId===runIdRef.current)setRun(next);
    });
    source.addEventListener('run-event',event=>{
      const next=JSON.parse((event as MessageEvent).data) as RunEvent;
      if(next.runId===runIdRef.current)setEvents(current=>current.some(item=>item.type===next.type&&item.occurredAt===next.occurredAt)?current:[...current,next]);
    });
    return()=>source.close();
  },[tenantId]);

  React.useEffect(()=>{
    if(!run||!['COMPLETED','FAILED','CANCELLED'].includes(run.status))return;
    if(run.status==='COMPLETED'){
      const answer=responseText(run.output,String(run.output.answer||run.output.message||JSON.stringify(run.output,null,2)));
      setMessages(current=>current.at(-1)?.role==='assistant'?current:[...current,{role:'assistant',text:answer,output:run.output}]);
      if(run.output.createdAgentId)onCatalogChanged?.();
    }
  },[run,onCatalogChanged]);

  async function execute(){
    const message=prompt.trim();
    if(!message)return;
    setBusy(true);setFailure('');setEvents([]);setRun(null);
    try{
      const input={message,conversation:messages};
      setMessages(current=>[...current,{role:'user',text:message}]);
      setPrompt('');
      const response=await fetch('/api/v1/runs',{method:'POST',headers,body:JSON.stringify({agentId:agent.id,input,subjectId,scopes:['agents:invoke'],async:true})});
      if(!response.ok)throw new Error(`${response.status} ${await response.text()}`);
      const started=await response.json() as Run;
      runIdRef.current=started.runId;
      setRun(started);
      const prior=await fetch(`/api/v1/runs/${started.runId}/events`,{headers});
      if(prior.ok)setEvents(await prior.json());
      const latest=await fetch(`/api/v1/runs/${started.runId}`,{headers});
      if(latest.ok)setRun(await latest.json());
    }catch(error){setFailure(error instanceof Error?error.message:String(error))}
    finally{setBusy(false)}
  }

  async function stop(){
    if(!run)return;
    const response=await fetch(`/api/v1/runs/${run.runId}/cancel`,{method:'POST',headers});
    if(!response.ok&&response.status!==409)setFailure(`${response.status} ${await response.text()}`);
  }

  async function closeConsole(){
    if(run&&['CREATED','RUNNING','WAITING','WAITING_APPROVAL'].includes(run.status)){
      try{await fetch(`/api/v1/runs/${run.runId}/cancel`,{method:'POST',headers})}catch{/* backend terminal cleanup is also idempotent */}
    }
    close();
  }

  const active=run&&['CREATED','RUNNING','WAITING','WAITING_APPROVAL'].includes(run.status);const isCreator=agent.id==='agent-creator';
  return <section className="panel playground console workspace-console">
    <div className="panel-title"><div><p className="eyebrow">AGENT WORKSPACE</p><h2>{agent.displayName}</h2><code>{agent.id} · active version</code></div><button aria-label="Close workspace and stop active run" onClick={()=>void closeConsole()}>Close workspace</button></div>
    <div className="console-toolbar">
      <strong>{taskOnly?'Run this task agent':'Chat with this agent'}{agent.topology==='MULTI_AGENT'?' · multi-agent system':''}</strong>
      <span className={`stream-state ${streamState}`}><i/>{streamState==='live'?'Live events':streamState}</span>
      {run&&<span className={`status ${run.status.toLowerCase()}`}>{run.status}</span>}
    </div>

    <div className="chat-shell"><div className="chat-messages">{messages.length===0?<div className="chat-empty">{isCreator?'Try: Create a chat agent called Sales SQL Assistant that reads PostgreSQL and uses SQL safety.':isWebsite?'Try: Read https://example.com and summarize the page.':isDatabase?'Try: Which electronics products are available?':taskOnly?'Describe the task and include every input the agent needs.':'Describe what you want the agent to do, including any inputs it needs.'}</div>:messages.map((message,index)=><article className={`chat-message ${message.role} ${message.output?'rich':''}`} key={index}><b>{message.role==='user'?'You':agent.displayName}</b>{message.output?<RichResponse output={message.output} text={message.text}/>:<p>{message.text}</p>}</article>)}</div></div>
    <div className="chat-composer"><textarea aria-label={taskOnly?'Task input':'Message agent'} placeholder={isWebsite?'Include the website URL and your question…':isDatabase?'Ask about products or include a category…':taskOnly?'Describe the task and all required inputs…':'Message the agent…'} value={prompt} onChange={event=>setPrompt(event.target.value)} onKeyDown={event=>{if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();void execute()}}}/><button className="primary" disabled={busy||!!active||!prompt.trim()} onClick={execute}>{busy?'Starting…':taskOnly?'Run':'Send'}</button><button className="danger" disabled={!active} onClick={stop}>Stop</button></div>

    {failure&&<div className="notice error">{failure}</div>}

    <div className="console-output">
      <section><div className="panel-title"><h3>Execution progress</h3><small>{events.length} events</small></div><div className="event-log">{events.length===0?<p>No events yet.</p>:events.map((event,index)=><article key={`${event.occurredAt}-${index}`}><i className={event.type.includes('failed')?'failed':''}/><div><ProgressEvent event={event}/></div></article>)}</div></section>
      <section><div className="panel-title"><h3>Response details</h3>{run&&<code>{run.runId.slice(0,8)}</code>}</div>{!run?<p className="muted">Run the agent to see its response.</p>:run.status==='FAILED'?<div className="agent-error"><b>Run failed</b><p>{run.error||'Unknown runtime error'}</p></div>:run.status==='CANCELLED'?<div className="agent-error"><b>Run cancelled</b></div>:run.status==='COMPLETED'?<details><summary>View structured execution output</summary><pre>{JSON.stringify(run.output,null,2)}</pre></details>:<p className="muted">Waiting for the agent…</p>}</section>
    </div>
  </section>
}
