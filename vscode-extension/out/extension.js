"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.activate = activate;
exports.deactivate = deactivate;
const vscode = __importStar(require("vscode"));
const cp = __importStar(require("child_process"));
const path = __importStar(require("path"));
const fs = __importStar(require("fs"));
class XRayProvider {
    emitter = new vscode.EventEmitter();
    onDidChangeTreeData = this.emitter.event;
    root = [];
    refresh(items) { this.root = items; this.emitter.fire(); }
    getTreeItem(item) { return item; }
    getChildren() { return this.root; }
}
class XRayState {
    analysis;
    emitter = new vscode.EventEmitter();
    onDidChange = this.emitter.event;
    set(a) { this.analysis = a; this.emitter.fire(); }
    get() { return this.analysis; }
}
class XRayTreeProvider {
    state;
    constructor(state) {
        this.state = state;
    }
    emitter = new vscode.EventEmitter();
    onDidChangeTreeData = this.emitter.event;
    refresh() { this.emitter.fire(); }
    getTreeItem(e) { return e; }
    getChildren() {
        const a = this.state.get();
        if (!a)
            return [new vscode.TreeItem('Run Analyze Project')];
        const h = a.codeHealthRadar;
        const items = [
            item(`Health ${Math.round(h.score)}/100`, 'projectXray.openHealthRadar'),
            item(`${a.entities.length} symbols · ${a.relations.length} relations`, 'projectXray.openReport'),
            item(`${a.endpoints.length} Spring endpoints`, 'projectXray.openReport'),
            item(`${a.gitTimeMachine.commits.length} Git commits loaded`, 'projectXray.openTimeMachine'),
            item('Open Persistent Code Index', 'projectXray.openIndex'),
            item('Open Dependency Invalidation Plan', 'projectXray.openInvalidationPlan'),
            item('Open Persistent Symbol Store', 'projectXray.openSymbolStore'),
            item('Resolve Symbol from Persistent Store', 'projectXray.resolveSymbol'),
            item('Open Compiler Context', 'projectXray.openCompilerContext'),
            item('Open Dependency Galaxy', 'projectXray.openReport')
        ];
        return items;
    }
}
function item(label, command) {
    const x = new vscode.TreeItem(label);
    x.command = { command, title: label };
    x.iconPath = new vscode.ThemeIcon(command.includes('Health') ? 'pulse' : command.includes('Time') ? 'history' : 'symbol-misc');
    return x;
}
function coreJarPath(context) {
    const configured = vscode.workspace.getConfiguration('projectXray').get('coreJarPath');
    if (configured)
        return configured;
    return path.resolve(context.extensionPath, '..', 'core', 'target', 'xray-core-1.0.0.jar');
}
async function runAnalysis(context, folder) {
    const jar = coreJarPath(context);
    if (!fs.existsSync(jar))
        throw new Error(`Project X-Ray core JAR not found at ${jar}. Build the core with Maven first or configure projectXray.coreJarPath.`);
    const xrayDir = path.join(folder.uri.fsPath, '.xray');
    const output = path.join(xrayDir, 'analysis.json');
    fs.mkdirSync(xrayDir, { recursive: true });
    return await new Promise((resolve, reject) => {
        const child = cp.spawn('java', ['-jar', jar, folder.uri.fsPath, output], { cwd: folder.uri.fsPath });
        let stderr = '';
        child.stderr.on('data', (d) => stderr += d.toString());
        child.on('error', reject);
        child.on('close', (code) => {
            if (code !== 0) {
                reject(new Error(stderr.trim() || `X-Ray core exited with code ${code}`));
                return;
            }
            try {
                resolve(JSON.parse(fs.readFileSync(output, 'utf8')));
            }
            catch (e) {
                reject(new Error(`Analysis completed but result could not be read: ${String(e)}`));
            }
        });
    });
}
async function runImpact(context, root, entityId) {
    const jar = coreJarPath(context);
    if (!fs.existsSync(jar))
        throw new Error(`Project X-Ray core JAR not found at ${jar}. Build the core with Maven first or configure projectXray.coreJarPath.`);
    return await new Promise((resolve, reject) => {
        const child = cp.spawn('java', ['-jar', jar, root, '--impact', entityId], { cwd: root });
        let stdout = '';
        let stderr = '';
        child.stdout.on('data', (d) => stdout += d.toString());
        child.stderr.on('data', (d) => stderr += d.toString());
        child.on('error', reject);
        child.on('close', (code) => {
            if (code !== 0) {
                reject(new Error(stderr.trim() || `Impact analysis exited with code ${code}`));
                return;
            }
            try {
                resolve(JSON.parse(stdout));
            }
            catch (e) {
                reject(new Error(`Impact analysis completed but its result could not be read: ${String(e)}`));
            }
        });
    });
}
function openVisualization(context, data) {
    const panel = vscode.window.createWebviewPanel('projectXray.galaxy', `X-Ray · ${data.project}`, vscode.ViewColumn.One, { enableScripts: true, retainContextWhenHidden: true });
    const safe = JSON.stringify(data).replace(/</g, '\\u003c').replace(/>/g, '\\u003e').replace(/&/g, '\\u0026');
    panel.webview.html = getGalaxyHtml(safe);
    panel.webview.onDidReceiveMessage(async (message) => {
        if (message.type === 'requestImpact') {
            try {
                const impact = await runImpact(context, data.rootPath, message.entityId);
                panel.webview.postMessage({ type: 'impactResult', impact });
            }
            catch (e) {
                panel.webview.postMessage({ type: 'impactError', message: String(e) });
            }
            return;
        }
        if (message.type === 'openFile') {
            const file = vscode.Uri.file(path.join(data.rootPath, message.file));
            try {
                const doc = await vscode.workspace.openTextDocument(file);
                const editor = await vscode.window.showTextDocument(doc, vscode.ViewColumn.Two);
                if (message.line > 0) {
                    const line = Math.max(0, message.line - 1);
                    editor.selection = new vscode.Selection(line, 0, line, 0);
                    editor.revealRange(new vscode.Range(line, 0, line, 0), vscode.TextEditorRevealType.InCenter);
                }
            }
            catch (e) {
                vscode.window.showErrorMessage(`Project X-Ray: cannot open ${message.file}: ${String(e)}`);
            }
        }
    });
}
function getGalaxyHtml(serialized) {
    return `<!doctype html><html><head><meta charset="UTF-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
<style>
:root{color-scheme:dark}*{box-sizing:border-box}
body{margin:0;background:#040711;color:#e8f1fb;font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;overflow:hidden}
.top{height:72px;padding:13px 18px;border-bottom:1px solid #16263a;background:#07101c;display:flex;align-items:center;justify-content:space-between}
.brand{display:flex;align-items:center;gap:10px}.mark{width:34px;height:34px;border:1px solid #55c7ff;border-radius:10px;display:grid;place-items:center;font-weight:800;color:#8ddcff;box-shadow:0 0 18px #163e5b}
.title{font-weight:750;font-size:15px}.meta{font-size:9px;color:#7187a5;margin-top:2px}
.stats{display:flex;gap:6px}.pill{font-size:9px;padding:6px 9px;border:1px solid #1b3149;background:#0a1725;border-radius:999px;color:#a9bed5}
.layout{height:calc(100vh - 72px);display:grid;grid-template-columns:1fr 330px}
.canvas{position:relative;background:radial-gradient(circle at 50% 48%,#102946 0,#071222 34%,#040711 75%)}
svg{width:100%;height:100%}.side{border-left:1px solid #17283d;background:#07101a;padding:13px;overflow:auto}
.section{font-size:8px;letter-spacing:.16em;text-transform:uppercase;color:#627792;margin:3px 0 7px}
.card{background:#0a1522;border:1px solid #172c43;border-radius:11px;padding:10px;margin-bottom:10px}
.row{display:flex;justify-content:space-between;gap:10px;padding:6px 0;border-bottom:1px solid #132337;font-size:10px}.row:last-child{border:0}
.big{font-size:20px;font-weight:750}.muted{font-size:9px;color:#7186a2;line-height:1.5}.good{color:#85dfbb}.warn{color:#f0b36e}.danger{color:#ef8e9a}
.search{width:100%;padding:8px 9px;background:#07111e;border:1px solid #1a334e;border-radius:8px;color:#dce9f7;outline:none;font-size:10px;margin-bottom:7px}
.btnrow{display:flex;gap:5px;margin-bottom:7px}.btn{flex:1;padding:7px;border:1px solid #203a55;background:#0b1a2a;color:#b8cbe0;border-radius:8px;font-size:9px;cursor:pointer}.btn.active{border-color:#53c4ff;color:#e6f7ff;background:#0e263b}
.node{cursor:pointer}.node circle{fill:#091521;stroke:#52718f;stroke-width:1.3}.node text{fill:#dce9f8;font-size:9px;pointer-events:none}
.pkg circle{stroke:#58c6ff;filter:drop-shadow(0 0 5px #1b5675)}.method circle{stroke:#9d8cff}
.edge{stroke:#4a6681;opacity:.5}.edge.strong{stroke:#6cbfe7;opacity:.75}.edge.calls{stroke:#9c8cf5}.edge.injects{stroke:#64d8b6}.edge.imports{stroke:#6e89a7;stroke-dasharray:4 4}
.edge.cycle{stroke:#e88491;stroke-width:2;opacity:.9}
.badge{display:inline-block;padding:3px 5px;border-radius:6px;border:1px solid #203c59;background:#0c1d2e;font-size:8px;color:#a9c1d8}
.toolbar{position:absolute;top:12px;left:12px;z-index:2;display:flex;gap:5px}.toolbar .btn{flex:none;background:#091522dd;backdrop-filter:blur(9px)}
.legend{font-size:8px;color:#7e94ad;line-height:1.75}.legend b{color:#c9d8e9}
</style></head><body>
<div class="top"><div class="brand"><div class="mark">X</div><div><div class="title">PROJECT X-RAY · DEPENDENCY GALAXY</div><div class="meta" id="project"></div></div></div>
<div class="stats"><span class="pill" id="files"></span><span class="pill" id="nodes"></span><span class="pill" id="edges"></span><span class="pill" id="depth"></span><span class="pill" id="cycles"></span><span class="pill" id="health"></span></div></div>
<div class="layout"><div class="canvas"><div class="toolbar"><button class="btn active" id="galaxyBtn">Galaxy</button><button class="btn" id="packageBtn">Packages</button><button class="btn" id="focusBtn">Focus</button><button class="btn" id="fitBtn">Recenter</button><button class="btn" id="timeBtn">Time Machine</button></div><svg id="graph" viewBox="0 0 1100 760" preserveAspectRatio="xMidYMid meet"></svg></div>
<aside class="side">
<div class="section">Live repository</div><div class="card"><div class="row"><span>Scan</span><b id="scan"></b></div><div class="row"><span>Cache</span><b id="cache"></b></div><div class="row"><span>Build</span><b id="build"></b></div><div class="row"><span>Git branch</span><b id="branch"></b></div><div class="row"><span>Working tree</span><b id="dirty"></b></div><div class="row"><span>Analysis</span><b id="duration"></b></div></div>
<div class="section">Galaxy controls</div><div class="card"><input class="search" id="search" placeholder="Search actual symbol or package…"><div class="btnrow"><button class="btn active" id="all">All</button><button class="btn" id="controllers">Controllers</button><button class="btn" id="services">Services</button><button class="btn" id="repos">Repositories</button></div><div class="row"><span>Real nodes</span><b id="ncount"></b></div><div class="row"><span>Real edges</span><b id="ecount"></b></div><div class="row"><span>Detected cycles</span><b id="ccount"></b></div></div>
<div class="section">Selected node</div><div class="card" id="detail"><div class="muted">Select a real node to inspect its source-backed dependencies and dependents.</div></div>
<div class="section">Warnings</div><div class="card" id="warnings"></div>
<div class="section">Code Health Radar</div><div class="card">
<div class="big" id="healthScore"></div><div class="muted" id="healthState"></div>
<div id="healthMetrics" style="margin-top:7px"></div>
<div id="healthFindings" style="margin-top:7px"></div>
</div>
<div class="section">Code Time Machine</div><div class="card">
<div class="muted" id="tmStatus"></div>
<div class="row"><span>Commits loaded</span><b id="tmCommits"></b></div>
<div class="row"><span>Historical snapshots</span><b id="tmSnapshots"></b></div>
<div class="row"><span>Current diff</span><b id="tmDiff"></b></div>
<div id="timeline" style="margin-top:8px;max-height:190px;overflow:auto"></div>
</div>
<div class="section">Visual semantics</div><div class="card legend"><b>Node size</b> = relationship degree. <br><b>Edge thickness</b> = aggregated real evidence count. <br><b>Blue</b> = general dependency. <b>Purple</b> = calls. <b>Mint</b> = injection. <b>Red</b> = detected cycle.<br><br>Nodes and edges are generated only from the current repository's analysis model.</div>
</aside></div>
<script>
const data=${serialized};
const G=data.dependencyGalaxy;const svg=document.getElementById('graph');const NS='http://www.w3.org/2000/svg';const vscode=acquireVsCodeApi();
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
document.getElementById('project').textContent=data.project+' · schema '+data.schemaVersion+' · '+new Date(data.analyzedAt).toLocaleTimeString();
document.getElementById('files').textContent=data.filesScanned+' files';document.getElementById('cache').textContent=(data.warnings||[]).some((w:string)=>w.includes('semantic no-op'))?'semantic reuse':((data.warnings||[]).some((w:string)=>w.includes('partial semantic re-analysis'))?'partial semantic':((data.warnings||[]).some((w:string)=>w.includes('Incremental cache reused'))?'reused':'fresh'));document.getElementById('nodes').textContent=G.nodes.length+' nodes';document.getElementById('edges').textContent=G.edges.length+' edges';document.getElementById('depth').textContent='depth '+G.maxDepth;document.getElementById('cycles').textContent=G.cycles.length+' cycles';document.getElementById('health').textContent='health '+Math.round(data.codeHealthRadar.score);
document.getElementById('build').textContent=data.build.system;document.getElementById('branch').textContent=data.git.repository?(data.git.branch||'repository'):'none';document.getElementById('dirty').textContent=data.git.repository?(data.git.dirty?'modified':'clean'):'n/a';document.getElementById('duration').textContent=data.durationMs+' ms';
const warnings=document.getElementById('warnings');warnings.innerHTML=data.warnings.length?data.warnings.slice(0,12).map(w=>'<div class="row"><span>'+esc(w)+'</span></div>').join(''):'<div class="muted good">No analyzer warnings.</div>';
const HR=data.codeHealthRadar||{score:0,metrics:[],findings:[],warnings:[]};
document.getElementById('healthScore').textContent=Math.round(HR.score)+'/100';
document.getElementById('healthState').textContent=HR.score>=80?'Healthy static profile':HR.score>=60?'Watch areas of structural risk':'High structural risk detected';
document.getElementById('healthMetrics').innerHTML=HR.metrics.filter(m=>m.id!=='overall-score').slice(0,8).map(m=>'<div class="row"><span>'+esc(m.name)+'</span><b>'+esc(String(m.value))+' '+esc(m.unit)+'</b></div>').join('');
document.getElementById('healthFindings').innerHTML=HR.findings.length?HR.findings.slice(0,6).map(f=>'<div class="row" style="display:block"><b>'+esc(f.severity.toUpperCase())+'</b> · '+esc(f.title)+'<div class="muted">'+esc(f.evidence)+'</div></div>').join(''):'<div class="muted good">No structural findings.</div>';
const TM=data.gitTimeMachine||{repository:false,commits:[],snapshots:[],currentDiff:{addedEntities:[],removedEntities:[],changedFiles:[],addedPackages:[],removedPackages:[],relationDelta:0,endpointDelta:0,cycleDelta:0},warnings:[]};
document.getElementById('tmCommits').textContent=TM.commits.length;
document.getElementById('tmSnapshots').textContent=TM.snapshots.length;
document.getElementById('tmStatus').textContent=TM.repository?'Real Git history reconstructed from commits.':'This project is not a Git repository.';
document.getElementById('tmDiff').textContent=TM.repository?(TM.currentDiff.changedFiles.length+' changed files'):'—';
const timeline=document.getElementById('timeline');
timeline.innerHTML=TM.commits.map((c,i)=>'<div class="row" style="display:block;cursor:pointer" data-hash="'+esc(c.hash)+'"><b>'+esc(c.shortHash)+'</b> · '+esc(c.subject)+'<div class="muted">'+esc(c.author)+' · '+esc(c.authoredAt)+' · '+c.changedFiles+' files</div></div>').join('');
timeline.querySelectorAll('[data-hash]').forEach(el=>el.addEventListener('click',()=>{
  const hash=el.getAttribute('data-hash');const snap=TM.snapshots.find(x=>x.commitHash===hash);
  if(!snap)return;
  document.getElementById('detail').innerHTML='<div style="font-weight:750;margin-bottom:8px">Historical snapshot</div>'+
    '<div class="row"><span>Commit</span><b>'+esc(hash.slice(0,10))+'</b></div>'+
    '<div class="row"><span>Files</span><b>'+snap.filesScanned+'</b></div>'+
    '<div class="row"><span>Entities</span><b>'+snap.entities+'</b></div>'+
    '<div class="row"><span>Relations</span><b>'+snap.relations+'</b></div>'+
    '<div class="row"><span>Endpoints</span><b>'+snap.endpoints+'</b></div>'+
    '<div class="row"><span>Packages</span><b>'+snap.packages+'</b></div>'+
    '<div class="row"><span>Cycles</span><b>'+snap.cycles+'</b></div>'+
    '<div class="muted" style="margin-top:7px">'+esc(snap.subject)+'</div>';
}));
let mode='galaxy',filter='all',query='',selected=null,focus=false;
function roleFilter(n){if(filter==='controllers')return n.frameworkRole==='controller';if(filter==='services')return n.frameworkRole==='service';if(filter==='repos')return n.frameworkRole==='repository';return true}
function visible(){return G.nodes.filter(n=>roleFilter(n)&&(n.name.toLowerCase().includes(query.toLowerCase())||n.qualifiedName.toLowerCase().includes(query.toLowerCase()))).slice(0,350)}
function edgeClass(kind){if(kind==='calls')return 'calls';if(kind==='injects')return 'injects';if(kind==='imports')return 'imports';return 'strong'}
function draw(){
  while(svg.firstChild)svg.removeChild(svg.firstChild);
  const nodes=visible(), W=1100,H=760,cx=W/2,cy=H/2;
  const pos=new Map();
  const packages=[...new Set(nodes.map(n=>n.packageName||'<default>'))].sort();
  const pkgCenters=new Map();
  packages.forEach((pkg,i)=>{const a=i/Math.max(1,packages.length)*Math.PI*2-Math.PI/2;pkgCenters.set(pkg,{x:cx+Math.cos(a)*250,y:cy+Math.sin(a)*190})});
  const byPkg=new Map();nodes.forEach(n=>{if(!byPkg.has(n.packageName))byPkg.set(n.packageName,[]);byPkg.get(n.packageName).push(n)});
  for(const [pkg,arr] of byPkg){const c=pkgCenters.get(pkg);arr.forEach((n,i)=>{const a=i/Math.max(1,arr.length)*Math.PI*2-Math.PI/2;const r=Math.min(105,28+arr.length*7);pos.set(n.id,{x:c.x+Math.cos(a)*r,y:c.y+Math.sin(a)*r})})}
  const cycleSet=new Set();G.cycles.forEach(c=>{for(let i=0;i<c.length;i++){cycleSet.add(c[i]+'>'+c[(i+1)%c.length])}});
  let edges=G.edges.filter(e=>pos.has(e.sourceId)&&pos.has(e.targetId));
  if(focus&&selected)edges=edges.filter(e=>e.sourceId===selected.id||e.targetId===selected.id);
  edges.forEach(e=>{const a=pos.get(e.sourceId),b=pos.get(e.targetId);const l=document.createElementNS(NS,'line');l.setAttribute('x1',a.x);l.setAttribute('y1',a.y);l.setAttribute('x2',b.x);l.setAttribute('y2',b.y);l.setAttribute('class','edge '+edgeClass(e.kind)+(cycleSet.has(e.sourceId+'>'+e.targetId)?' cycle':''));l.setAttribute('stroke-width',String(Math.min(4,1+Math.log2(1+e.evidenceCount))));svg.appendChild(l)});
  // Package halos are derived from real package names; no synthetic package nodes enter the data model.
  for(const [pkg,arr] of byPkg){const c=pkgCenters.get(pkg);const halo=document.createElementNS(NS,'circle');halo.setAttribute('cx',c.x);halo.setAttribute('cy',c.y);halo.setAttribute('r',String(Math.min(120,38+arr.length*6)));halo.setAttribute('fill','none');halo.setAttribute('stroke','#193b58');halo.setAttribute('stroke-dasharray','2 6');halo.setAttribute('opacity','.55');svg.appendChild(halo);const t=document.createElementNS(NS,'text');t.setAttribute('x',c.x);t.setAttribute('y',c.y-42);t.setAttribute('text-anchor','middle');t.setAttribute('fill','#66809d');t.setAttribute('font-size','8');t.textContent=pkg;svg.appendChild(t)}
  nodes.forEach(n=>{const p=pos.get(n.id);if(!p)return;const g=document.createElementNS(NS,'g');g.setAttribute('class','node '+(n.kind==='method'?'method':'pkg'));g.addEventListener('click',()=>selectNode(n));const c=document.createElementNS(NS,'circle');const degree=n.inboundRelations+n.outboundRelations;const r=Math.min(25,9+Math.sqrt(degree+1)*3.2);c.setAttribute('cx',p.x);c.setAttribute('cy',p.y);c.setAttribute('r',r);g.appendChild(c);const t=document.createElementNS(NS,'text');t.setAttribute('x',p.x);t.setAttribute('y',p.y+r+12);t.setAttribute('text-anchor','middle');t.textContent=n.name.length>22?n.name.slice(0,20)+'…':n.name;g.appendChild(t);svg.appendChild(g)});
  const title=document.createElementNS(NS,'text');title.setAttribute('x','18');title.setAttribute('y','738');title.setAttribute('fill','#58718e');title.setAttribute('font-size','9');title.textContent='Source-backed Dependency Galaxy · '+nodes.length+' visible real entities · '+edges.length+' visible real relationships';svg.appendChild(title);
  document.getElementById('ncount').textContent=nodes.length;document.getElementById('ecount').textContent=edges.length;document.getElementById('ccount').textContent=G.cycles.length;
}
function selectNode(n){
  selected=n;
  const rel=G.edges.filter(e=>e.sourceId===n.id||e.targetId===n.id);
  const inbound=rel.filter(e=>e.targetId===n.id),outbound=rel.filter(e=>e.sourceId===n.id);
  document.getElementById('detail').innerHTML='<div style="font-weight:750;margin-bottom:8px">'+esc(n.name)+'</div>'+
    '<div class="row"><span>Kind</span><b>'+esc(n.kind)+'</b></div>'+
    '<div class="row"><span>Role</span><b>'+esc(n.frameworkRole||'—')+'</b></div>'+
    '<div class="row"><span>Package</span><b>'+esc(n.packageName||'—')+'</b></div>'+
    '<div class="row"><span>Inbound</span><b>'+inbound.length+'</b></div>'+
    '<div class="row"><span>Outbound</span><b>'+outbound.length+'</b></div>'+
    '<div class="muted" style="margin-top:8px">'+esc(n.file)+':'+n.line+'</div>'+
    '<button class="btn" id="open" style="width:100%;margin-top:8px">Open actual source</button>'+
    '<button class="btn" id="impact" style="width:100%;margin-top:6px">Analyze real change impact</button>'+
    '<div id="impactResult" style="margin-top:8px"></div>';
  document.getElementById('open').onclick=()=>vscode.postMessage({type:'openFile',file:n.file,line:n.line});
  document.getElementById('impact').onclick=()=>{document.getElementById('impact').textContent='Analyzing real graph…';vscode.postMessage({type:'requestImpact',entityId:n.id})};
  draw();
}
window.addEventListener('message',event=>{
  const m=event.data;
  if(m.type==='impactError'){const el=document.getElementById('impactResult');if(el)el.innerHTML='<div class="muted danger">'+esc(m.message)+'</div>';return}
  if(m.type==='impactResult'){
    const x=m.impact;const el=document.getElementById('impactResult');if(!el)return;
    el.innerHTML='<div class="section" style="margin-top:8px">Potential impact</div>'+
      '<div class="row"><span>Direct dependents</span><b>'+x.directDependents+'</b></div>'+
      '<div class="row"><span>Transitive dependents</span><b>'+x.transitiveDependents+'</b></div>'+
      '<div class="row"><span>Direct dependencies</span><b>'+x.directDependencies+'</b></div>'+
      '<div class="row"><span>Transitive dependencies</span><b>'+x.transitiveDependencies+'</b></div>'+
      '<div class="muted" style="margin-top:7px">'+(x.affectedEndpoints.length?'Affected endpoints: '+x.affectedEndpoints.slice(0,8).map(esc).join(', '):'No affected endpoints were found in the analyzed graph.')+'</div>'+
      '<div class="muted" style="margin-top:5px">'+(x.affectedTests.length?'Affected tests: '+x.affectedTests.slice(0,8).map(esc).join(', '):'No test entities were identified in the reachable impact set.')+'</div>'+
      '<div class="muted warn" style="margin-top:7px">'+x.warnings.map(esc).join('<br>')+'</div>';
  }
});
document.getElementById('search').addEventListener('input',e=>{query=e.target.value;draw()});
function filterBtn(id,v){['all','controllers','services','repos'].forEach(x=>document.getElementById(x).classList.remove('active'));document.getElementById(id).classList.add('active');filter=v;draw()}
document.getElementById('all').onclick=()=>filterBtn('all','all');document.getElementById('controllers').onclick=()=>filterBtn('controllers','controllers');document.getElementById('services').onclick=()=>filterBtn('services','services');document.getElementById('repos').onclick=()=>filterBtn('repos','repos');
document.getElementById('galaxyBtn').onclick=()=>{mode='galaxy';document.getElementById('galaxyBtn').classList.add('active');document.getElementById('packageBtn').classList.remove('active');draw()};
document.getElementById('packageBtn').onclick=()=>{mode='packages';document.getElementById('packageBtn').classList.add('active');document.getElementById('galaxyBtn').classList.remove('active');draw()};
document.getElementById('focusBtn').onclick=()=>{focus=!focus;document.getElementById('focusBtn').classList.toggle('active',focus);draw()};
document.getElementById('fitBtn').onclick=()=>draw();
document.getElementById('timeBtn').onclick=()=>{
  document.getElementById('timeline').scrollIntoView({behavior:'smooth',block:'nearest'});
  const d=TM.currentDiff;
  document.getElementById('detail').innerHTML='<div style="font-weight:750;margin-bottom:8px">HEAD vs previous commit</div>'+
    '<div class="row"><span>Changed files</span><b>'+d.changedFiles.length+'</b></div>'+
    '<div class="row"><span>Entities added</span><b>'+d.addedEntities.length+'</b></div>'+
    '<div class="row"><span>Entities removed</span><b>'+d.removedEntities.length+'</b></div>'+
    '<div class="row"><span>Relation delta</span><b>'+d.relationDelta+'</b></div>'+
    '<div class="row"><span>Endpoint delta</span><b>'+d.endpointDelta+'</b></div>'+
    '<div class="row"><span>Cycle delta</span><b>'+d.cycleDelta+'</b></div>'+
    '<div class="muted" style="margin-top:7px">'+(d.addedPackages.length?'Added packages: '+d.addedPackages.slice(0,8).map(esc).join(', '):'No added packages detected.')+'</div>'+
    '<div class="muted" style="margin-top:5px">'+(d.removedPackages.length?'Removed packages: '+d.removedPackages.slice(0,8).map(esc).join(', '):'No removed packages detected.')+'</div>';
};
draw();
</script></body></html>`;
}
function activate(context) {
    const state = new XRayState();
    const provider = new XRayTreeProvider(state);
    context.subscriptions.push(vscode.window.registerTreeDataProvider('projectXray.explorer', provider));
    const status = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
    status.text = '$(pulse) X-Ray: idle';
    status.show();
    context.subscriptions.push(status);
    let timer;
    const analyze = vscode.commands.registerCommand('projectXray.analyzeProject', async () => {
        const folder = vscode.workspace.workspaceFolders?.[0];
        if (!folder) {
            vscode.window.showErrorMessage('Project X-Ray: open a project folder first.');
            return;
        }
        await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: 'Project X-Ray · Repository analysis', cancellable: false }, async (progress) => {
            status.text = '$(sync~spin) X-Ray: scanning repository…';
            progress.report({ message: 'Scanning source files and resolving symbols…' });
            try {
                const d = await runAnalysis(context, folder);
                state.set(d);
                provider.refresh();
                progress.report({ message: `Resolved ${d.entities.length} symbols · ${d.relations.length} relationships` });
                status.text = `$(pulse) X-Ray: ${d.entities.length} symbols · health ${Math.round(d.codeHealthRadar.score)}/100`;
                openVisualization(context, d);
            }
            catch (e) {
                status.text = '$(error) X-Ray: error';
                vscode.window.showErrorMessage(`Project X-Ray: ${String(e)}`);
            }
        });
    });
    const refresh = vscode.commands.registerCommand('projectXray.refresh', () => {
        provider.refresh();
    });
    const openCompilerContext = vscode.commands.registerCommand('projectXray.openCompilerContext', async () => {
        const folder = vscode.workspace.workspaceFolders?.[0];
        if (!folder) {
            vscode.window.showErrorMessage('Project X-Ray: open a workspace folder first.');
            return;
        }
        const uri = vscode.Uri.file(vscode.Uri.joinPath(folder.uri, '.xray', 'compiler-context.json').fsPath);
        try {
            const doc = await vscode.workspace.openTextDocument(uri);
            await vscode.window.showTextDocument(doc, { preview: true });
        }
        catch {
            vscode.window.showInformationMessage('Project X-Ray: compiler context not generated yet. Run Analyze Project first.');
        }
    });
    const resolveSymbol = vscode.commands.registerCommand('projectXray.resolveSymbol', async () => {
        const folder = vscode.workspace.workspaceFolders?.[0];
        if (!folder) {
            vscode.window.showErrorMessage('Project X-Ray: open a workspace folder first.');
            return;
        }
        const query = await vscode.window.showInputBox({ prompt: 'Symbol ID, qualified name, or unambiguous simple name' });
        if (!query)
            return;
        const uri = vscode.Uri.file(vscode.Uri.joinPath(folder.uri, '.xray', 'symbol-resolution.json').fsPath);
        const storeUri = vscode.Uri.file(vscode.Uri.joinPath(folder.uri, '.xray', 'symbol-store.json').fsPath);
        try {
            const bytes = await vscode.workspace.fs.readFile(storeUri);
            const text = new TextDecoder().decode(bytes);
            const data = JSON.parse(text);
            const symbols = data.symbols || [];
            let matches = symbols.filter((x) => x.id === query);
            let strategy = 'symbol-id';
            if (matches.length === 0) {
                matches = symbols.filter((x) => x.qualifiedName === query);
                strategy = 'qualified-name';
            }
            if (matches.length === 0) {
                matches = symbols.filter((x) => x.name === query);
                strategy = 'simple-name';
            }
            const result = { query, resolved: matches.length === 1, symbol: matches.length === 1 ? matches[0] : null, candidates: matches, strategy };
            await vscode.workspace.fs.writeFile(uri, new TextEncoder().encode(JSON.stringify(result, null, 2)));
            const doc = await vscode.workspace.openTextDocument(uri);
            await vscode.window.showTextDocument(doc, { preview: true });
        }
        catch {
            vscode.window.showInformationMessage('Project X-Ray: no persistent symbol store yet. Run Analyze Project first.');
        }
    });
    const openSymbolStore = vscode.commands.registerCommand('projectXray.openSymbolStore', async () => {
        const folder = vscode.workspace.workspaceFolders?.[0];
        if (!folder) {
            vscode.window.showErrorMessage('Project X-Ray: open a workspace folder first.');
            return;
        }
        const uri = vscode.Uri.file(vscode.Uri.joinPath(folder.uri, '.xray', 'symbol-store.json').fsPath);
        try {
            const doc = await vscode.workspace.openTextDocument(uri);
            await vscode.window.showTextDocument(doc, { preview: true });
        }
        catch {
            vscode.window.showInformationMessage('Project X-Ray: no persistent symbol store yet. Run Analyze Project first.');
        }
    });
    const openInvalidationPlan = vscode.commands.registerCommand('projectXray.openInvalidationPlan', async () => {
        const folder = vscode.workspace.workspaceFolders?.[0];
        if (!folder) {
            vscode.window.showErrorMessage('Project X-Ray: open a workspace folder first.');
            return;
        }
        const uri = vscode.Uri.file(vscode.Uri.joinPath(folder.uri, '.xray', 'invalidation-plan.json').fsPath);
        try {
            const doc = await vscode.workspace.openTextDocument(uri);
            await vscode.window.showTextDocument(doc, { preview: true });
        }
        catch {
            vscode.window.showInformationMessage('Project X-Ray: no invalidation plan yet. Run Analyze Project first.');
        }
    });
    const openIndex = vscode.commands.registerCommand('projectXray.openIndex', async () => {
        const folder = vscode.workspace.workspaceFolders?.[0];
        if (!folder) {
            vscode.window.showErrorMessage('Project X-Ray: open a workspace folder first.');
            return;
        }
        const uri = vscode.Uri.file(vscode.Uri.joinPath(folder.uri, '.xray', 'code-index.json').fsPath);
        try {
            const doc = await vscode.workspace.openTextDocument(uri);
            await vscode.window.showTextDocument(doc, { preview: true });
        }
        catch {
            vscode.window.showInformationMessage('Project X-Ray: no persistent index yet. Run Analyze Project first.');
        }
    });
    const openReport = vscode.commands.registerCommand('projectXray.openReport', async () => {
        const f = vscode.workspace.workspaceFolders?.[0];
        if (!f)
            return;
        const report = vscode.Uri.file(path.join(f.uri.fsPath, '.xray', 'analysis.json'));
        try {
            await vscode.window.showTextDocument(report);
        }
        catch {
            vscode.window.showWarningMessage('Project X-Ray: no analysis exists yet. Run Analyze Project first.');
        }
    });
    const openTimeMachine = vscode.commands.registerCommand('projectXray.openTimeMachine', async () => {
        const folder = vscode.workspace.workspaceFolders?.[0];
        if (!folder) {
            vscode.window.showErrorMessage('Project X-Ray: open a project folder first.');
            return;
        }
        status.text = '$(history) X-Ray: reconstructing Git history…';
        try {
            const d = await runAnalysis(context, folder);
            state.set(d);
            provider.refresh();
            if (!d.gitTimeMachine.repository) {
                vscode.window.showWarningMessage('Project X-Ray: the current project is not a Git repository.');
                status.text = '$(history) X-Ray: no Git repository';
                return;
            }
            status.text = `$(history) X-Ray: ${d.gitTimeMachine.snapshots.length} historical snapshots`;
            openVisualization(context, d);
        }
        catch (e) {
            status.text = '$(error) X-Ray: history error';
            vscode.window.showErrorMessage(`Project X-Ray: ${String(e)}`);
        }
    });
    const openHealthRadar = vscode.commands.registerCommand('projectXray.openHealthRadar', async () => {
        const folder = vscode.workspace.workspaceFolders?.[0];
        if (!folder) {
            vscode.window.showErrorMessage('Project X-Ray: open a project folder first.');
            return;
        }
        status.text = '$(pulse) X-Ray: calculating Code Health Radar…';
        try {
            const d = await runAnalysis(context, folder);
            state.set(d);
            provider.refresh();
            status.text = `$(pulse) X-Ray: health ${Math.round(d.codeHealthRadar.score)}/100`;
            openVisualization(context, d);
        }
        catch (e) {
            status.text = '$(error) X-Ray: health error';
            vscode.window.showErrorMessage(`Project X-Ray: ${String(e)}`);
        }
    });
    const watcher = vscode.workspace.onDidSaveTextDocument((doc) => {
        if (!vscode.workspace.getConfiguration('projectXray').get('analyzeOnSave', true))
            return;
        if (!doc.fileName.endsWith('.java'))
            return;
        if (timer)
            clearTimeout(timer);
        timer = setTimeout(() => vscode.commands.executeCommand('projectXray.analyzeProject'), 1200);
    });
    context.subscriptions.push(analyze, refresh, openIndex, openInvalidationPlan, openSymbolStore, resolveSymbol, openCompilerContext, openReport, openTimeMachine, openHealthRadar, watcher);
}
function deactivate() { }
//# sourceMappingURL=extension.js.map