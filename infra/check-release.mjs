import {execFileSync} from 'node:child_process';
import {readFileSync,existsSync,readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

// Read-only checks over tracked files and non-ignored untracked files.
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const gitArgs=['-c',`safe.directory=${root.replaceAll('\\','/')}`];
const git=(...args)=>execFileSync('git',[...gitArgs,...args],{cwd:root,encoding:'utf8',stdio:['ignore','pipe','pipe']});
const failures=[];
try {
 const files=git('ls-files','--cached','--others','--exclude-standard','-z').split('\0').filter(Boolean);
 const forbidden=/(^|\/)(node_modules|target|dist|test-results|playwright-report|backups)\//;
 const privateName=/(^|\/)(\.env(?:\.[^/]+)?|\.demo-password|\.woo-credentials\.json|[^/]+\.(?:pem|p12|pfx|key))$/i;
 const tokens=[/AKIA[0-9A-Z]{16}/,/gh[pousr]_[A-Za-z0-9]{36,}/,/github_pat_[A-Za-z0-9_]{50,}/,/sk-proj-[A-Za-z0-9_-]{40,}/,/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/];
 for(const file of files){
  if(forbidden.test(file)||privateName.test(file)&&!file.endsWith('.example'))failures.push(`不应提交的文件：${file}`);
  const absolute=path.resolve(root,file);
  if(!absolute.startsWith(root+path.sep)||!existsSync(absolute))continue;
  if(!/\.(?:java|tsx?|[cm]?js|json|ya?ml|md|ps1|sh|properties|xml|sql|example)$/i.test(file))continue;
  const content=readFileSync(absolute,'utf8');
  if(tokens.some(pattern=>pattern.test(content)))failures.push(`需人工核对凭据格式：${file}（内容不输出）`);
 }
 for(const file of ['apps/web/package-lock.json','infra/maven-settings.xml','.env.example','.github/workflows/verify.yml','docs/IMPLEMENTATION_PLAN.md'])if(!files.includes(file)||!existsSync(path.join(root,file)))failures.push(`缺少交付文件：${file}`);
 const migrations=readdirSync(path.join(root,'services/api/src/main/resources/db/migration')).filter(f=>/^V\d+__.*\.sql$/.test(f));
 const versions=migrations.map(f=>Number(f.match(/^V(\d+)/)[1])).sort((a,b)=>a-b);
 if(new Set(versions).size!==versions.length||versions.some((v,i)=>v!==i+1))failures.push('迁移版本重复或不连续');
 try{git('diff','--check');git('diff','--cached','--check');}catch{failures.push('Git差异包含格式问题，请执行 git diff --check / git diff --cached --check');}
 if(failures.length){for(const message of failures)process.stderr.write(message+'\n');process.exitCode=1;}
 else console.log(`PASS：检查 ${files.length} 个候选文件、${migrations.length} 个连续迁移；未发现所覆盖的私有文件/凭据格式或Git差异格式问题。未暂存、提交或推送。`);
}catch(error){console.error(`检查未完成：${error.code||error.name}；请确认在含Git元数据的项目目录运行，且Git可用。`);process.exitCode=1;}
