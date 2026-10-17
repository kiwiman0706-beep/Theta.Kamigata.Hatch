const {test}=require('node:test');
const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/core.js');
function make(plan='standard',keys=C.slots(plan).map(x=>x.key)){
 return {id:'test-1',plan,frames:Object.fromEntries(keys.map(key=>[key,{file:`test-${key}.jpg`}])),child:'りさ',title:'ボブ',date:'2026-10-03'};
}
test('every plan contains a complete orbit, optional upper orbit and one top image',()=>{
 for(const [key,p]of Object.entries(C.plans)){
  const slots=C.slots(key);assert.equal(slots.length,p.count);assert.equal(new Set(slots.map(x=>x.key)).size,p.count);
  for(let row=0;row<p.rows;row++){assert.equal(slots.filter(s=>s.row===row).length,p.columns);assert.equal(slots.find(s=>s.row===row&&s.col===p.columns/2).azimuth,180);}
  assert.equal(slots.at(-1).key,'top');assert.equal(slots.at(-1).elevation,90);
 }
});
test('horizontal dragging wraps around and visits each saved frame',()=>{
 for(const plan of Object.keys(C.plans)){
  const s=make(plan),cols=C.plan(plan).columns;
  assert.equal(C.nextInRow(s,'r0_a0',-1).col,cols-1);
  assert.equal(C.nextInRow(s,`r0_a${cols-1}`,1).col,0);
  let at='r0_a0';const visited=[];for(let i=0;i<cols;i++){at=C.nextInRow(s,at,1).key;visited.push(at);}assert.equal(new Set(visited).size,cols);assert.equal(at,'r0_a0');
 }
});
test('vertical drag moves from normal height to upper ring, top and back, preserving yaw',()=>{
 const s=make();assert.equal(C.nextRow(s,'r0_a3',1).key,'r1_a3');assert.equal(C.nextRow(s,'r1_a3',1).key,'top');
 assert.equal(C.nextRow(s,'top',-1,3).key,'r1_a3');assert.equal(C.nextRow(s,'r1_a3',-1).key,'r0_a3');assert.equal(C.nextRow(s,'r0_a3',-1).key,'r0_a3');
});
test('sparse rings skip missing directions and missing elevations',()=>{
 const s=make('standard',['r0_a0','r0_a4','top']);assert.equal(C.nextInRow(s,'r0_a0',1).key,'r0_a4');assert.equal(C.nextInRow(s,'r0_a4',1).key,'r0_a0');
 assert.equal(C.nextRow(s,'r0_a4',1).key,'top');assert.equal(C.nextRow(s,'top',-1,4).key,'r0_a4');
 assert.equal(C.nearest(s,0,7).key,'r0_a0');
});
test('empty and single-photo sessions remain usable',()=>{
 assert.equal(C.nearest(make('easy',[]),0,0),null);const top=make('easy',['top']);assert.equal(C.nextInRow(top,'top',1).key,'top');assert.equal(C.nextRow(top,'top',-1).key,'top');
 const one=make('standard',['r1_a6']);assert.equal(C.nearest(one,0,0).key,'r1_a6');assert.equal(C.nextInRow(one,'r1_a6',1).key,'r1_a6');
});
test('front, child right, back and child left are unambiguously mapped',()=>{
 const slots=C.slots('standard');assert.equal(slots[0].label,'正面');assert.equal(slots[2].label,'右側');assert.equal(slots[4].label,'後ろ');assert.equal(slots[6].label,'左側');assert.match(C.guidance(slots[2],'standard'),/お子さんの右側/);
});
test('backup metadata rejects duplicate records, invalid directions and path traversal',()=>{
 const base={schema:1,sessions:[make()]};assert.equal(C.validate(base),base);
 const clone=x=>JSON.parse(JSON.stringify(x));
 const duplicate=clone(base);duplicate.sessions.push(make());assert.throws(()=>C.validate(duplicate));
 for(const bad of ['../photo.jpg','photo.png','x/photo.jpg']){const raw=clone(base);raw.sessions[0].frames.r0_a0.file=bad;assert.throws(()=>C.validate(raw));}
 const direction=clone(base);direction.sessions[0].frames.r9_a0={file:'x.jpg'};assert.throws(()=>C.validate(direction));
 const plan=clone(base);plan.sessions[0].plan='unknown';assert.throws(()=>C.validate(plan));
});
