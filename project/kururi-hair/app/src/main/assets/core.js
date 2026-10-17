(function(root) {
  'use strict';
  const dirs8 = ['正面','右ななめ前','右側','右ななめ後ろ','後ろ','左ななめ後ろ','左側','左ななめ前'];
  const dirs12 = ['正面','右前 30°','右前 60°','右側','右後ろ 120°','右後ろ 150°','後ろ','左後ろ 150°','左後ろ 120°','左側','左前 60°','左前 30°'];
  const plans = {
    easy:{name:'かんたん',count:9,columns:8,rows:1,detail:'周囲8枚 ＋ 真上1枚'},
    standard:{name:'ぐるぐる',count:17,columns:8,rows:2,detail:'周囲8枚 ＋ 斜め上8枚 ＋ 真上1枚'},
    smooth:{name:'なめらか',count:25,columns:12,rows:2,detail:'周囲12枚 ＋ 斜め上12枚 ＋ 真上1枚'}
  };
  function plan(p) { return plans[p] || plans.standard; }
  function slots(p) {
    const v=plan(p), ds=v.columns===12?dirs12:dirs8, out=[];
    for(let row=0;row<v.rows;row++) for(let col=0;col<v.columns;col++) out.push({key:`r${row}_a${col}`,row,col,azimuth:col*360/v.columns,elevation:row*35,label:ds[col]+(row?'・斜め上':''),short:ds[col],step:out.length+1});
    out.push({key:'top',row:v.rows,col:0,azimuth:0,elevation:90,label:'真上（つむじ）',short:'真上',step:out.length+1});
    return out;
  }
  function direction(key,p) {return slots(p).find(s=>s.key===key);}
  function wrap(x,n){return ((x%n)+n)%n;}
  function angularDistance(a,b,n){const d=Math.abs(wrap(a,n)-wrap(b,n));return Math.min(d,n-d);}
  function nearest(session,row,col){
    const v=plan(session.plan), all=slots(session.plan).filter(s=>session.frames[s.key]);
    if(!all.length) return null;
    row=Math.max(0,Math.min(v.rows,row)); col=wrap(col,v.columns);
    const exact=all.find(s=>s.row===row && (s.key==='top'||s.col===col));
    if(exact)return exact;
    return all.sort((a,b)=>{
      const score=s=>Math.abs(s.row-row)*v.columns*2+(s.key==='top'?0:angularDistance(s.col,col,v.columns));
      return score(a)-score(b)||a.step-b.step;
    })[0];
  }
  function nextInRow(session,key,delta){
    const at=direction(key,session.plan),v=plan(session.plan);
    if(!at || at.key==='top')return nearest(session,0,delta>0?0:v.columns-1);
    for(let n=1;n<=v.columns;n++){
      const col=wrap(at.col+Math.sign(delta)*n,v.columns),candidate=direction(`r${at.row}_a${col}`,session.plan);
      if(candidate && session.frames[candidate.key]) return candidate;
    }
    return at;
  }
  function nextRow(session,key,delta,rememberCol=0){
    const at=direction(key,session.plan),v=plan(session.plan);if(!at)return null;
    const col=at.key==='top'?rememberCol:at.col;
    for(let row=at.row+Math.sign(delta);row>=0&&row<=v.rows;row+=Math.sign(delta)){
      const candidates=slots(session.plan).filter(s=>s.row===row&&session.frames[s.key]);
      if(candidates.length) return nearest(session,row,col);
    }
    return at;
  }
  function guidance(slot,p){
    if(slot.key==='top')return 'お子さんの頭の真上から、つむじを中央に。お子さんは正面を向いたままでOKです。';
    const named=slot.col===0?'お子さんの正面':slot.col===plan(p).columns/2?'お子さんの真後ろ':`お子さんの${slot.short}`;
    return `${named}へ移動してください。${slot.row?'カメラを少し高くして、約35°見下ろします。':'カメラを頭と同じ高さにします。'}頭全体を枠に合わせてください。`;
  }
  function validate(raw){
    if(!raw||raw.schema!==1||!Array.isArray(raw.sessions)||raw.sessions.length>500)throw Error('対応するバックアップではありません。');
    const seen=new Set();
    for(const s of raw.sessions){
      if(!s||typeof s.id!=='string'||!/^[-a-zA-Z0-9_]{1,80}$/.test(s.id)||seen.has(s.id)||!plans[s.plan]||!s.frames||typeof s.frames!=='object'||Array.isArray(s.frames))throw Error('記録の形式が正しくありません。');
      seen.add(s.id);
      const allowed=new Set(slots(s.plan).map(x=>x.key));
      for(const [key,f] of Object.entries(s.frames))if(!allowed.has(key)||!f||typeof f.file!=='string'||!/^[a-zA-Z0-9_-]+\.jpg$/.test(f.file))throw Error('写真の形式が正しくありません。');
    }
    return raw;
  }
  const api={plans,plan,slots,direction,nearest,nextInRow,nextRow,guidance,wrap,validate};
  root.HairCore=api;if(typeof module!=='undefined')module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
