/* くるりヘア — Offline QTVR-style object viewer */
(() => {
  'use strict';
  const C=HairCore, app=document.getElementById('app'), modals=document.getElementById('modal-root');
  const native=typeof window.Android!=='undefined';
  const icons={
    plus:'<path d="M12 5v14M5 12h14"/>',camera:'<path d="M4 7h4l2-3h4l2 3h4v13H4z"/><circle cx="12" cy="13" r="4"/>',
    back:'<path d="m14 5-7 7 7 7"/>',next:'<path d="m9 5 7 7-7 7"/>',down:'<path d="m6 9 6 6 6-6"/>',up:'<path d="m6 15 6-6 6 6"/>',
    settings:'<path d="m9 3-1 3-3 1-2 4 2 3 1 3 4 2 3-2 3-1 2-4-2-3-1-3-4-2z"/><circle cx="11" cy="11" r="3"/>',
    album:'<rect x="4" y="3" width="16" height="18" rx="3"/><path d="m6 16 4-4 3 3 2-2 3 3"/><circle cx="14" cy="8" r="1"/>',
    help:'<circle cx="12" cy="12" r="9"/><path d="M9.5 9a2.5 2.5 0 1 1 3.8 2.1c-1.1.7-1.3 1.1-1.3 2.4M12 17h.01"/>',
    edit:'<path d="m16 3 5 5-12 12-6 1 1-6zM13 6l5 5"/>',more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    close:'<path d="m6 6 12 12M6 18 18 6"/>',download:'<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>',upload:'<path d="M12 16V4m-5 5 5-5 5 5M4 16v5h16v-5"/>',
    play:'<path d="m9 5 11 7-11 7z"/>',video:'<rect x="3" y="5" width="12" height="14" rx="2"/><path d="m15 10 6-4v12l-6-4z"/>',pause:'<path d="M8 5v14M16 5v14"/>',zoom:'<circle cx="10" cy="10" r="6"/><path d="m15 15 6 6M10 7v6M7 10h6"/>',
    check:'<path d="m5 12 4 4L19 6"/>',shield:'<path d="m12 3 9 4v6c0 5-9 9-9 9s-9-4-9-9V7z"/><path d="m7 12 3 3 6-6"/>',
    rotate:'<path d="M20 9a8 8 0 1 0 0 7M20 3v6h-6"/>',align:'<path d="M12 3v18M3 12h18M5 5h3M5 5v3M19 5h-3M19 5v3M5 19h3M5 19v-3M19 19h-3M19 19v-3"/>',
    trash:'<path d="M4 6h16M9 3h6M7 6l1 15h8l1-15M10 10v7M14 10v7"/>',search:'<circle cx="10" cy="10" r="6"/><path d="m15 15 6 6"/>',star:'<path d="m12 3 3 6 6 1-4 5 1 6-6-3-6 3 1-6-4-5 6-1z"/>'
  };
  function icon(name,size=22){return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name]||icons.star}</svg>`;}
  function esc(s){return String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
  function today(){const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
  function dateLabel(d){if(!d)return '';const a=d.split('-');return a.length===3?`${a[0]}.${a[1]}.${a[2]}`:d;}
  function headArt(azimuth=0,elevation=0){
    const a=azimuth*Math.PI/180, side=Math.sin(a), back=Math.cos(a)<-.35;
    const hair='#73628e',skin='#f3d5bc';
    if(elevation===90)return `<svg viewBox="0 0 140 145" xmlns="http://www.w3.org/2000/svg"><ellipse cx="70" cy="124" rx="43" ry="8" fill="#cfc3dc" opacity=".3"/><ellipse cx="70" cy="64" rx="43" ry="49" fill="${hair}"/><path d="M70 51c20-16 24 22 2 26-22 4-28-31-9-39 18-8 38 4 41 20M70 51c-13 2-16 14-8 21" fill="none" stroke="#9480aa" stroke-width="3" stroke-linecap="round"/><path d="m31 83 8 25M109 83l-8 25" stroke="${hair}" stroke-width="12" stroke-linecap="round"/></svg>`;
    const shift=side*12;
    return `<svg viewBox="0 0 140 145" xmlns="http://www.w3.org/2000/svg"><ellipse cx="70" cy="132" rx="51" ry="8" fill="#cfc3dc" opacity=".25"/><path d="M33 133q2-29 37-29t37 29" fill="#c2d8cf"/><path d="M59 97h22v19q-11 11-22 0" fill="${skin}"/><path d="M32 85C21 39 38 13 69 13c38 0 54 30 39 77l-5 28H36z" fill="${hair}"/><ellipse cx="${70+shift}" cy="70" rx="${back?32:33-Math.abs(side)*5}" ry="${43-elevation/5}" fill="${back?hair:skin}"/>${back?'<path d="M44 34q23-9 46 0M41 44q29-10 58 0M38 60q30-7 63 0" fill="none" stroke="#8b789f" stroke-width="2"/>':`<path d="M35 60q2-38 34-35 13 26 38 32-21 0-40-20-6 23-32 23" fill="${hair}"/><ellipse cx="${56+shift}" cy="72" rx="2" ry="3" fill="#53445e"/><ellipse cx="${83+shift}" cy="72" rx="2" ry="3" fill="#53445e"/><path d="M${62+shift} 88q7 6 14 0" fill="none" stroke="#b78478" stroke-width="2" stroke-linecap="round"/><ellipse cx="${51+shift}" cy="83" rx="5" ry="3" fill="#edb7b1" opacity=".55"/><ellipse cx="${88+shift}" cy="83" rx="5" ry="3" fill="#edb7b1" opacity=".55"/>`}</svg>`;
  }
  function orbit(slot,p='standard'){
    const v=C.plan(p),ang=(slot?.azimuth||0)*Math.PI/180, r=42,x=62-Math.sin(ang)*r,y=62+Math.cos(ang)*r;
    return `<svg viewBox="0 0 124 134" xmlns="http://www.w3.org/2000/svg"><circle cx="62" cy="62" r="42" fill="none" stroke="#ddd3ed" stroke-width="1.5" stroke-dasharray="3 5"/><ellipse cx="62" cy="62" rx="19" ry="22" fill="#f2d5bc"/><path d="M43 62q-5-25 19-25t19 25q-8-2-19-15-5 12-19 15" fill="#89759f"/><path d="m54 75 8 8 8-8" stroke="#b796c5" stroke-width="2" fill="none"/>${Array.from({length:v.columns},(_,i)=>{const a=i*2*Math.PI/v.columns;return `<circle cx="${62-Math.sin(a)*r}" cy="${62+Math.cos(a)*r}" r="3" fill="#d7cce5"/>`;}).join('')}<circle cx="${slot?.key==='top'?62:x}" cy="${slot?.key==='top'?62:y}" r="10" fill="#8275bf"/><circle cx="${slot?.key==='top'?62:x}" cy="${slot?.key==='top'?62:y}" r="4" fill="white"/><text x="62" y="125" text-anchor="middle" fill="#9483a7" font-size="10">上から見た撮影位置</text></svg>`;
  }
  let db={schema:1,sessions:[]}, route={name:'home'}, filter='',query='',view={key:null,zoom:1,panX:0,panY:0,rememberCol:0},autoTimer=null,loadFailed=false;
  const history=[];let toastTimer,selectedPlan='standard',demo=null;
  try{
    const raw=native?Android.load():localStorage.getItem('kururi-store');
    if(raw){const data=JSON.parse(raw);if(data.error)throw Error(data.error);db=C.validate(data);}
  }catch(e){loadFailed=true;setTimeout(()=>alert('保存データを読み込めませんでした。書き込みを止めています。バックアップから復元するか、アプリを再起動してください。'),400);}
  function persist(){
    if(loadFailed){toast('データを読み込めていないため保存できません。');return false;}
    try{C.validate(db);if(native){const r=JSON.parse(Android.save(JSON.stringify(db)));if(!r.ok)throw Error(r.error||'保存できませんでした。');}else localStorage.setItem('kururi-store',JSON.stringify(db));return true;}catch(e){toast(e.message||'保存できませんでした。');return false;}
  }
  function session(id=route.id){return id==='demo'?demo:db.sessions.find(x=>x.id===id);}
  function count(s){return Object.keys(s.frames).length;}
  const demoImageCache=new Map();
  function imageFor(s,key){
    const f=s.frames[key];if(!f)return '';
    if(s.demo){if(!demoImageCache.has(key)){const slot=C.direction(key,s.plan);demoImageCache.set(key,'data:image/svg+xml;charset=utf-8,'+encodeURIComponent(headArt(slot.azimuth,slot.elevation)));}return demoImageCache.get(key);}
    if(!native&&f.data)return f.data;
    return `https://kururi.local/photos/${encodeURIComponent(f.file)}?v=${f.updated||s.updated||0}`;
  }
  function transformFor(f){const t=f?.align||{};return {x:Number(t.x)||0,y:Number(t.y)||0,scale:Number(t.scale)||1,rotation:Number(t.rotation)||0};}
  function displayTransform(f,zoom=1,px=0,py=0){const t=transformFor(f);return `translate(${t.x+px}%,${t.y+py}%) scale(${t.scale*zoom}) rotate(${t.rotation}deg)`;}
  function cover(s){const slot=C.nearest(s,0,0);return slot?`<img loading="lazy" src="${imageFor(s,slot.key)}" alt="${esc(s.title)}">`:headArt();}
  function toast(t){const el=document.getElementById('toast');el.textContent=t;el.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('show'),3200);}
  function busy(message){stopAuto();closeModal();const div=document.createElement('div');div.id='busy';div.className='busy-overlay';div.innerHTML=`<div class="spinner"></div><div>${esc(message)}</div>`;document.body.appendChild(div);}
  function unbusy(){document.getElementById('busy')?.remove();}
  function go(name,id,replace=false){stopAuto();closeModal();if(!replace)history.push({...route});route={name,id};if(name==='viewer'){const s=session(id);view={key:C.nearest(s,0,0)?.key||null,zoom:1,panX:0,panY:0,rememberCol:0};}render();window.scrollTo(0,0);}
  function back(){if(modals.childElementCount){closeModal();return;}stopAuto();if(history.length){route=history.pop();render();window.scrollTo(0,0);}else if(route.name!=='home'){route={name:'home'};render();}else if(native)Android.exit();}
  window.appBack=back;
  function nav(active){return `<nav class="nav" aria-label="メニュー"><button data-action="nav-home" class="${active==='home'?'active':''}">${icon('album',20)}アルバム</button><button data-action="nav-help" class="${active==='help'?'active':''}">${icon('help',20)}撮り方</button><button data-action="nav-settings" class="${active==='settings'?'active':''}">${icon('settings',20)}設定</button></nav>`;}
  function topbar(title,extra=''){return `<div class="topbar"><button class="icon-btn" data-action="back" aria-label="戻る">${icon('back')}</button><h1>${esc(title)}</h1>${extra||'<span style="width:44px"></span>'}</div>`;}
  function render(){
    if(route.id&&!session()){route={name:'home'};}
    if(route.name==='home')renderHome();else if(route.name==='new'||route.name==='edit')renderForm();else if(route.name==='viewer')renderViewer();else if(route.name==='guide')renderGuide();else if(route.name==='help')renderHelp();else if(route.name==='settings')renderSettings();
  }
  function renderHome(){
    const children=[...new Set(db.sessions.map(x=>x.child))].filter(Boolean);
    const list=db.sessions.filter(s=>(!filter||s.child===filter)&&(!query||(s.child+' '+s.title+' '+s.note).toLowerCase().includes(query.toLowerCase()))).sort((a,b)=>b.date.localeCompare(a.date)||(b.created||0)-(a.created||0));
    app.innerHTML=`<main class="page"><header class="header"><div class="brand"><div class="brand-icon">${icon('rotate',26)}</div><div><div class="brand-title">くるりヘア</div><div class="eyebrow">LITTLE HAIR MEMORIES</div></div></div><button class="icon-btn transparent" data-action="nav-help" aria-label="撮り方">${icon('help')}</button></header><h1 class="title">今日の髪型を、<br>ぐるっと残そう。</h1><p class="subtitle">前も、後ろも、つむじも。かわいい今を記録。</p><div class="hero"><div><h2>写真でつくる<br>くるくるアルバム</h2><p>指で左右・上下に動かすと、<br>撮った角度の写真に切り替わります。</p><div class="mini-pill">${icon('shield',13)}写真はこの端末に保存</div></div><div class="hero-art">${headArt()}<div style="text-align:center;color:#a08daf;font-size:11px;margin-top:-8px">← くるっ →</div></div></div><button class="primary new-btn" data-action="new">${icon('plus')}髪型の記録をつくる</button><div class="section-heading"><h2>髪型アルバム</h2><span class="small">${db.sessions.length}件の記録</span></div>${children.length?`<div class="chips"><button data-action="filter" data-value="" class="chip ${!filter?'active':''}">みんな</button>${children.map(x=>`<button data-action="filter" data-value="${esc(x)}" class="chip ${filter===x?'active':''}">${esc(x)}</button>`).join('')}</div>`:''}${db.sessions.length>4?`<div class="search">${icon('search',18)}<input id="search" aria-label="記録を検索" placeholder="髪型・名前・メモで検索" value="${esc(query)}"></div>`:''}${list.length?`<div class="grid">${list.map(s=>{const n=count(s),total=C.plan(s.plan).count;return `<button class="record" data-action="view" data-id="${s.id}"><div class="record-cover">${cover(s)}<span class="record-badge">${n===total?'ぐるっと完成':`${n} / ${total}方向`}</span></div><div class="record-info"><div class="record-child">${esc(s.child||'お子さん')}</div><h3>${esc(s.title)}</h3><div class="record-footer"><span>${dateLabel(s.date)}</span><span>${n===total?'360°':n?'撮影中':'未撮影'} ${icon('next',12)}</span></div><div class="progress-track"><span style="width:${n/total*100}%"></span></div></div></button>`;}).join('')}</div>`:`<div class="empty">${icon('album',35)}<strong>${db.sessions.length?'該当する記録はありません':'最初の髪型を残してみよう'}</strong>${db.sessions.length?'検索やお子さんの選択を変えてください。':'撮影方向のガイドに沿って、<br>1枚ずつ撮るだけで大丈夫。'}<button class="secondary" data-action="demo">${icon('play',17)}デモで操作を試す</button></div>`}<p class="footer-hint">${list.length?'<button class="text-button" data-action="demo">デモでくるくる操作を試す</button>':'途中で保存して、あとから撮り足せます。'}</p></main>${nav('home')}`;
    const search=document.getElementById('search');if(search)search.addEventListener('input',()=>{const pos=search.selectionStart;query=search.value;renderHome();const el=document.getElementById('search');el.focus();el.setSelectionRange(pos,pos);});
  }
  function renderForm(){
    const edit=route.name==='edit',s=edit?session():null;if(edit)selectedPlan=s.plan;
    app.innerHTML=`<main class="page">${topbar(edit?'記録を編集':'新しい髪型の記録')}<h2 class="title" style="font-size:23px">${edit?'思い出の情報を編集':'どんな髪型を残す？'}</h2><p class="subtitle">名前と日付を入れたら、撮影を始められます。</p><form id="record-form"><div class="panel"><label class="label" for="child">お子さんの名前<span class="required">ニックネームでもOK</span></label><input id="child" maxlength="40" required placeholder="例：りさ" value="${esc(s?.child||filter||'')}" list="child-names" autocomplete="off"><datalist id="child-names">${[...new Set(db.sessions.map(x=>x.child))].map(x=>`<option value="${esc(x)}"></option>`).join('')}</datalist><label class="label" for="title">髪型のタイトル</label><input id="title" maxlength="80" required placeholder="例：はじめてのボブ" value="${esc(s?.title||'')}" autocomplete="off"><label class="label" for="date">記録した日</label><input id="date" type="date" required value="${esc(s?.date||today())}"><label class="label" for="note">メモ<span class="required">任意</span></label><textarea id="note" maxlength="4000" placeholder="前髪の長さ、結び方、美容室など">${esc(s?.note||'')}</textarea></div>${!edit?`<div class="section-heading"><h2>撮影する方向の数</h2></div><div class="plan-list">${Object.entries(C.plans).map(([k,p])=>`<button type="button" class="plan-option ${k===selectedPlan?'selected':''}" data-action="plan" data-value="${k}"><span class="radio"></span><span class="plan-info"><span class="plan-name">${p.name}${k==='standard'?'<span class="tag">おすすめ</span>':''}</span><span class="plan-detail" style="display:block">${p.detail}</span></span><span class="plan-count">${p.count}<small>枚</small></span></button>`).join('')}</div><div class="note-box" style="margin-top:18px">${icon('help',21)}<span>お子さんは正面を向いたまま。<br>撮る人が周囲を移動します。少ない枚数でも見返せます。</span></div>`:`<p class="small">撮影プラン：${C.plan(s.plan).name}（${C.plan(s.plan).count}方向）</p>`}<div class="form-footer"><button class="primary" type="submit">${icon(edit?'check':'camera')}${edit?'変更を保存':'保存して撮影へ'}</button>${!edit?'<button type="button" class="text-button" data-action="save-later">記録だけ作って、撮影はあとで</button>':''}</div></form></main>`;
    document.getElementById('record-form').addEventListener('submit',e=>{e.preventDefault();saveForm(true);});
  }
  function saveForm(shoot){
    const form=document.getElementById('record-form');if(!form.reportValidity())return;
    const child=document.getElementById('child').value.trim(),title=document.getElementById('title').value.trim(),date=document.getElementById('date').value,note=document.getElementById('note').value.trim();
    if(!child||!title){toast('名前と髪型のタイトルを入力してください。');return;}
    const before=JSON.parse(JSON.stringify(db)),editing=route.name==='edit';
    let s=editing?session():{id:'hair-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,9),plan:selectedPlan,frames:{},created:Date.now()};
    Object.assign(s,{child,title,date,note,updated:Date.now()});if(!editing)db.sessions.push(s);
    if(!persist()){db=before;return;}go(editing?'viewer':shoot?'guide':'home',editing||shoot?s.id:undefined,true);if(!shoot&&!editing)toast('記録を保存しました。');
  }
  function renderViewer(){
    const s=session(),n=count(s),p=C.plan(s.plan);if(view.key&&!s.frames[view.key])view.key=C.nearest(s,0,0)?.key||null;
    const at=view.key?C.direction(view.key,s.plan):null;
    app.innerHTML=`<main class="page viewer-page">${topbar(s.demo?'くるくる操作のデモ':s.child+' の髪型',s.demo?'':`<button class="icon-btn" data-action="record-menu" aria-label="記録のメニュー">${icon('more')}</button>`)}${s.demo?'<div class="demo-label">イラストで操作を体験できます。実際の記録では撮影した写真が切り替わります。</div>':''}<div class="viewer-stage" id="viewer-stage" aria-label="写真を左右・上下にドラッグして角度を変更">${at?`<img id="main-photo" src="${imageFor(s,view.key)}" alt="${esc(at.label)}" style="transform:${displayTransform(s.frames[view.key],view.zoom,view.panX,view.panY)}"><div class="angle-badge" id="angle-badge">${esc(at.label)}</div><div class="stage-tools"><button class="icon-btn" data-action="zoom" aria-label="拡大・戻す">${icon('zoom',18)}</button>${!s.demo?`<button class="icon-btn" data-action="align" aria-label="写真の位置合わせ">${icon('align',18)}</button>`:''}</div><div class="stage-help" id="stage-help">${view.zoom>1?'ドラッグで移動 · 拡大ボタンで戻す':'左右でぐるっと · 上下で斜め上・真上'}</div>`:`<div class="stage-placeholder">${headArt()}<strong>写真を撮ると、ここで回せます</strong><span class="small">途中の枚数でも見ることができます。</span></div>`}</div><div class="viewer-direction"><div><strong id="current-label">${esc(at?.label||'まだ写真がありません')}</strong><small id="current-sub">${n} / ${p.count}方向を保存 · ${p.name}</small></div><div class="viewer-controls"><button data-action="prev-frame" aria-label="ひとつ前の角度" ${n?'':'disabled'}>${icon('back',18)}</button><button id="autoplay" data-action="autoplay" aria-label="自動で回す" ${n>1?'':'disabled'}>${icon('play',17)}</button><button data-action="next-frame" aria-label="次の角度" ${n?'':'disabled'}>${icon('next',18)}</button></div></div><div class="row-tabs">${Array.from({length:p.rows+1},(_,r)=>`<button data-action="view-row" data-value="${r}" class="${at?.row===r?'active':''}" ${C.slots(s.plan).some(x=>x.row===r&&s.frames[x.key])?'':'disabled'}>${r===p.rows?'真上':r?'斜め上':'頭の高さ'}</button>`).join('')}</div><div class="thumb-strip" id="thumb-strip">${C.slots(s.plan).filter(x=>x.row===(at?.row||0)).map(slot=>thumb(s,slot)).join('')}</div><div class="record-details"><div><h2>${esc(s.title)}</h2><p>${dateLabel(s.date)}${s.child?' · '+esc(s.child):''}</p>${s.note?`<p style="margin-top:8px">${esc(s.note)}</p>`:''}</div><span class="tag" style="flex:0 0 auto">${n===p.count?'完成':'撮影途中'}</span></div><div class="detail-actions">${s.demo?'<button class="primary" data-action="new">自分の記録をつくる</button>':`<button class="primary" data-action="guide" data-id="${s.id}">${icon('camera',19)}${n===p.count?'写真を確認・撮り直す':'撮影する・撮り足す'}</button><button class="secondary" data-action="export-view">${icon('download',18)}書き出す</button>`}</div><p class="footer-hint">${s.demo?'本番は、角度ごとの写真がそのまま表示されます。':n?'ピンチで拡大できます。拡大中はドラッグで写真を移動。<br>写真がない方向は、撮影済みの方向へ切り替わります。':'撮影画面の案内に沿って、最初の1枚を撮ってみましょう。'}</p></main>`;
    if(at)bindViewer();
  }
  function thumb(s,slot){return `<button class="thumb ${view.key===slot.key?'active':''}" data-action="choose-frame" data-key="${slot.key}" aria-label="${esc(slot.label)}${s.frames[slot.key]?'':' 未撮影'}"><span class="thumb-photo ${s.frames[slot.key]?'':'missing'}">${s.frames[slot.key]?`<img loading="lazy" src="${imageFor(s,slot.key)}" alt="">`:icon('plus',17)}</span>${esc(slot.short)}</button>`;}
  function setFrame(slot){
    if(!slot||!session().frames[slot.key])return;
    const changed=view.key!==slot.key,old=C.direction(view.key,session().plan);view.key=slot.key;
    if(slot.key!=='top')view.rememberCol=slot.col;if(changed){view.zoom=1;view.panX=0;view.panY=0;}
    const s=session(),p=C.plan(s.plan),img=document.getElementById('main-photo');if(!img){renderViewer();return;}
    img.src=imageFor(s,slot.key);img.alt=slot.label;img.style.transform=displayTransform(s.frames[view.key],view.zoom,view.panX,view.panY);
    document.getElementById('angle-badge').textContent=slot.label;document.getElementById('current-label').textContent=slot.label;document.getElementById('stage-help').textContent=view.zoom>1?'ドラッグで移動 · 拡大ボタンで戻す':'左右でぐるっと · 上下で斜め上・真上';
    document.querySelectorAll('.row-tabs button').forEach(x=>x.classList.toggle('active',Number(x.dataset.value)===slot.row));
    if(old?.row!==slot.row)document.getElementById('thumb-strip').innerHTML=C.slots(s.plan).filter(x=>x.row===slot.row).map(x=>thumb(s,x)).join('');
    else document.querySelectorAll('.thumb').forEach(x=>x.classList.toggle('active',x.dataset.key===slot.key));
  }
  function moveHorizontal(d){setFrame(C.nextInRow(session(),view.key,d));}
  function moveVertical(d){setFrame(C.nextRow(session(),view.key,d,view.rememberCol));}
  function updateZoom(){const s=session(),img=document.getElementById('main-photo');if(img)img.style.transform=displayTransform(s.frames[view.key],view.zoom,view.panX,view.panY);const h=document.getElementById('stage-help');if(h)h.textContent=view.zoom>1.02?'ドラッグで移動 · 拡大ボタンで戻す':'左右でぐるっと · 上下で斜め上・真上';}
  function bindViewer(){
    const stage=document.getElementById('viewer-stage'),pointers=new Map();let last=null,dist=0,pinchZoom=1,accX=0,accY=0,tapAt=0;
    stage.addEventListener('pointerdown',e=>{if(e.target.closest('button'))return;stopAuto();stage.setPointerCapture(e.pointerId);pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});last={x:e.clientX,y:e.clientY};accX=accY=0;if(pointers.size===2){const[a,b]=[...pointers.values()];dist=Math.hypot(a.x-b.x,a.y-b.y);pinchZoom=view.zoom;}});
    stage.addEventListener('pointermove',e=>{
      if(!pointers.has(e.pointerId))return;const previous=pointers.get(e.pointerId);pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});
      if(pointers.size>1){const[a,b]=[...pointers.values()];view.zoom=Math.max(1,Math.min(4,pinchZoom*Math.hypot(a.x-b.x,a.y-b.y)/Math.max(1,dist)));updateZoom();last={x:e.clientX,y:e.clientY};return;}
      const dx=e.clientX-previous.x,dy=e.clientY-previous.y;last={x:e.clientX,y:e.clientY};
      if(view.zoom>1.02){const r=stage.getBoundingClientRect();view.panX=Math.max(-80,Math.min(80,view.panX+dx/r.width*100));view.panY=Math.max(-80,Math.min(80,view.panY+dy/r.height*100));updateZoom();return;}
      accX+=dx;accY+=dy;
      if(Math.abs(accX)>26&&Math.abs(accX)>Math.abs(accY)*1.1){moveHorizontal(accX<0?1:-1);accX=accY=0;}
      else if(Math.abs(accY)>42&&Math.abs(accY)>Math.abs(accX)*1.2){moveVertical(accY<0?1:-1);accX=accY=0;}
    });
    const end=e=>{const wasMulti=pointers.size>1;pointers.delete(e.pointerId);if(pointers.size===1){last=[...pointers.values()][0];accX=accY=0;}if(!pointers.size){if(view.zoom<1.06){view.zoom=1;view.panX=view.panY=0;updateZoom();}last=null;}if(wasMulti)tapAt=0;};
    stage.addEventListener('pointerup',end);stage.addEventListener('pointercancel',end);
    stage.addEventListener('dblclick',e=>{if(e.target.closest('button'))return;view.zoom=view.zoom>1?1:2;view.panX=view.panY=0;updateZoom();});
  }
  function stopAuto(){if(autoTimer){clearInterval(autoTimer);autoTimer=null;}const b=document.getElementById('autoplay');if(b){b.innerHTML=icon('play',17);b.setAttribute('aria-label','自動で回す');}}
  function toggleAuto(){if(autoTimer){stopAuto();return;}if(view.key==='top')setFrame(C.nearest(session(),0,0));view.zoom=1;view.panX=view.panY=0;updateZoom();autoTimer=setInterval(()=>moveHorizontal(1),650);const b=document.getElementById('autoplay');if(b){b.innerHTML=icon('pause',17);b.setAttribute('aria-label','自動回転を停止');}}
  function renderGuide(){
    const s=session(),all=C.slots(s.plan),first=all.find(x=>!s.frames[x.key]),p=C.plan(s.plan);
    app.innerHTML=`<main class="page guide-page">${topbar('撮影する方向を選ぶ')}<div class="guide-head"><div class="guide-orbit">${orbit(first||all[0],s.plan)}</div><div><h2 class="guide-title">${esc(s.child)} の撮影</h2><p class="guide-desc">${count(s)} / ${p.count}方向を保存<br>右・左は、お子さんから見た方向です。</p></div></div><div class="progress-track" style="height:6px;margin-top:15px"><span style="width:${count(s)/p.count*100}%"></span></div><div class="guide-current"><h3>${first?'次に撮る方向：'+esc(first.label):'すべての方向を撮影できました'}</h3><p>${first?esc(C.guidance(first,s.plan)):'写真をタップすると、撮り直しや位置合わせができます。'}</p></div>${Array.from({length:p.rows+1},(_,row)=>`<div class="section-heading"><h2>${row===p.rows?'真上から':row?'少し高い位置から（約35°）':'頭と同じ高さから'}</h2><span class="small">${all.filter(x=>x.row===row&&s.frames[x.key]).length} / ${row===p.rows?1:p.columns}</span></div><div class="direction-grid">${all.filter(x=>x.row===row).map(slot=>`<button class="direction-tile ${s.frames[slot.key]?'done':''} ${first?.key===slot.key?'current':''}" data-action="slot" data-key="${slot.key}">${s.frames[slot.key]?`<img loading="lazy" src="${imageFor(s,slot.key)}" alt=""><span class="done-check">✓</span>`:`<span class="slot-icon">${icon(slot.key==='top'?'down':'camera',24)}</span>`}${esc(slot.label)}</button>`).join('')}</div>`).join('')}<div class="sticky-action"><button class="primary" data-action="capture-remaining" ${first?'':'disabled'}>${icon('camera',19)}${first?'未撮影の方向を順に撮る':'すべて撮影済み'}</button><button class="secondary" data-action="view" data-id="${s.id}">${icon('rotate',18)}見返す</button></div><p class="footer-hint">撮影中も1枚ずつ保存します。いつでも中断できます。</p></main>`;
    document.querySelector('.guide-current').insertAdjacentHTML('afterend',`<div class="panel"><h3 style="font-size:15px;margin:0 0 8px">じっとしていないときは、動画で。</h3><p class="subtitle" style="margin-bottom:12px">撮影・読み込みした動画を止めて、良い場面を各方向の写真にできます。</p><button class="secondary" style="width:100%" data-action="video">${icon('video',19)}動画からまとめて選ぶ</button></div>`);
  }
  function modal(html){stopAuto();modals.innerHTML=`<div class="modal-backdrop"><section class="modal" role="dialog" aria-modal="true">${html}</section></div>`;modals.querySelector('.modal-backdrop').addEventListener('click',e=>{if(e.target.classList.contains('modal-backdrop'))closeModal();});}
  function closeModal(){modals.innerHTML='';}
  function videoModal(key=''){
    const s=session(),slot=key?C.direction(key,s.plan):null;
    modal(`<div class="modal-header"><h2>動画から良い瞬間を選ぶ</h2><button class="modal-close" data-action="modal-close" aria-label="閉じる">${icon('close')}</button></div><p>お子さんが動いても、あとから一時停止して写真を選べます。一本の動画から、複数の方向を保存できます。</p><div class="note-box"><span>正面 → お子さんの右側 → 後ろ → 左側。<br>同じ距離でゆっくり移動し、斜め上・真上も撮ります。</span></div><p>${slot?`最初の保存先：<strong>${esc(slot.label)}</strong><br>`:''}動画を止める → 前後のコマを選ぶ → 方向を選んで保存。撮影方向は自分で指定します。</p><button class="primary wide" data-action="record-video" data-key="${esc(key)}">${icon('video',19)}動画を撮影する</button><button class="secondary wide" data-action="import-video" data-key="${esc(key)}">${icon('album',19)}既存の動画を選ぶ</button><p style="font-size:10px;margin-bottom:0">撮影は60秒を目安に。読み込みは5分・400MBまで。アプリには選んだ写真を保存します。今回録画する動画は切り出し終了後に削除します。読み込む元の動画は変更しません。</p>`);
  }
  function startVideo(record,key=''){
    if(loadFailed){toast('保存データを読み込めていないため動画を選べません。');return;}
    if(!native){toast('動画からの切り出しはAndroidアプリで利用できます。');return;}
    const s=session();busy(record?'動画カメラを開いています…':'動画を読み込んでいます…');
    try{Android[record?'captureVideo':'importVideo'](s.id,JSON.stringify(C.slots(s.plan)),key);}catch(_){unbusy();toast('動画機能を利用できません。アプリを更新してください。');}
  }
  function slotModal(key){
    const s=session(),slot=C.direction(key,s.plan);if(!slot)return;const f=s.frames[key];
    modal(`<div class="modal-header"><h2>${esc(slot.label)}</h2><button class="modal-close" data-action="modal-close" aria-label="閉じる">${icon('close')}</button></div><div style="max-width:110px;margin:0 auto">${orbit(slot,s.plan)}</div><p>${esc(C.guidance(slot,s.plan))}</p>${f?`<img class="photo-preview" src="${imageFor(s,key)}" alt="${esc(slot.label)}">`:''}<button class="primary wide" data-action="capture-one" data-key="${key}">${icon('camera',19)}${f?'この方向を撮り直す':'この方向を撮る'}</button><button class="secondary wide" data-action="import-photo" data-key="${key}">${icon('album',19)}既存の写真から選ぶ</button><button class="secondary wide" data-action="video" data-key="${key}">${icon('video',19)}動画の一場面から選ぶ</button>${f?`<div class="button-row"><button class="text-button" data-action="align-slot" data-key="${key}">位置を合わせる</button><button class="text-button danger" data-action="delete-photo" data-key="${key}">この写真を削除</button></div>`:''}`);
  }
  function camera(keys){
    if(loadFailed){toast('保存データを読み込めていないため撮影できません。');return;}
    const s=session();if(!keys.length)return;closeModal();
    if(native){Android.capture(s.id,JSON.stringify(keys.map(k=>C.direction(k,s.plan))));}
    else browserImport(keys[0]);
  }
  function browserImport(key){
    const s=session(),input=document.createElement('input');input.type='file';input.accept='image/*';input.onchange=()=>{const file=input.files[0];if(!file)return;const r=new FileReader();r.onload=()=>{const before=JSON.parse(JSON.stringify(db));s.frames[key]={file:'browser-'+Date.now()+'.jpg',data:r.result,updated:Date.now(),align:{x:0,y:0,scale:1,rotation:0}};s.updated=Date.now();if(!persist()){db=before;return;}render();toast('写真を保存しました。');};r.readAsDataURL(file);};input.click();
  }
  function renderHelp(){
    app.innerHTML=`<main class="page"><div class="header"><h1 class="title">ぐるっと撮るコツ</h1><div class="brand-icon">${icon('camera',24)}</div></div><div class="panel"><div style="display:flex;align-items:center;gap:15px"><div style="width:105px;flex:0 0 105px">${orbit({azimuth:45})}</div><div><h2 style="font-size:17px;margin:0 0 7px;line-height:1.6">お子さんはそのまま。<br>撮る人が動こう。</h2><p class="subtitle">右・左はお子さんを基準に案内します。</p></div></div></div><div class="panel"><div class="guide-list"><div class="guide-tip"><div><strong>明るい場所で、座って撮影</strong><span>なるべく同じ明るさ・背景で撮ります。頭の向きと姿勢を保つと、写真を回したときに自然につながります。</span></div></div><div class="guide-tip"><div><strong>正面 → お子さんの右側 → 後ろ → 左側</strong><span>カメラの向きを頭へ向けたまま、同じ距離を保って移動します。撮影画面の点で、今の撮影位置を確認できます。</span></div></div><div class="guide-tip"><div><strong>頭の大きさをガイド枠に合わせる</strong><span>髪の先まで入るようにします。カメラの「前の写真」をオンにすると、前の1枚を薄く重ねて位置を合わせられます。</span></div></div><div class="guide-tip"><div><strong>斜め上を撮り、最後につむじを撮る</strong><span>斜め上はカメラを高くして、約35°見下ろします。真上はつむじを中心に撮ってください。</span></div></div><div class="guide-tip"><div><strong>あとから写真を追加・調整できる</strong><span>方向ごとのマスをタップして撮り直せます。既存写真の読み込みや位置合わせもできます。</span></div></div></div></div><div class="panel"><h2 style="font-size:15px;margin:0 0 13px">見返すときの操作</h2><p class="subtitle">左右にドラッグ：周囲の写真へ<br>上にドラッグ：斜め上 → 真上へ<br>下にドラッグ：頭と同じ高さへ<br>ピンチ：写真を拡大（拡大中は移動）<br>▶ ボタン：自動でゆっくり一周</p></div><button class="secondary" style="width:100%" data-action="demo">${icon('play',18)}デモで操作を試す</button></main>${nav('help')}`;
    document.querySelector('main > [data-action="demo"]').insertAdjacentHTML('beforebegin','<div class="panel"><h2 style="font-size:15px;margin:0 0 10px">じっとしてくれないときは動画で撮る</h2><p class="subtitle">撮影方向を選ぶ画面の「動画からまとめて選ぶ」で撮影または読み込み。動画を止め、スライダー・前後のコマで良い瞬間を選びます。保存先の方向を指定して1枚ずつ保存してください。一本の動画から複数方向を選べます。</p></div>');
  }
  function renderSettings(){
    app.innerHTML=`<main class="page"><div class="header"><h1 class="title">設定とバックアップ</h1><div class="brand-icon">${icon('settings',24)}</div></div><div class="note-box">${icon('shield',23)}<span>写真と記録はこの端末に保存します。<br>アカウント登録やネット接続は不要です。</span></div><div class="panel"><h2 style="font-size:15px;margin:0 0 8px">大切な記録を持ち運ぶ</h2><button class="settings-row" data-action="export-backup"><span><strong>すべての記録をバックアップ</strong><small>写真とメモをZIPファイルで保存</small></span><span class="row-icon">${icon('download')}</span></button><button class="settings-row" data-action="import-backup"><span><strong>バックアップを読み込む</strong><small>このアプリのZIPから、記録を追加・復元</small></span><span class="row-icon">${icon('upload')}</span></button></div><div class="panel"><h2 style="font-size:15px;margin:0 0 10px">写真をくるくる見せるファイル</h2><p class="subtitle">各記録の「書き出す」から、写真を回して見られるHTMLファイルを保存できます。PCのブラウザでも開けるので、美容室での相談や家族への共有に使えます。</p></div><div class="panel"><h2 style="font-size:15px;margin:0 0 10px">保存について</h2><p class="subtitle">現在：${db.sessions.length}件 / ${db.sessions.reduce((a,s)=>a+count(s),0)}枚<br><br>アプリを削除すると、端末内の写真と記録も消えます。機種変更やアンインストールの前に、バックアップを保存してください。<br><br>同じ記録を復元する場合は、そのバックアップの内容に戻ります。別の記録はそのまま残ります。</p></div><p class="version">くるりヘア 1.1.0 · 写真で残す、ぐるっとした思い出。</p></main>${nav('settings')}`;
  }
  function showDemo(){demo={id:'demo',demo:true,plan:'smooth',child:'デモ',title:'くるっとボブのサンプル',date:today(),note:'イラストで左右・上下の操作を体験できます。',frames:{}};for(const slot of C.slots(demo.plan))demo.frames[slot.key]={file:`demo-${slot.key}.jpg`};go('viewer','demo');}
  function alignModal(key){
    const s=session();if(!s.frames[key]||s.demo)return;const slot=C.direction(key,s.plan),initial=transformFor(s.frames[key]),t={...initial};
    modal(`<div class="modal-header"><h2>写真の位置合わせ</h2><button class="modal-close" data-action="modal-close" aria-label="閉じる">${icon('close')}</button></div><p>${esc(slot.label)} · ドラッグで位置を調整できます。</p><div class="align-stage" id="align-stage"><img id="align-photo" src="${imageFor(s,key)}" alt=""><div class="align-guide"></div><div class="align-axis"></div></div><div class="range-line"><span>大きさ</span><input id="align-scale" type="range" min="0.5" max="3" step="0.01" value="${t.scale}" aria-label="写真の大きさ"></div><div class="button-row"><button class="secondary" id="align-rotate">${icon('rotate',16)}90°回転</button><button class="secondary" id="align-reset">元に戻す</button></div><button class="primary wide" id="align-save">この位置で保存</button>`);
    const img=document.getElementById('align-photo'),stage=document.getElementById('align-stage');
    const apply=()=>{img.style.transform=`translate(${t.x}%,${t.y}%) scale(${t.scale}) rotate(${t.rotation}deg)`;document.getElementById('align-scale').value=t.scale;};apply();
    document.getElementById('align-scale').oninput=e=>{t.scale=Number(e.target.value);apply();};document.getElementById('align-rotate').onclick=()=>{t.rotation=(t.rotation+90)%360;apply();};document.getElementById('align-reset').onclick=()=>{Object.assign(t,{x:0,y:0,scale:1,rotation:0});apply();};
    let last=null;stage.onpointerdown=e=>{stage.setPointerCapture(e.pointerId);last={x:e.clientX,y:e.clientY};};stage.onpointermove=e=>{if(!last)return;const r=stage.getBoundingClientRect();t.x=Math.max(-80,Math.min(80,t.x+(e.clientX-last.x)/r.width*100));t.y=Math.max(-80,Math.min(80,t.y+(e.clientY-last.y)/r.height*100));last={x:e.clientX,y:e.clientY};apply();};stage.onpointerup=stage.onpointercancel=()=>last=null;
    document.getElementById('align-save').onclick=()=>{const before=s.frames[key].align;s.frames[key].align={...t};s.updated=Date.now();if(!persist()){s.frames[key].align=before;return;}closeModal();render();toast('写真の位置を保存しました。');};
  }
  function deletePhoto(key){modal(`<h2>この方向の写真を削除しますか？</h2><p>「${esc(C.direction(key,session().plan).label)}」の写真を外します。あとから撮り直すことができます。</p><div class="button-row"><button class="secondary" data-action="modal-close">キャンセル</button><button class="primary" id="confirm-delete-photo">削除する</button></div>`);document.getElementById('confirm-delete-photo').onclick=()=>{const s=session(),f=s.frames[key];delete s.frames[key];s.updated=Date.now();if(!persist()){s.frames[key]=f;return;}closeModal();view.key=C.nearest(s,0,0)?.key;render();toast('写真を削除しました。');};}
  function recordMenu(){const s=session();modal(`<div class="modal-header"><h2>${esc(s.title)}</h2><button class="modal-close" data-action="modal-close" aria-label="閉じる">${icon('close')}</button></div><button class="settings-row" data-action="edit-record"><strong>名前・日付・メモを編集</strong>${icon('edit',19)}</button><button class="settings-row" data-action="guide" data-id="${s.id}"><strong>写真を確認・撮り直す</strong>${icon('camera',19)}</button><button class="settings-row" data-action="export-view"><strong>くるくる見られるHTMLを書き出す</strong>${icon('download',19)}</button><button class="settings-row danger" data-action="delete-record"><strong>この記録を削除する</strong>${icon('trash',19)}</button>`);}
  function confirmDeleteRecord(){const s=session();modal(`<h2>この髪型の記録を削除しますか？</h2><p>「${esc(s.title)}」と、登録した${count(s)}枚の写真を削除します。必要なら先にバックアップを保存してください。</p><div class="button-row"><button class="secondary" data-action="modal-close">キャンセル</button><button class="primary" id="confirm-delete-record">削除する</button></div>`);document.getElementById('confirm-delete-record').onclick=()=>{const old=db.sessions;db.sessions=db.sessions.filter(x=>x.id!==s.id);if(!persist()){db.sessions=old;return;}history.length=0;go('home',undefined,true);toast('記録を削除しました。');};}
  document.addEventListener('click',e=>{
    const b=e.target.closest('[data-action]');if(!b||b.disabled)return;const a=b.dataset.action,k=b.dataset.key,v=b.dataset.value;
    switch(a){
      case 'back':back();break;case 'modal-close':closeModal();break;
      case 'nav-home':filter='';query='';history.length=0;go('home',undefined,true);break;case 'nav-help':history.length=0;go('help',undefined,true);break;case 'nav-settings':history.length=0;go('settings',undefined,true);break;
      case 'new':selectedPlan='standard';go('new');break;case 'plan':selectedPlan=v;document.querySelectorAll('.plan-option').forEach(x=>x.classList.toggle('selected',x.dataset.value===v));break;case 'save-later':saveForm(false);break;
      case 'filter':filter=v;renderHome();break;case 'demo':showDemo();break;case 'view':go('viewer',b.dataset.id);break;case 'guide':go('guide',b.dataset.id||route.id);break;
      case 'prev-frame':stopAuto();moveHorizontal(-1);break;case 'next-frame':stopAuto();moveHorizontal(1);break;case 'view-row':stopAuto();setFrame(C.nearest(session(),Number(v),view.rememberCol));break;case 'autoplay':toggleAuto();break;
      case 'choose-frame':stopAuto();if(session().frames[k])setFrame(C.direction(k,session().plan));else if(!session().demo)slotModal(k);break;
      case 'zoom':stopAuto();view.zoom=view.zoom>1?1:2;view.panX=view.panY=0;updateZoom();break;case 'align':alignModal(view.key);break;case 'align-slot':alignModal(k);break;
      case 'slot':slotModal(k);break;case 'capture-one':camera([k]);break;case 'capture-remaining':camera(C.slots(session().plan).filter(x=>!session().frames[x.key]).map(x=>x.key));break;
      case 'video':videoModal(k);break;case 'record-video':startVideo(true,k);break;case 'import-video':startVideo(false,k);break;
      case 'import-photo':closeModal();if(native)Android.importPhoto(session().id,k);else browserImport(k);break;case 'delete-photo':deletePhoto(k);break;
      case 'record-menu':recordMenu();break;case 'edit-record':go('edit',route.id);break;case 'delete-record':confirmDeleteRecord();break;
      case 'export-backup':if(!native){toast('バックアップはAndroidアプリで利用できます。');break;}if(loadFailed){toast('保存データを読み込めていません。');break;}busy('バックアップを保存しています…');Android.exportBackup();break;
      case 'import-backup':if(!native){toast('復元はAndroidアプリで利用できます。');break;}modal('<h2>バックアップを読み込みます</h2><p>このアプリで保存したZIPを選んでください。同じ記録はバックアップの内容に戻り、別の記録はそのまま残ります。</p><div class="button-row"><button class="secondary" data-action="modal-close">キャンセル</button><button class="primary" id="confirm-import">ZIPを選ぶ</button></div>');document.getElementById('confirm-import').onclick=()=>{busy('バックアップを読み込んでいます…');Android.importBackup();};break;
      case 'export-view':if(!count(session())){toast('写真を1枚以上保存してください。');break;}if(native){busy('写真をまとめています…');Android.exportView(session().id);}else toast('HTML書き出しはAndroidアプリで利用できます。');break;
    }
  });
  window.nativeResult=result=>{
    unbusy();if(typeof result==='string'){try{result=JSON.parse(result);}catch(_){result={ok:false,message:'操作を完了できませんでした。'};}}
    if(result.kind==='cancel'){return;}
    if(result.ok){try{const raw=JSON.parse(Android.load());if(raw.error)throw Error(raw.error);db=C.validate(raw);loadFailed=false;}catch(_){toast('記録を読み込めませんでした。再起動してください。');return;}
      if(route.name==='viewer'&&session()){view.key=C.nearest(session(),0,view.rememberCol)?.key||null;}render();toast(result.message||'保存しました。');
    }else toast(result.message||'操作を完了できませんでした。');
  };
  document.addEventListener('visibilitychange',()=>{if(document.hidden)stopAuto();});
  // Deliberately small public surface for automated interaction checks.
  window.Kururi={getState:()=>({db,route,view}),setFrame,back};
  render();
})();
