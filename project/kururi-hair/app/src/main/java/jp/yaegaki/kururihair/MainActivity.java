package jp.yaegaki.kururihair;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.*;
import android.widget.Toast;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private WebView web;
    private HairStore store;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final int CAMERA=100,PHOTO=101,BACKUP_OUT=102,BACKUP_IN=103,HTML_OUT=104,VIDEO_CAPTURE=105,VIDEO_IMPORT=106,VIDEO_PICK=107,PERMISSION=201;
    private String pendingId="",pendingSlot="",pendingSlots="[]",pendingVideoPath="",pendingCameraAction="photo";
    private boolean pageReady=false;
    private final List<String> deferred=new ArrayList<>();
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);store=HairStore.get(this);
        if(saved!=null){pendingId=saved.getString("id","");pendingSlot=saved.getString("slot","");pendingSlots=saved.getString("slots","[]");pendingVideoPath=saved.getString("video","");pendingCameraAction=saved.getString("cameraAction","photo");}
        web=new WebView(this);web.setBackgroundColor(Color.rgb(249,247,253));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setAllowFileAccessFromFileURLs(false);settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportZoom(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);web.addJavascriptInterface(new Bridge(),"Android");
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();if(!"https".equals(uri.getScheme())||!"kururi.local".equals(uri.getHost()))return denied();
                String path=uri.getPath();try{
                    if(path!=null&&path.startsWith("/photos/")){String name=path.substring(8);return new WebResourceResponse("image/jpeg",null,new FileInputStream(store.photo(name)));}
                    if(path==null||path.equals("/"))path="/index.html";
                    if(!path.matches("/[a-zA-Z0-9_-]+\\.(html|js|css)"))return denied();
                    String mime=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":"text/html";
                    return new WebResourceResponse(mime,"UTF-8",getAssets().open(path.substring(1)));
                }catch(Exception e){return denied();}
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public void onPageFinished(WebView view,String url){if(!"https://kururi.local/index.html".equals(url))return;pageReady=true;for(String js:deferred)view.evaluateJavascript(js,null);deferred.clear();}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsAlert(WebView view,String url,String message,JsResult result){new android.app.AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("OK",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true;}
        });
        FrameLayout surface=new FrameLayout(this);surface.setBackgroundColor(Color.rgb(249,247,253));surface.addView(web,new FrameLayout.LayoutParams(-1,-1));
        setContentView(surface);applyInsets(surface);web.loadUrl("https://kururi.local/index.html");
    }
    private void applyInsets(View surface){
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(Build.VERSION.SDK_INT>=35){surface.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout()|android.view.WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return android.view.WindowInsets.CONSUMED;});surface.requestApplyInsets();}
    }
    private WebResourceResponse denied(){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("id",pendingId);out.putString("slot",pendingSlot);out.putString("slots",pendingSlots);out.putString("video",pendingVideoPath);out.putString("cameraAction",pendingCameraAction);}
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.appBack && window.appBack()",null);else super.onBackPressed();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("Android");web.destroy();}worker.shutdown();super.onDestroy();}
    public final class Bridge {
        @JavascriptInterface public String load(){try{return store.load().toString();}catch(Exception e){return errorJson("保存データを読み込めませんでした。バックアップから復元できます。");}}
        @JavascriptInterface public String save(String json){try{if(json.length()>4*1024*1024)throw new IOException("記録が大きすぎます。");store.save(new JSONObject(json));return "{\"ok\":true}";}catch(Exception e){return errorJson(message(e));}}
        @JavascriptInterface public void capture(String id,String slots){runOnUiThread(()->{try{store.find(id);new JSONArray(slots);pendingId=id;pendingSlots=slots;pendingCameraAction="photo";if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},PERMISSION);else openCamera();}catch(Exception e){event(false,"capture",message(e));}});}
        @JavascriptInterface public void captureVideo(String id,String slots,String key){runOnUiThread(()->{try{prepareVideo(id,slots,key);pendingCameraAction="video";if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},PERMISSION);else openVideoCamera();}catch(Exception e){event(false,"video",message(e));}});}
        @JavascriptInterface public void importVideo(String id,String slots,String key){runOnUiThread(()->{try{prepareVideo(id,slots,key);Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*");safeLaunch(i,VIDEO_IMPORT);}catch(Exception e){event(false,"video",message(e));}});}
        @JavascriptInterface public void importPhoto(String id,String slot){runOnUiThread(()->{pendingId=id;pendingSlot=slot;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");safeLaunch(i,PHOTO);});}
        @JavascriptInterface public void exportBackup(){runOnUiThread(()->{String date=new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date());Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"kururi-hair-backup-"+date+".zip");safeLaunch(i,BACKUP_OUT);});}
        @JavascriptInterface public void importBackup(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});safeLaunch(i,BACKUP_IN);});}
        @JavascriptInterface public void exportView(String id){runOnUiThread(()->{pendingId=id;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/html").putExtra(Intent.EXTRA_TITLE,"kururi-hair-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date())+".html");safeLaunch(i,HTML_OUT);});}
        @JavascriptInterface public void exit(){runOnUiThread(()->finish());}
    }
    private void safeLaunch(Intent intent,int request){try{startActivityForResult(intent,request);}catch(Exception e){event(false,"io","ファイル選択画面を開けませんでした。");}}
    private void openCamera(){Intent i=new Intent(this,CaptureActivity.class).putExtra("id",pendingId).putExtra("slots",pendingSlots);startActivityForResult(i,CAMERA);}
    private void prepareVideo(String id,String slots,String key) throws Exception {
        store.find(id);JSONArray list=new JSONArray(slots);if(list.length()==0||list.length()>25)throw new IOException("撮影方向が正しくありません。");
        pendingId=id;pendingSlots=slots;pendingSlot=key==null?"":key;
    }
    private void openVideoCamera(){
        try{
            File file=VideoFilesProvider.newVideo(this);pendingVideoPath=file.getPath();Uri uri=VideoFilesProvider.uri(this,file);
            Intent i=new Intent(MediaStore.ACTION_VIDEO_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,uri)
                .putExtra(MediaStore.EXTRA_DURATION_LIMIT,60).putExtra(MediaStore.EXTRA_SIZE_LIMIT,200L*1024*1024)
                .putExtra(MediaStore.EXTRA_VIDEO_QUALITY,1).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("動画の保存先",uri));startActivityForResult(i,VIDEO_CAPTURE);
        }catch(Exception e){deletePendingVideo();event(false,"video","動画カメラを開けませんでした。端末のカメラで動画を撮って「既存の動画を選ぶ」から読み込めます。");}
    }
    private void deletePendingVideo(){
        try{if(!pendingVideoPath.isEmpty()){File file=VideoFilesProvider.checkedFile(this,pendingVideoPath);revokeUriPermission(VideoFilesProvider.uri(this,file),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);file.delete();}}catch(Exception ignored){}
        pendingVideoPath="";
    }
    private void openVideoPicker(File file,String id,String slots,String key){
        runOnUiThread(()->{if(isFinishing()||isDestroyed()){file.delete();return;}try{pendingVideoPath=file.getPath();startActivityForResult(new Intent(this,VideoFrameActivity.class).putExtra("path",file.getPath()).putExtra("id",id).putExtra("slots",slots).putExtra("key",key),VIDEO_PICK);}catch(Exception e){deletePendingVideo();event(false,"video",message(e));}});
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==PERMISSION){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){if("video".equals(pendingCameraAction))openVideoCamera();else openCamera();}else event(false,"capture","撮影にはカメラの許可が必要です。設定で許可するか、既存の写真・動画を選んでください。");}}
    @Override protected void onActivityResult(int request,int result,Intent intent){
        super.onActivityResult(request,result,intent);
        if(request==CAMERA){if(result==RESULT_OK)event(true,"capture",intent==null?"撮影した写真を保存しました。":intent.getStringExtra("message"));else event(true,"capture","撮影を中断しました。保存済みの写真は残っています。");return;}
        if(request==VIDEO_PICK){deletePendingVideo();if(result==RESULT_OK)event(true,"video",intent==null?"動画から選んだ写真を保存しました。":intent.getStringExtra("message"));else event(false,"cancel","");return;}
        if(request==VIDEO_CAPTURE||request==VIDEO_IMPORT){
            if(result!=RESULT_OK){if(request==VIDEO_CAPTURE)deletePendingVideo();event(false,"cancel","");return;}
            final String id=pendingId,slots=pendingSlots,key=pendingSlot,path=pendingVideoPath;
            final Uri source=intent==null?null:intent.getData();
            worker.submit(()->{
                File file=null;
                try{
                    if(request==VIDEO_CAPTURE){file=VideoFilesProvider.checkedFile(this,path);revokeUriPermission(VideoFilesProvider.uri(this,file),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}
                    else file=VideoFilesProvider.newVideo(this);
                    if(request==VIDEO_IMPORT||file.length()==0){
                        if(source==null)throw new IOException("動画が保存されませんでした。既存の動画の読み込みも利用できます。");
                        try(InputStream in=getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException("動画を開けません。");HairStore.copy(in,out,400L*1024*1024);}
                    }
                    if(file.length()==0||file.length()>400L*1024*1024)throw new IOException("動画は400MBまでです。短い動画を選んでください。");
                    openVideoPicker(file,id,slots,key);
                }catch(Exception e){if(file!=null)file.delete();event(false,"video",message(e));}
            });return;
        }
        if(result!=RESULT_OK||intent==null||intent.getData()==null){event(false,"cancel","");return;}
        final Uri uri=intent.getData();final String id=pendingId,slot=pendingSlot;
        worker.submit(()->{
            try{
                if(request==PHOTO){File tmp=new File(getCacheDir(),"import-"+UUID.randomUUID());try{try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp)){if(in==null)throw new IOException("写真を開けません。");HairStore.copy(in,out,80L*1024*1024);}store.addPhoto(id,slot,tmp);}finally{tmp.delete();}event(true,"photo","写真を保存しました。");}
                else if(request==BACKUP_OUT){OutputStream out=getContentResolver().openOutputStream(uri,"wt");if(out==null)throw new IOException("保存先を開けません。");store.exportBackup(out);event(true,"backup","バックアップを保存しました。");}
                else if(request==BACKUP_IN){try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("バックアップを開けません。");int n=store.importBackup(in,getCacheDir());event(true,"restore",n+"件の記録を読み込みました。");}}
                else if(request==HTML_OUT){String template;try(InputStream in=getAssets().open("export.html")){template=new String(HairStore.readAll(in,1024*1024),StandardCharsets.UTF_8);}try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("保存先を開けません。");store.exportView(id,template,out);}event(true,"html","HTMLを保存しました。PCのブラウザでも開けます。");}
            }catch(Exception e){event(false,"io",message(e));}
        });
    }
    private String errorJson(String s){try{return new JSONObject().put("ok",false).put("error",s).toString();}catch(Exception e){return "{\"ok\":false,\"error\":\"保存できませんでした。\"}";}}
    private static String message(Exception e){String m=e.getMessage();return m==null||m.length()>180?"操作を完了できませんでした。空き容量やファイルを確認してください。":m;}
    private void event(boolean ok,String kind,String message){
        try{String js="window.nativeResult && window.nativeResult("+new JSONObject().put("ok",ok).put("kind",kind).put("message",message==null?"保存しました。":message).toString()+")";
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(pageReady)web.evaluateJavascript(js,null);else deferred.add(js);});
        }catch(Exception ignored){}
    }
}
