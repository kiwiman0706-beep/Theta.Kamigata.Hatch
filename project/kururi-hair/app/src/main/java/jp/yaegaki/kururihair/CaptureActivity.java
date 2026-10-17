package jp.yaegaki.kururihair;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.Size;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

/** Guided native Camera2 capture, with review before each atomic save. */
public final class CaptureActivity extends Activity {
    private HairStore store;
    private String id;
    private JSONArray slots;
    private int at=0,accepted=0;
    private FrameLayout cameraFrame;
    private TextureView texture;
    private ImageView review,ghost;
    private GuideView guide;
    private TextView title,direction,hint,counter,status;
    private Button shutter,skip,ghostButton,swap,retake,accept;
    private LinearLayout shooting,reviewing;
    private volatile CameraDevice camera;
    private volatile CameraCaptureSession cameraSession;
    private volatile ImageReader reader;
    private CaptureRequest.Builder previewRequest;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Semaphore lock=new Semaphore(1);
    private Size previewSize;
    private int sensorOrientation=90;
    private volatile boolean front=false,opening=false,paused=true,reviewMode=false,shootingNow=false,saving=false,closed=false;
    private File pending;
    private Bitmap reviewBitmap,ghostBitmap;
    private Runnable timeout;
    private volatile int generation=0;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(34,29,46));getWindow().setNavigationBarColor(Color.rgb(34,29,46));getWindow().getDecorView().setSystemUiVisibility(0);
        store=HairStore.get(this);id=getIntent().getStringExtra("id");
        try{store.find(id);slots=new JSONArray(getIntent().getStringExtra("slots"));if(slots.length()==0||slots.length()>25)throw new IOException("撮影方向が正しくありません。");}
        catch(Exception e){Toast.makeText(this,"撮影を開始できませんでした。",Toast.LENGTH_LONG).show();finish();return;}
        if(saved!=null){at=saved.getInt("at",0);accepted=saved.getInt("accepted",0);front=saved.getBoolean("front",false);}
        if(at>=slots.length()){finishSaved();return;}
        buildUI();setDirection();
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){if(!paused&&!reviewMode)openCamera();}
            public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){configureTransform(w,h);}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture t){return true;}
            public void onSurfaceTextureUpdated(SurfaceTexture t){}
        });
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private Button button(String value,int color,int bg){Button b=new Button(this);b.setText(value);b.setTextSize(13);b.setTextColor(color);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(10),dp(5),dp(10),dp(5));GradientDrawable g=new GradientDrawable();g.setColor(bg);g.setCornerRadius(dp(14));b.setBackground(g);return b;}
    private void buildUI(){
        int white=Color.WHITE,muted=Color.rgb(189,180,205),panel=Color.rgb(47,40,62),lavender=Color.rgb(202,185,240);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(34,29,46));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(14),dp(8),dp(14),dp(8));
        Button done=button("終了",white,panel);top.addView(done,new LinearLayout.LayoutParams(dp(62),dp(42)));done.setOnClickListener(v->confirmFinish());
        title=text("ガイドに合わせて撮影",16,white);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER);top.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));
        counter=text("",12,muted);counter.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);top.addView(counter,new LinearLayout.LayoutParams(dp(65),dp(42)));root.addView(top);
        direction=text("",21,white);direction.setTypeface(null,Typeface.BOLD);direction.setPadding(dp(22),dp(4),dp(22),0);root.addView(direction,new LinearLayout.LayoutParams(-1,dp(36)));
        hint=text("",12,muted);hint.setPadding(dp(22),0,dp(22),dp(8));root.addView(hint,new LinearLayout.LayoutParams(-1,dp(52)));
        cameraFrame=new FrameLayout(this);texture=new TextureView(this);cameraFrame.addView(texture,new FrameLayout.LayoutParams(-1,-1));
        ghost=new ImageView(this);ghost.setScaleType(ImageView.ScaleType.CENTER_CROP);ghost.setAlpha(.22f);ghost.setVisibility(View.GONE);cameraFrame.addView(ghost,new FrameLayout.LayoutParams(-1,-1));
        guide=new GuideView(this);cameraFrame.addView(guide,new FrameLayout.LayoutParams(-1,-1));
        review=new ImageView(this);review.setScaleType(ImageView.ScaleType.CENTER_CROP);review.setVisibility(View.GONE);cameraFrame.addView(review,new FrameLayout.LayoutParams(-1,-1));
        root.addView(cameraFrame,new LinearLayout.LayoutParams(-1,0,1));
        status=text("カメラを準備しています…",12,muted);status.setGravity(Gravity.CENTER);root.addView(status,new LinearLayout.LayoutParams(-1,dp(32)));
        shooting=new LinearLayout(this);shooting.setOrientation(LinearLayout.VERTICAL);shooting.setPadding(dp(18),dp(2),dp(18),dp(14));
        LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER);ghostButton=button("前の写真：OFF",muted,panel);tools.addView(ghostButton,new LinearLayout.LayoutParams(0,dp(37),1));
        swap=button("カメラ切替",muted,panel);LinearLayout.LayoutParams switchParams=new LinearLayout.LayoutParams(0,dp(37),1);switchParams.leftMargin=dp(10);tools.addView(swap,switchParams);shooting.addView(tools);
        ghostButton.setOnClickListener(v->toggleGhost());swap.setOnClickListener(v->{if(shootingNow||saving)return;front=!front;closeCamera();openCamera();});
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);actions.setPadding(0,dp(12),0,0);
        skip=button("あとで撮る",muted,Color.TRANSPARENT);actions.addView(skip,new LinearLayout.LayoutParams(0,dp(64),1));skip.setOnClickListener(v->{if(!shootingNow&&!saving)nextSlot();});
        shutter=button("",white,white);GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(Color.WHITE);circle.setStroke(dp(5),Color.rgb(152,132,186));shutter.setBackground(circle);shutter.setContentDescription("写真を撮る");actions.addView(shutter,new LinearLayout.LayoutParams(dp(68),dp(68)));shutter.setEnabled(false);shutter.setOnClickListener(v->takePhoto());
        TextView spacer=text("",12,white);actions.addView(spacer,new LinearLayout.LayoutParams(0,dp(64),1));shooting.addView(actions);root.addView(shooting);
        reviewing=new LinearLayout(this);reviewing.setPadding(dp(18),dp(10),dp(18),dp(20));reviewing.setGravity(Gravity.CENTER);
        retake=button("撮り直す",white,panel);reviewing.addView(retake,new LinearLayout.LayoutParams(0,dp(52),1));retake.setOnClickListener(v->retake());
        accept=button("保存して次へ",Color.rgb(46,34,64),lavender);LinearLayout.LayoutParams acceptParams=new LinearLayout.LayoutParams(0,dp(52),1.5f);acceptParams.leftMargin=dp(12);reviewing.addView(accept,acceptParams);accept.setOnClickListener(v->savePhoto());reviewing.setVisibility(View.GONE);root.addView(reviewing);
        setContentView(root);
        if(Build.VERSION.SDK_INT>=35){root.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});root.requestApplyInsets();}
    }
    private JSONObject slot(){return slots.optJSONObject(at);}
    private void setDirection(){
        if(slot()==null)return;String label=slot().optString("label");direction.setText(label);counter.setText((at+1)+" / "+slots.length());
        boolean top=slot().optString("key").equals("top"),high=slot().optInt("row")>0;
        hint.setText(top?"頭の真上から。つむじを中央に入れます。":high?"この方向へ移動して、約35°見下ろします。\n右・左は、お子さんから見た方向です。":"この方向へ移動して、頭と同じ高さから。\n右・左は、お子さんから見た方向です。");
        if(guide!=null){guide.invalidate();}if(accept!=null)accept.setText(at==slots.length()-1?"保存して終了":"保存して次へ");
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("at",at);out.putInt("accepted",accepted);out.putBoolean("front",front);}
    @Override protected void onResume(){super.onResume();paused=false;if(texture==null)return;cameraThread=new HandlerThread("hair-camera");cameraThread.start();cameraHandler=new Handler(cameraThread.getLooper());if(texture.isAvailable()&&!reviewMode)openCamera();}
    @Override protected void onPause(){paused=true;generation++;cancelTimeout();shootingNow=false;if(skip!=null)skip.setEnabled(true);if(swap!=null)swap.setEnabled(true);closeCamera();if(cameraThread!=null){cameraThread.quitSafely();try{cameraThread.join(800);}catch(InterruptedException e){Thread.currentThread().interrupt();}cameraThread=null;cameraHandler=null;}super.onPause();}
    @Override protected void onDestroy(){closed=true;cancelTimeout();if(pending!=null)pending.delete();if(reviewBitmap!=null)reviewBitmap.recycle();if(ghostBitmap!=null)ghostBitmap.recycle();io.shutdown();super.onDestroy();}
    private void openCamera(){
        if(paused||opening||reviewMode||camera!=null||cameraHandler==null||!texture.isAvailable())return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){fail("カメラの許可が必要です。");return;}
        CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);
        try{
            String chosen=null;CameraCharacteristics chars=null;
            for(String cid:manager.getCameraIdList()){CameraCharacteristics c=manager.getCameraCharacteristics(cid);Integer facing=c.get(CameraCharacteristics.LENS_FACING);if(facing!=null&&facing==(front?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK)){chosen=cid;chars=c;break;}}
            if(chosen==null){if(front){front=false;openCamera();return;}throw new IOException("背面カメラが見つかりません。既存写真の取り込みを利用してください。");}
            StreamConfigurationMap map=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);if(map==null)throw new IOException("このカメラは使用できません。");
            Integer orientation=chars.get(CameraCharacteristics.SENSOR_ORIENTATION);sensorOrientation=orientation==null?90:orientation;
            Size jpeg=chooseSize(map.getOutputSizes(ImageFormat.JPEG),true);previewSize=chooseSize(map.getOutputSizes(SurfaceTexture.class),false);
            final int ticket=++generation;
            reader=ImageReader.newInstance(jpeg.getWidth(),jpeg.getHeight(),ImageFormat.JPEG,2);
            reader.setOnImageAvailableListener(r->{
                try(Image image=r.acquireNextImage()){
                    if(image==null)return;ByteBuffer buffer=image.getPlanes()[0].getBuffer();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);
                    File f=new File(getCacheDir(),"capture-"+UUID.randomUUID()+".jpg");try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes);}
                    ui.post(()->{if(ticket==generation&&shootingNow&&!paused)showReview(f);else f.delete();});
                }catch(Exception e){ui.post(()->{if(ticket==generation&&!paused)captureFailed("写真を読み込めませんでした。もう一度撮影してください。");});}
            },cameraHandler);
            configureTransform(texture.getWidth(),texture.getHeight());opening=true;shutter.setEnabled(false);status.setText("カメラを準備しています…");
            if(!lock.tryAcquire(1800,TimeUnit.MILLISECONDS)){opening=false;throw new IOException("カメラの準備に時間がかかっています。戻って再度開いてください。");}
            try{manager.openCamera(chosen,new CameraDevice.StateCallback(){
                private boolean held=true;
                private void release(){if(held){held=false;lock.release();}}
                @Override public void onOpened(CameraDevice device){release();opening=false;if(paused||ticket!=generation||isFinishing()){device.close();return;}camera=device;createPreview(ticket);}
                @Override public void onDisconnected(CameraDevice device){release();device.close();if(ticket!=generation)return;opening=false;camera=null;ui.post(()->{if(!paused)fail("カメラとの接続が切れました。戻って再度開いてください。");});}
                @Override public void onError(CameraDevice device,int error){release();device.close();if(ticket!=generation)return;opening=false;camera=null;ui.post(()->{if(!paused)fail("カメラを使用できません。ほかのカメラアプリを閉じて再試行してください。");});}
            },cameraHandler);}catch(Exception e){lock.release();opening=false;throw e;}
        }catch(Exception e){fail(e.getMessage()==null?"カメラを開けませんでした。":e.getMessage());}
    }
    private Size chooseSize(Size[] sizes,boolean photo) throws IOException {
        if(sizes==null||sizes.length==0)throw new IOException("対応する撮影サイズがありません。");
        long target=photo?2600000:1000000;Size best=sizes[0];double bestScore=Double.MAX_VALUE;
        for(Size s:sizes){double ratio=(double)Math.max(s.getWidth(),s.getHeight())/Math.min(s.getWidth(),s.getHeight());long pixels=(long)s.getWidth()*s.getHeight();double score=Math.abs(ratio-4.0/3)*15+Math.abs(Math.log((double)pixels/target));if(pixels>(photo?5000000:2500000))score+=8;if(score<bestScore){bestScore=score;best=s;}}
        return best;
    }
    private void createPreview(int ticket){
        try{
            if(camera==null||reader==null||texture.getSurfaceTexture()==null)return;
            SurfaceTexture st=texture.getSurfaceTexture();st.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());Surface surface=new Surface(st);
            previewRequest=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);previewRequest.addTarget(surface);previewRequest.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
            setFocus(previewRequest);previewRequest.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
            camera.createCaptureSession(Arrays.asList(surface,reader.getSurface()),new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession s){if(paused||ticket!=generation||camera==null){s.close();return;}cameraSession=s;try{s.setRepeatingRequest(previewRequest.build(),null,cameraHandler);ui.post(()->{if(paused||isFinishing())return;shutter.setEnabled(true);swap.setEnabled(true);status.setText("頭の位置と大きさを枠に合わせてください");});}catch(Exception e){ui.post(()->fail("プレビューを開始できませんでした。"));}}
                @Override public void onConfigureFailed(CameraCaptureSession s){ui.post(()->{if(ticket==generation&&!paused)fail("このカメラでは撮影できません。既存写真を取り込んでください。");});}
            },cameraHandler);
        }catch(Exception e){ui.post(()->fail("カメラの準備に失敗しました。"));}
    }
    private void setFocus(CaptureRequest.Builder b){
        try{CameraCharacteristics c=((CameraManager)getSystemService(CAMERA_SERVICE)).getCameraCharacteristics(camera.getId());int[] modes=c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);if(modes!=null){for(int mode:modes)if(mode==CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE){b.set(CaptureRequest.CONTROL_AF_MODE,mode);return;}}}catch(Exception ignored){}
    }
    private void configureTransform(int w,int h){
        if(previewSize==null||w<=0||h<=0)return;
        int display=getWindowManager().getDefaultDisplay().getRotation()*90;
        CameraGeometry.Preview corrected=CameraGeometry.preview(sensorOrientation,display,
                previewSize.getWidth(),previewSize.getHeight(),w,h);
        Matrix m=new Matrix();
        m.setScale(corrected.scaleX,corrected.scaleY,w/2f,h/2f);
        m.postRotate(corrected.rotationDegrees,w/2f,h/2f);
        texture.setTransform(m);
    }
    private void takePhoto(){
        if(camera==null||cameraSession==null||reader==null||shootingNow||saving||reviewMode)return;
        shootingNow=true;shutter.setEnabled(false);skip.setEnabled(false);swap.setEnabled(false);status.setText("撮影しています…");
        final int captureGeneration=generation;
        try{
            CaptureRequest.Builder b=camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);b.addTarget(reader.getSurface());b.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);setFocus(b);b.set(CaptureRequest.JPEG_QUALITY,(byte)94);
            int display=getWindowManager().getDefaultDisplay().getRotation()*90;
            b.set(CaptureRequest.JPEG_ORIENTATION,CameraGeometry.jpegOrientation(sensorOrientation,display,front));
            timeout=()->{if(captureGeneration==generation&&!paused)captureFailed("撮影に時間がかかっています。もう一度撮影してください。");};ui.postDelayed(timeout,7000);
            cameraSession.capture(b.build(),new CameraCaptureSession.CaptureCallback(){@Override public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure f){ui.post(()->{if(captureGeneration==generation&&!paused)captureFailed("撮影できませんでした。もう一度撮影してください。");});}},cameraHandler);
        }catch(Exception e){captureFailed("撮影できませんでした。もう一度撮影してください。");}
    }
    private void cancelTimeout(){if(timeout!=null){ui.removeCallbacks(timeout);timeout=null;}}
    private void captureFailed(String message){cancelTimeout();shootingNow=false;if(closed||isFinishing()||reviewMode)return;status.setText(message);shutter.setEnabled(false);skip.setEnabled(true);swap.setEnabled(true);Toast.makeText(this,message,Toast.LENGTH_SHORT).show();closeCamera();if(!paused)openCamera();}
    private void showReview(File file){
        cancelTimeout();shootingNow=false;if(closed||isFinishing()||paused){file.delete();return;}pending=file;reviewMode=true;review.setVisibility(View.VISIBLE);guide.setVisibility(View.GONE);ghost.setVisibility(View.GONE);shooting.setVisibility(View.GONE);reviewing.setVisibility(View.VISIBLE);status.setText("この写真でよければ、保存してください");closeCamera();
        io.submit(()->{try{
            Bitmap b=readUpright(file,1200);ui.post(()->{if(closed||isFinishing()||!reviewMode){b.recycle();return;}if(reviewBitmap!=null)reviewBitmap.recycle();reviewBitmap=b;review.setImageBitmap(b);});
        }catch(Exception e){ui.post(()->{retake();status.setText("写真を確認できませんでした。もう一度撮影してください。");});}});
    }
    private static Bitmap readUpright(File file,int max) throws Exception {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/opts.inSampleSize>max)opts.inSampleSize*=2;
        Bitmap b=BitmapFactory.decodeFile(file.getPath(),opts);if(b==null)throw new IOException();
        int o=new android.media.ExifInterface(file.getPath()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();switch(o){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(-90);break;}
        if(m.isIdentity())return b;Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();return rotated;
    }
    private void retake(){
        if(saving)return;if(pending!=null){pending.delete();pending=null;}reviewMode=false;review.setImageDrawable(null);review.setVisibility(View.GONE);if(reviewBitmap!=null){reviewBitmap.recycle();reviewBitmap=null;}guide.setVisibility(View.VISIBLE);shooting.setVisibility(View.VISIBLE);reviewing.setVisibility(View.GONE);skip.setEnabled(true);swap.setEnabled(true);shutter.setEnabled(false);openCamera();
    }
    private void savePhoto(){
        if(pending==null||saving)return;saving=true;accept.setEnabled(false);retake.setEnabled(false);status.setText("この写真を保存しています…");File source=pending;String key=slot().optString("key");
        io.submit(()->{try{store.addPhoto(id,key,source);ui.post(()->{saving=false;accepted++;accept.setEnabled(true);retake.setEnabled(true);if(closed||isFinishing())return;nextSlot();});}catch(Exception e){ui.post(()->{saving=false;accept.setEnabled(true);retake.setEnabled(true);status.setText("保存できませんでした。空き容量を確認して再試行してください。");});}});
    }
    private void nextSlot(){
        if(saving)return;at++;if(at>=slots.length()){finishSaved();return;}retake();setDirection();ghost.setVisibility(View.GONE);ghostButton.setText("前の写真：OFF");
    }
    private void toggleGhost(){
        if(ghost.getVisibility()==View.VISIBLE){ghost.setVisibility(View.GONE);ghostButton.setText("前の写真：OFF");return;}
        File f=store.lastPhoto(id);if(f==null){Toast.makeText(this,"最初の1枚を保存すると使えます。",Toast.LENGTH_SHORT).show();return;}
        io.submit(()->{try{Bitmap b=readUpright(f,700);ui.post(()->{if(closed||isFinishing()||reviewMode){b.recycle();return;}if(ghostBitmap!=null)ghostBitmap.recycle();ghostBitmap=b;ghost.setImageBitmap(b);ghost.setVisibility(View.VISIBLE);ghostButton.setText("前の写真：ON");});}catch(Exception e){ui.post(()->Toast.makeText(this,"前の写真を開けませんでした。",Toast.LENGTH_SHORT).show());}});
    }
    private void finishSaved(){Intent result=new Intent().putExtra("message",accepted==0?"撮影を中断しました。続きはいつでも撮れます。":accepted+"枚の写真を保存しました。");setResult(RESULT_OK,result);finish();}
    private void confirmFinish(){if(saving){Toast.makeText(this,"写真を保存しています。少しお待ちください。",Toast.LENGTH_SHORT).show();return;}if(pending!=null&&reviewMode)new AlertDialog.Builder(this).setTitle("撮影を終了しますか？").setMessage("今確認している写真はまだ保存していません。それまでに保存した写真は残ります。").setNegativeButton("撮影に戻る",null).setPositiveButton("終了",(d,w)->finishSaved()).show();else finishSaved();}
    @Override public void onBackPressed(){confirmFinish();}
    private void fail(String message){opening=false;shutter.setEnabled(false);status.setText(message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void closeCamera(){
        generation++;boolean acquired=false;
        try{acquired=lock.tryAcquire(1800,TimeUnit.MILLISECONDS);if(!acquired)return;
            if(cameraSession!=null){cameraSession.close();cameraSession=null;}if(camera!=null){camera.close();camera=null;}if(reader!=null){reader.close();reader=null;}opening=false;
        }catch(InterruptedException e){Thread.currentThread().interrupt();}finally{if(acquired)lock.release();}
    }
    private final class GuideView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        GuideView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float w=getWidth(),h=getHeight();if(w==0||h==0||slot()==null)return;boolean top=slot().optString("key").equals("top");
            float ow=w*.67f,oh=top?Math.min(ow,h*.72f):Math.min(h*.7f,w*.96f),cy=h*.48f;
            RectF oval=new RectF(w/2-ow/2,cy-oh/2,w/2+ow/2,cy+oh/2);
            Path tint=new Path();tint.setFillType(Path.FillType.EVEN_ODD);tint.addRect(0,0,w,h,Path.Direction.CW);tint.addOval(oval,Path.Direction.CW);paint.setStyle(Paint.Style.FILL);paint.setColor(0x39231b33);canvas.drawPath(tint,paint);
            paint.setColor(0xddffffff);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setPathEffect(new DashPathEffect(new float[]{dp(8),dp(6)},0));canvas.drawOval(oval,paint);
            paint.setStrokeWidth(dp(1));paint.setColor(0x75ffffff);canvas.drawLine(w/2,oval.top-dp(12),w/2,oval.bottom+dp(12),paint);canvas.drawLine(oval.left-dp(12),cy,oval.right+dp(12),cy,paint);paint.setPathEffect(null);
            float centerX=dp(49),centerY=dp(48),radius=dp(29);paint.setStyle(Paint.Style.FILL);paint.setColor(0x89251e34);canvas.drawCircle(centerX,centerY,radius+dp(13),paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(0xaae4d7fa);paint.setStrokeWidth(dp(1));canvas.drawCircle(centerX,centerY,radius,paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xffd1bfeb);canvas.drawOval(new RectF(centerX-dp(9),centerY-dp(10),centerX+dp(9),centerY+dp(10)),paint);
            Path nose=new Path();nose.moveTo(centerX-dp(3),centerY+dp(9));nose.lineTo(centerX,centerY+dp(14));nose.lineTo(centerX+dp(3),centerY+dp(9));canvas.drawPath(nose,paint);
            double a=slot().optDouble("azimuth")*Math.PI/180;float x=top?centerX:centerX-(float)Math.sin(a)*radius,y=top?centerY:centerY+(float)Math.cos(a)*radius;
            paint.setColor(Color.WHITE);canvas.drawCircle(x,y,dp(6),paint);paint.setColor(0xff8275bf);canvas.drawCircle(x,y,dp(3),paint);
            paint.setColor(0xffeee4fb);paint.setTextSize(dp(10));paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("撮る人の位置",centerX,centerY+radius+dp(24),paint);
            paint.setTextSize(dp(12));paint.setColor(0xddffffff);canvas.drawText(top?"つむじを中心に":"髪の先まで枠に入れよう",w/2,Math.min(h-dp(16),oval.bottom+dp(28)),paint);
        }
    }
}
