package jp.yaegaki.kururihair;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.AtomicFile;
import android.util.Base64;
import android.util.Base64OutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Private, transactional metadata + immutable normalized JPEGs. No network. */
public final class HairStore {
    private static HairStore instance;
    public static synchronized HairStore get(Context context) {
        if(instance==null)instance=new HairStore(context.getApplicationContext());
        return instance;
    }
    private final File root, photos;
    private final AtomicFile index;
    private HairStore(Context context) {
        root=new File(context.getFilesDir(),"hair-records");
        photos=new File(root,"photos");
        if(!photos.isDirectory()&&!photos.mkdirs()) throw new IllegalStateException("保存先を作成できません。");
        index=new AtomicFile(new File(root,"records.json"));
    }
    public synchronized JSONObject load() throws Exception {
        if(!index.getBaseFile().exists()&&!new File(index.getBaseFile().getPath()+".bak").exists())
            return new JSONObject().put("schema",1).put("sessions",new JSONArray());
        byte[] bytes=index.readFully();
        if(bytes.length>4*1024*1024)throw new IOException("記録ファイルが大きすぎます。");
        return validate(new JSONObject(new String(bytes,StandardCharsets.UTF_8)),photos,true);
    }
    public synchronized void save(JSONObject value) throws Exception {
        JSONObject checked=validate(value,photos,true);commit(checked);garbageCollect(checked);
    }
    private void commit(JSONObject value) throws Exception {
        FileOutputStream out=null;
        try {out=index.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));index.finishWrite(out);}
        catch(Exception e){if(out!=null)index.failWrite(out);throw e;}
    }
    private JSONObject validate(JSONObject raw,File photoDir,boolean checkFiles) throws Exception {
        if(raw.optInt("schema")!=1)throw new IOException("対応する記録形式ではありません。");
        JSONArray arr=raw.getJSONArray("sessions");if(arr.length()>500)throw new IOException("記録は500件までです。");
        JSONArray clean=new JSONArray();Set<String> ids=new HashSet<>();
        for(int i=0;i<arr.length();i++){
            JSONObject s=arr.getJSONObject(i),out=new JSONObject();String id=s.getString("id"),plan=s.getString("plan");
            if(!id.matches("[-A-Za-z0-9_]{1,80}")||!ids.add(id))throw new IOException("記録IDが正しくありません。");
            if(!plan.equals("easy")&&!plan.equals("standard")&&!plan.equals("smooth"))throw new IOException("撮影プランが正しくありません。");
            int cols=plan.equals("smooth")?12:8,rows=plan.equals("easy")?1:2;
            out.put("id",id).put("plan",plan).put("child",limited(s.optString("child"),40)).put("title",limited(s.optString("title"),80))
                .put("date",limited(s.optString("date"),20)).put("note",limited(s.optString("note"),4000))
                .put("created",s.optLong("created")).put("updated",s.optLong("updated"));
            JSONObject frames=s.getJSONObject("frames"),newFrames=new JSONObject();
            if(frames.length()>cols*rows+1)throw new IOException("写真の数が正しくありません。");
            Iterator<String> it=frames.keys();while(it.hasNext()){
                String key=it.next();if(!validSlot(key,cols,rows))throw new IOException("撮影方向が正しくありません。");
                JSONObject f=frames.getJSONObject(key);String filename=f.getString("file");
                if(!filename.matches("[A-Za-z0-9_-]{1,120}\\.jpg"))throw new IOException("写真の名前が正しくありません。");
                File file=new File(photoDir,filename);
                if(checkFiles&&(!file.isFile()||file.length()==0||file.length()>20L*1024*1024))throw new IOException("写真ファイルが見つからないか、破損しています。");
                JSONObject nf=new JSONObject().put("file",filename).put("updated",f.optLong("updated"));
                JSONObject a=f.optJSONObject("align"),na=new JSONObject();
                if(a!=null){na.put("x",finite(a.optDouble("x",0),-80,80,0)).put("y",finite(a.optDouble("y",0),-80,80,0))
                    .put("scale",finite(a.optDouble("scale",1),0.5,3,1)).put("rotation",Math.floorMod(a.optInt("rotation",0),360)/90*90);}
                else na.put("x",0).put("y",0).put("scale",1).put("rotation",0);
                nf.put("align",na);newFrames.put(key,nf);
            }
            out.put("frames",newFrames);clean.put(out);
        }
        return new JSONObject().put("schema",1).put("sessions",clean);
    }
    private static boolean validSlot(String key,int cols,int rows){
        if(key.equals("top"))return true;
        if(!key.matches("r[01]_a[0-9]{1,2}"))return false;
        try{return Integer.parseInt(key.substring(1,2))<rows&&Integer.parseInt(key.substring(4))<cols;}catch(Exception e){return false;}
    }
    private static double finite(double n,double lo,double hi,double fallback){return Double.isNaN(n)||Double.isInfinite(n)?fallback:Math.max(lo,Math.min(hi,n));}
    private static String limited(String s,int max){return s.length()>max?s.substring(0,max):s;}
    public synchronized JSONObject find(String id) throws Exception {
        JSONArray arr=load().getJSONArray("sessions");for(int i=0;i<arr.length();i++)if(arr.getJSONObject(i).getString("id").equals(id))return arr.getJSONObject(i);
        throw new IOException("記録が見つかりません。");
    }
    public synchronized File photo(String name) throws IOException {
        if(!name.matches("[A-Za-z0-9_-]{1,120}\\.jpg"))throw new IOException("写真の名前が正しくありません。");
        File f=new File(photos,name);if(!f.isFile())throw new FileNotFoundException();return f;
    }
    public synchronized void addPhoto(String id,String key,File input) throws Exception {
        JSONObject snapshot=load();JSONArray arr=snapshot.getJSONArray("sessions");JSONObject target=null;
        for(int i=0;i<arr.length();i++)if(arr.getJSONObject(i).getString("id").equals(id))target=arr.getJSONObject(i);
        if(target==null)throw new IOException("記録が見つかりません。");
        String plan=target.getString("plan");if(!validSlot(key,plan.equals("smooth")?12:8,plan.equals("easy")?1:2))throw new IOException("撮影方向が正しくありません。");
        String name="photo-"+UUID.randomUUID()+".jpg";File out=new File(photos,name),temp=new File(photos,name+".tmp");
        normalize(input,temp);
        if(!temp.renameTo(out)){temp.delete();throw new IOException("写真を保存できません。");}
        long now=System.currentTimeMillis();target.getJSONObject("frames").put(key,new JSONObject().put("file",name).put("updated",now)
            .put("align",new JSONObject().put("x",0).put("y",0).put("scale",1).put("rotation",0)));target.put("updated",now);
        try{commit(validate(snapshot,photos,true));garbageCollect(snapshot);}catch(Exception e){out.delete();throw e;}
    }
    public synchronized File lastPhoto(String id) {
        try{JSONObject frames=find(id).getJSONObject("frames");JSONObject best=null;Iterator<String> it=frames.keys();while(it.hasNext()){JSONObject f=frames.getJSONObject(it.next());if(best==null||f.optLong("updated")>best.optLong("updated"))best=f;}return best==null?null:photo(best.getString("file"));}catch(Exception e){return null;}
    }
    private static void normalize(File input,File output) throws Exception {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(input.getPath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>40000||bounds.outHeight>40000)throw new IOException("この画像を読み込めません。JPEG・PNGなどの写真を選んでください。");
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/opts.inSampleSize>2400)opts.inSampleSize*=2;
        Bitmap source=BitmapFactory.decodeFile(input.getPath(),opts);if(source==null)throw new IOException("この写真を読み込めません。");
        Bitmap upright=source,result=null;
        try{
            Matrix m=new Matrix();int orientation=ExifInterface.ORIENTATION_NORMAL;
            try{orientation=new ExifInterface(input.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException ignored){}
            switch(orientation){
                case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;
                case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(-90);break;
            }
            if(!m.isIdentity())upright=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,true);
            double ratio=Math.min(1,1800.0/Math.max(upright.getWidth(),upright.getHeight()));
            result=ratio<1?Bitmap.createScaledBitmap(upright,Math.max(1,(int)(upright.getWidth()*ratio)),Math.max(1,(int)(upright.getHeight()*ratio)),true):upright;
            try(FileOutputStream out=new FileOutputStream(output)){if(!result.compress(Bitmap.CompressFormat.JPEG,92,out))throw new IOException("写真を保存できません。");out.getFD().sync();}
        }finally{if(result!=null&&result!=upright)result.recycle();if(upright!=source)upright.recycle();source.recycle();}
    }
    private void garbageCollect(JSONObject snapshot) throws Exception {
        Set<String> refs=referenced(snapshot);File[] files=photos.listFiles();if(files!=null)for(File f:files)if(!refs.contains(f.getName()))f.delete();
    }
    private Set<String> referenced(JSONObject snapshot) throws Exception {
        Set<String> names=new LinkedHashSet<>();JSONArray arr=snapshot.getJSONArray("sessions");for(int i=0;i<arr.length();i++){JSONObject frames=arr.getJSONObject(i).getJSONObject("frames");Iterator<String> it=frames.keys();while(it.hasNext())names.add(frames.getJSONObject(it.next()).getString("file"));}return names;
    }
    public synchronized void exportBackup(OutputStream raw) throws Exception {
        JSONObject snapshot=load();try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(raw))){
            out.putNextEntry(new ZipEntry("records.json"));out.write(snapshot.toString(2).getBytes(StandardCharsets.UTF_8));out.closeEntry();
            for(String name:referenced(snapshot)){out.putNextEntry(new ZipEntry("photos/"+name));try(InputStream in=new FileInputStream(photo(name))){copy(in,out,20L*1024*1024);}out.closeEntry();}
        }
    }
    public synchronized int importBackup(InputStream raw,File cacheDir) throws Exception {
        File staging=new File(cacheDir,"restore-"+UUID.randomUUID()),stagePhotos=new File(staging,"photos");if(!stagePhotos.mkdirs())throw new IOException("復元先を用意できません。");
        List<File> installed=new ArrayList<>();boolean committed=false;
        try{
            Set<String> seen=new HashSet<>();long total=0;int n=0;
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(raw))){ZipEntry e;while((e=zip.getNextEntry())!=null){
                if(++n>13001)throw new IOException("バックアップ内のファイル数が多すぎます。");
                String name=e.getName();if(e.isDirectory()&&name.equals("photos/"))continue;
                if(!name.equals("records.json")&&!name.matches("photos/[A-Za-z0-9_-]{1,120}\\.jpg"))throw new IOException("このアプリのバックアップではありません。");
                if(!seen.add(name))throw new IOException("重複したファイルが含まれています。");
                File dst=name.equals("records.json")?new File(staging,name):new File(stagePhotos,name.substring(7));
                long limit=name.equals("records.json")?4L*1024*1024:20L*1024*1024;
                try(OutputStream out=new FileOutputStream(dst)){total+=copy(zip,out,limit);}
                if(total>700L*1024*1024)throw new IOException("バックアップが大きすぎます（展開後700MBまで）。");zip.closeEntry();
            }}
            File metadata=new File(staging,"records.json");if(!metadata.isFile())throw new IOException("記録情報が見つかりません。");
            JSONObject incoming;try(InputStream in=new FileInputStream(metadata)){incoming=validate(new JSONObject(new String(readAll(in,4*1024*1024),StandardCharsets.UTF_8)),stagePhotos,true);}
            for(String name:referenced(incoming)){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(new File(stagePhotos,name).getPath(),bounds);if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>5000||bounds.outHeight>5000)throw new IOException("破損した写真が含まれています。");}
            JSONObject current;try{current=load();}catch(Exception e){current=new JSONObject().put("schema",1).put("sessions",new JSONArray());}
            JSONArray imported=incoming.getJSONArray("sessions"),existing=current.getJSONArray("sessions"),merged=new JSONArray();Set<String> replaces=new HashSet<>();
            for(int i=0;i<imported.length();i++)replaces.add(imported.getJSONObject(i).getString("id"));
            for(int i=0;i<existing.length();i++)if(!replaces.contains(existing.getJSONObject(i).getString("id")))merged.put(existing.getJSONObject(i));
            // Rename every imported image to an immutable fresh name. Never overwrite live bytes before commit.
            Map<String,String> renamed=new HashMap<>();for(String name:referenced(incoming)){
                String fresh="photo-"+UUID.randomUUID()+".jpg";File from=new File(stagePhotos,name),to=new File(photos,fresh);
                if(!from.renameTo(to)){try(InputStream in=new FileInputStream(from);OutputStream out=new FileOutputStream(to)){copy(in,out,20L*1024*1024);}}
                installed.add(to);renamed.put(name,fresh);
            }
            for(int i=0;i<imported.length();i++){JSONObject s=imported.getJSONObject(i),frames=s.getJSONObject("frames");Iterator<String> it=frames.keys();while(it.hasNext()){JSONObject f=frames.getJSONObject(it.next());f.put("file",renamed.get(f.getString("file")));}merged.put(s);}
            JSONObject next=validate(new JSONObject().put("schema",1).put("sessions",merged),photos,true);commit(next);committed=true;garbageCollect(next);return imported.length();
        }finally{if(!committed)for(File f:installed)f.delete();deleteTree(staging);}
    }
    public synchronized void exportView(String id,String template,OutputStream out) throws Exception {
        int marker=template.indexOf("__PAYLOAD__");if(marker<0)throw new IOException("書き出し用の画面を開けません。");
        JSONObject s=find(id),frames=s.getJSONObject("frames");s.remove("frames");String meta=safeScript(s.toString());
        writeString(out,template.substring(0,marker));writeString(out,meta.substring(0,meta.length()-1)+",\"frames\":{");
        Iterator<String> it=frames.keys();boolean first=true;while(it.hasNext()){
            String key=it.next();JSONObject f=frames.getJSONObject(key);if(!first)writeString(out,",");first=false;
            writeString(out,JSONObject.quote(key)+":{\"align\":"+f.getJSONObject("align").toString()+",\"image\":\"data:image/jpeg;base64,");
            // Stream photos directly. A 25-angle export does not require all base64 images in memory.
            try(InputStream in=new FileInputStream(photo(f.getString("file")));Base64OutputStream base64=new Base64OutputStream(out,Base64.NO_WRAP|Base64.NO_CLOSE)){copy(in,base64,20L*1024*1024);}
            writeString(out,"\"}");
        }
        writeString(out,"}}");writeString(out,template.substring(marker+"__PAYLOAD__".length()));out.flush();
    }
    private static String safeScript(String text){return text.replace("<","\\u003c").replace(">","\\u003e").replace("&","\\u0026");}
    private static void writeString(OutputStream out,String text) throws IOException {out.write(text.getBytes(StandardCharsets.UTF_8));}
    public static long copy(InputStream in,OutputStream out,long max) throws IOException {
        byte[] buffer=new byte[32768];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>max)throw new IOException("ファイルが大きすぎます。");out.write(buffer,0,n);}return total;
    }
    public static byte[] readAll(InputStream in,int max) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();copy(in,out,max);return out.toByteArray();}
    public static void deleteTree(File f){if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File c:children)deleteTree(c);}f.delete();}
}
