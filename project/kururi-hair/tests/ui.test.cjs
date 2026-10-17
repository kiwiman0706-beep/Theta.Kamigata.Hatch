// Real browser interaction checks; Android bridge is mocked, camera hardware is not tested.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const {chromium:browserType}=require(process.env.KURURI_PLAYWRIGHT_MODULE||'playwright');
const chromiumModule=require(process.env.KURURI_CHROMIUM_MODULE||'@sparticuz/chromium');
const chromium=chromiumModule.default||chromiumModule;
const C=require('../app/src/main/assets/core.js');
const assets=path.resolve(__dirname,'../app/src/main/assets');
const qa=path.resolve(__dirname,'../build/qa');fs.mkdirSync(qa,{recursive:true});
const server=http.createServer((req,res)=>{
 const name=req.url==='/'?'index.html':req.url.slice(1);
 if(!/^[a-zA-Z0-9_.-]+$/.test(name)){res.writeHead(404).end();return;}
 try{const bytes=fs.readFileSync(path.join(assets,name));res.setHeader('Content-Type',name.endsWith('.js')?'text/javascript':name.endsWith('.css')?'text/css':'text/html');res.end(bytes);}catch(e){res.writeHead(404).end();}
});
function storedSession(plan='standard',keys=C.slots(plan).map(x=>x.key)){
 return {id:'qa-session',child:'りさ',title:'すっきりボブ',date:'2026-10-03',note:'前髪は眉の上に。',plan,created:1,updated:1,frames:Object.fromEntries(keys.map(k=>[k,{file:'qa-'+k+'.jpg',updated:1,align:{x:0,y:0,scale:1,rotation:0}}]))};
}
async function main(){
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const url='http://127.0.0.1:'+server.address().port+'/';
 const browser=await browserType.launch({executablePath:await chromium.executablePath(),args:chromium.args,headless:true});
 let checks=0;const errors=[];
 try{
 const ctx=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:1,isMobile:true,hasTouch:true});
  await ctx.addInitScript(()=>{
    let db={schema:1,sessions:[]};window.qa={calls:[],getDB:()=>db,setDB:v=>{db=v;}};
    window.Android={load:()=>JSON.stringify(db),save:j=>{db=JSON.parse(j);return '{"ok":true}';},capture:(id,slots)=>qa.calls.push({kind:'capture',id,slots:JSON.parse(slots)}),importPhoto:(id,key)=>qa.calls.push({kind:'photo',id,key}),captureVideo:(id,slots,key)=>qa.calls.push({kind:'video-capture',id,slots:JSON.parse(slots),key}),importVideo:(id,slots,key)=>qa.calls.push({kind:'video-import',id,slots:JSON.parse(slots),key}),exportView:id=>qa.calls.push({kind:'html',id}),exportBackup:()=>qa.calls.push({kind:'backup'}),importBackup:()=>qa.calls.push({kind:'restore'}),exit:()=>qa.calls.push({kind:'exit'})};
  });
  const fontRoot=process.env.KURURI_QA_FONT_DIR;
  await ctx.route('https://kururi.local/**',route=>{
   const u=new URL(route.request().url());
   if(u.pathname.startsWith('/photos/'))return route.fulfill({status:200,contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="300" height="400"><rect width="300" height="400" fill="#ede6f6"/><ellipse cx="150" cy="155" rx="80" ry="103" fill="#806c96"/><ellipse cx="150" cy="180" rx="58" ry="80" fill="#f2d5bc"/><path d="M85 128q45-80 120 0" fill="#806c96"/><text x="150" y="340" text-anchor="middle" font-size="14" fill="#89759d">TEST PHOTO</text></svg>'});
   if(u.pathname.startsWith('/qa-fonts/')&&fontRoot){return route.fulfill({status:200,contentType:'font/woff2',body:fs.readFileSync(path.join(fontRoot,'files',path.basename(u.pathname)))});}
   let body=fs.readFileSync(path.join(assets,u.pathname.slice(1)||'index.html'));
   if(u.pathname.endsWith('.css')&&fontRoot){body=Buffer.concat([body,Buffer.from('\n'+fs.readFileSync(path.join(fontRoot,'index.css'),'utf8').replaceAll('./files/','/qa-fonts/'))]);}
   return route.fulfill({status:200,contentType:u.pathname.endsWith('.js')?'text/javascript':u.pathname.endsWith('.css')?'text/css':'text/html',body});
  });
  const page=await ctx.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.goto('https://kururi.local/index.html');await page.getByRole('button',{name:'髪型の記録をつくる',exact:true}).waitFor();await page.evaluate(()=>document.fonts.ready);checks++;
  assert.equal(await page.locator('body').evaluate(el=>el.scrollWidth<=window.innerWidth),true);checks++;
  await page.screenshot({path:path.join(qa,'home.png'),fullPage:true});
  await page.getByRole('button',{name:'デモで操作を試す'}).click();
  assert.equal(await page.locator('#current-label').textContent(),'正面');checks++;
  await page.getByRole('button',{name:'次の角度',exact:true}).click();assert.match(await page.locator('#current-label').textContent(),/30°/);checks++;
  await page.getByRole('button',{name:'斜め上',exact:true}).click();assert.match(await page.locator('#current-label').textContent(),/斜め上/);checks++;
  await page.getByRole('button',{name:'真上',exact:true}).click();assert.match(await page.locator('#current-label').textContent(),/真上/);checks++;
  await page.getByRole('button',{name:'ひとつ前の角度'}).click();assert.equal(await page.locator('#current-label').textContent(),'左前 30°');checks++;
  await page.screenshot({path:path.join(qa,'demo.png'),fullPage:true});
  await page.getByRole('button',{name:'戻る',exact:true}).click();
  await page.getByRole('button',{name:'髪型の記録をつくる',exact:true}).click();
  await page.locator('#child').fill('りさ');await page.locator('#title').fill('はじめてのボブ');await page.locator('#note').fill('<script>alert(1)</script>');
  await page.getByRole('button',{name:/なめらか/}).click();
  await page.getByRole('button',{name:'記録だけ作って、撮影はあとで',exact:true}).click();
  const saved=await page.evaluate(()=>qa.getDB());assert.equal(saved.sessions.length,1);assert.equal(saved.sessions[0].plan,'smooth');assert.equal(saved.sessions[0].note,'<script>alert(1)</script>');checks+=3;
  await page.locator('.record').click();await page.getByRole('button',{name:'撮影する・撮り足す',exact:true}).click();assert.equal(await page.locator('.direction-tile').count(),25);checks++;
  await page.locator('.direction-tile').first().click();await page.getByRole('button',{name:'この方向を撮る',exact:true}).click();
  let call=await page.evaluate(()=>qa.calls.at(-1));assert.equal(call.kind,'capture');assert.equal(call.slots[0].key,'r0_a0');checks++;
  await page.getByRole('button',{name:'未撮影の方向を順に撮る'}).click();call=await page.evaluate(()=>qa.calls.at(-1));assert.equal(call.slots.length,25);checks++;
  await page.screenshot({path:path.join(qa,'guide.png'),fullPage:true});
  // One video exposes the full direction plan; a slot chooses the initial direction only.
  await page.getByRole('button',{name:'動画からまとめて選ぶ',exact:true}).click();
  await page.getByRole('button',{name:'動画を撮影する',exact:true}).click();
  call=await page.evaluate(()=>qa.calls.at(-1));assert.equal(call.kind,'video-capture');assert.equal(call.slots.length,25);assert.equal(call.key,'');checks+=3;
  assert.equal(await page.locator('#busy').count(),1);checks++;
  await page.evaluate(()=>nativeResult({ok:false,kind:'cancel'}));assert.equal(await page.locator('#busy').count(),0);checks++;
  await page.locator('.direction-tile[data-key="top"]').click();await page.getByRole('button',{name:'動画の一場面から選ぶ',exact:true}).click();
  await page.screenshot({path:path.join(qa,'video-options.png'),fullPage:true});
  await page.getByRole('button',{name:'既存の動画を選ぶ',exact:true}).click();
  call=await page.evaluate(()=>qa.calls.at(-1));assert.equal(call.kind,'video-import');assert.equal(call.key,'top');assert.equal(call.slots.length,25);checks+=3;
  await page.evaluate(()=>nativeResult({ok:false,kind:'video',message:'読み込み中断'}));assert.equal(await page.locator('#busy').count(),0);checks++;
  // Simulate successful native photo capture returning fresh metadata.
  const populated=storedSession();await page.evaluate(record=>{qa.setDB({schema:1,sessions:[record]});window.nativeResult({ok:true,kind:'capture',message:'saved'});},populated);
  await page.locator('.record').click();await page.getByRole('button',{name:'写真を確認・撮り直す',exact:true}).click();
  await page.getByRole('button',{name:'動画からまとめて選ぶ',exact:true}).click();await page.getByRole('button',{name:'既存の動画を選ぶ',exact:true}).click();
  call=await page.evaluate(()=>qa.calls.at(-1));assert.equal(call.slots.length,17);assert.equal(call.id,'qa-session');checks+=2;
  await page.evaluate(()=>nativeResult({ok:true,kind:'video',message:'動画から3枚の写真を保存しました。'}));assert.equal(await page.locator('#busy').count(),0);checks++;
  await page.getByRole('button',{name:'アルバム',exact:true}).count();
  await page.evaluate(()=>{while(Kururi.getState().route.name!=='home')Kururi.back();});
  await page.locator('.record').click();assert.equal(await page.locator('#current-label').textContent(),'正面');checks++;
  await page.waitForFunction(()=>document.getElementById('main-photo').naturalWidth>0);assert.equal(await page.locator('#main-photo').evaluate(i=>i.complete&&i.naturalWidth>0),true);checks++;
  await page.screenshot({path:path.join(qa,'viewer.png'),fullPage:true});
  // Drag left, then up; real PointerEvents exercise the interactive viewer.
  const box=await page.locator('#viewer-stage').boundingBox();
  await page.mouse.move(box.x+box.width*.7,box.y+box.height*.5);await page.mouse.down();await page.mouse.move(box.x+box.width*.45,box.y+box.height*.5,{steps:3});await page.mouse.up();
  assert.notEqual(await page.locator('#current-label').textContent(),'正面');checks++;
  await page.mouse.move(box.x+box.width*.5,box.y+box.height*.6);await page.mouse.down();await page.mouse.move(box.x+box.width*.5,box.y+box.height*.35,{steps:2});await page.mouse.up();
  assert.match(await page.locator('#current-label').textContent(),/斜め上|真上/);checks++;
  await page.getByRole('button',{name:'拡大・戻す'}).click();assert.equal(await page.evaluate(()=>Kururi.getState().view.zoom),2);checks++;
  await page.getByRole('button',{name:'写真の位置合わせ'}).click();await page.locator('#align-scale').fill('1.4');await page.locator('#align-rotate').click();await page.locator('#align-save').click();
  assert.equal(Object.values((await page.evaluate(()=>qa.getDB())).sessions[0].frames).some(f=>f.align.scale===1.4&&f.align.rotation===90),true);checks++;
  await page.getByRole('button',{name:'記録のメニュー'}).click();await page.getByRole('button',{name:'名前・日付・メモを編集'}).click();await page.locator('#title').fill('すっきりボブ 更新');await page.getByRole('button',{name:'変更を保存',exact:true}).click();
  assert.equal((await page.evaluate(()=>qa.getDB())).sessions[0].title,'すっきりボブ 更新');checks++;
  await page.getByRole('button',{name:'書き出す',exact:true}).click();assert.equal((await page.evaluate(()=>qa.calls.at(-1))).kind,'html');checks++;
  await page.evaluate(()=>window.nativeResult({ok:false,kind:'cancel'}));assert.equal(await page.locator('#busy').count(),0);checks++;
  // Sparse one-image record: wrapping and row fallback work without a complete capture.
  const sparse=storedSession('easy',['top']);await page.evaluate(s=>{qa.setDB({schema:1,sessions:[s]});nativeResult({ok:true,kind:'restore'});},sparse);
  assert.match(await page.locator('#current-label').textContent(),/真上/);await page.getByRole('button',{name:'次の角度',exact:true}).click();assert.match(await page.locator('#current-label').textContent(),/真上/);checks+=2;
  // 320px phone, larger phone, portrait and landscape tablet: no horizontal page overflow.
  for(const size of [{width:320,height:640},{width:412,height:915},{width:800,height:1280},{width:1280,height:800}]){
   await page.setViewportSize(size);await page.evaluate(()=>{while(Kururi.getState().route.name!=='home')Kururi.back();});
   assert.equal(await page.locator('body').evaluate(el=>el.scrollWidth<=window.innerWidth),true,JSON.stringify(size));checks++;
   await page.locator('.record').click();await page.getByRole('button',{name:'撮影する・撮り足す',exact:true}).click();await page.getByRole('button',{name:'動画からまとめて選ぶ',exact:true}).click();
   assert.equal(await page.locator('.modal').evaluate(el=>el.scrollWidth<=el.clientWidth),true,'video dialog '+JSON.stringify(size));checks++;
   await page.getByRole('button',{name:'閉じる',exact:true}).click();
  }
  await page.getByRole('button',{name:'動画からまとめて選ぶ',exact:true}).click();await page.getByRole('button',{name:'既存の動画を選ぶ',exact:true}).click();
  assert.equal((await page.evaluate(()=>qa.calls.at(-1))).slots.length,9);checks++;
  await page.evaluate(()=>nativeResult({ok:false,kind:'cancel'}));
  // Offline exported viewer, real script execution and wrapping.
  const ex=storedSession('standard',['r0_a0','r0_a4','r1_a0','top']);for(const f of Object.values(ex.frames)){f.image='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="300" height="400"><rect width="300" height="400" fill="#eee9fa"/></svg>');delete f.file;}
  const exportFile=path.join(qa,'export-test.html');fs.writeFileSync(exportFile,fs.readFileSync(path.join(assets,'export.html'),'utf8').replace('__PAYLOAD__',JSON.stringify(ex).replace(/</g,'\\u003c')));
  const exp=await ctx.newPage();exp.on('pageerror',e=>errors.push(e.message));await exp.goto('file://'+exportFile);
  await exp.locator('#next').click();assert.equal(await exp.locator('#angle').textContent(),'後ろ');await exp.getByRole('button',{name:'真上',exact:true}).click();assert.match(await exp.locator('#angle').textContent(),/真上/);checks+=2;
  assert.deepEqual(errors,[]);checks++;
  const result={checks,passed:true,jsErrors:errors,viewports:['320x640','390x844','412x915','800x1280','1280x800'],scope:'Browser UI, HTML export and mocked photo/video bridges; physical Android camera and video decoding untested'};
  fs.writeFileSync(path.join(qa,'ui-results.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
 }finally{await browser.close();server.close();}
}
main().catch(e=>{console.error(e);server.close();process.exitCode=1;});
