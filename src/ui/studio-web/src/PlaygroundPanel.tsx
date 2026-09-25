import React from 'react';

type Agent = { id:string; displayName:string; interactionMode?:'TASK'|'CHAT'|'TASK_AND_CHAT'; topology?:'SINGLE_AGENT'|'MULTI_AGENT' };
type Run = { runId:string; agentId:string; agentVersion:string; status:string; createdAt:string; output:Record<string,unknown>; error?:string };
type RunEvent = { runId:string; type:string; occurredAt:string; attributes:Record<string,unknown> };
type Message = { role:'user'|'assistant'; text:string };

const tenant='local-development';
const headers={'Content-Type':'application/json','X-Tenant-Id':tenant};

export default function PlaygroundPanel({agent,close,onCatalogChanged}:{agent:Agent;close:()=>void;onCatalogChanged?:()=>void}) {
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
    const source=new EventSource(`/api/v1/runs/stream?tenantId=${encodeURIComponent(tenant)}`);
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
  },[]);

  React.useEffect(()=>{
    if(!run||!['COMPLETED','FAILED','CANCELLED'].includes(run.status))return;
    if(run.status==='COMPLETED'){
      const answer=String(run.output.answer||JSON.stringify(run.output,null,2));
      setMessages(current=>current.at(-1)?.role==='assistant'?current:[...current,{role:'assistant',text:answer}]);
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
      const response=await fetch('/api/v1/runs',{method:'POST',headers,body:JSON.stringify({agentId:agent.id,input,subjectId:'studio-user',scopes:['agents:invoke'],async:true})});
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
  return <div className="backdrop"><section className="modal playground console">
    <div className="panel-title"><div><p className="eyebrow">AGENT CONSOLE</p><h2>{agent.displayName}</h2><code>{agent.id} · active version</code></div><button aria-label="Close and stop active run" onClick={()=>void closeConsole()}>×</button></div>
    <div className="console-toolbar">
      <strong>{taskOnly?'Run this task agent':'Chat with this agent'}{agent.topology==='MULTI_AGENT'?' · multi-agent system':''}</strong>
      <span className={`stream-state ${streamState}`}><i/>{streamState==='live'?'Live events':streamState}</span>
      {run&&<span className={`status ${run.status.toLowerCase()}`}>{run.status}</span>}
    </div>

    <div className="chat-shell"><div className="chat-messages">{messages.length===0?<div className="chat-empty">{isCreator?'Try: Create a chat agent called Sales SQL Assistant that reads PostgreSQL and uses SQL safety.':isWebsite?'Try: Read https://example.com and summarize the page.':isDatabase?'Try: Which electronics products are available?':taskOnly?'Describe the task and include every input the agent needs.':'Describe what you want the agent to do, including any inputs it needs.'}</div>:messages.map((message,index)=><article className={`chat-message ${message.role}`} key={index}><b>{message.role==='user'?'You':agent.displayName}</b><p>{message.text}</p></article>)}</div></div>
    <div className="chat-composer"><textarea aria-label={taskOnly?'Task input':'Message agent'} placeholder={isWebsite?'Include the website URL and your question…':isDatabase?'Ask about products or include a category…':taskOnly?'Describe the task and all required inputs…':'Message the agent…'} value={prompt} onChange={event=>setPrompt(event.target.value)} onKeyDown={event=>{if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();void execute()}}}/><button className="primary" disabled={busy||!!active||!prompt.trim()} onClick={execute}>{busy?'Starting…':taskOnly?'Run':'Send'}</button><button className="danger" disabled={!active} onClick={stop}>Stop</button></div>

    {failure&&<div className="notice error">{failure}</div>}

    <div className="console-output">
      <section><div className="panel-title"><h3>Execution log</h3><small>{events.length} events</small></div><div className="event-log">{events.length===0?<p>No events yet.</p>:events.map((event,index)=><article key={`${event.occurredAt}-${index}`}><i className={event.type.includes('failed')?'failed':''}/><div><b>{event.type}</b><time>{new Date(event.occurredAt).toLocaleTimeString()}</time><pre>{Object.keys(event.attributes||{}).length?JSON.stringify(event.attributes,null,2):''}</pre></div></article>)}</div></section>
      <section><div className="panel-title"><h3>Response</h3>{run&&<code>{run.runId.slice(0,8)}</code>}</div>{!run?<p className="muted">Run the agent to see its structured response.</p>:run.status==='FAILED'?<div className="agent-error"><b>Run failed</b><p>{run.error||'Unknown runtime error'}</p></div>:run.status==='CANCELLED'?<div className="agent-error"><b>Run cancelled</b></div>:run.status==='COMPLETED'?<pre>{JSON.stringify(run.output,null,2)}</pre>:<p className="muted">Waiting for the agent…</p>}</section>
    </div>
  </section></div>
}
