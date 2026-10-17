package jp.yaegaki.kururihair;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Pause a local clip, inspect nearby frames, and atomically save each direction. */
public final class VideoFrameActivity extends Activity {
    private HairStore store;
    private String id;
    private File sourceFile;
    private JSONArray slots;
    private final Set<String> filled=new HashSet<>();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private MediaMetadataRetriever retriever;
    private VideoView video;
    private ImageView still;
    private Spinner directions;
    private SeekBar timeline;
    private TextView status,timeLabel,counter,hint;
    private Button play,previous,next,save,done;
    private Bitmap frameBitmap;
    private int selected=0,accepted=0,timeMs=0,durationMs=0,frameStepMs=33,loadedAtMs=-1;
    private volatile int frameGeneration=0;
    private volatile boolean closed=false;
    private boolean ready=false,saving=false,playing=false,dragging=false,videoPrepared=false,updatingSpinner=false;
    private Runnable pendingSeek;
    private final Runnable tick=new Runnable(){public void run(){
        if(closed||!playing)return;
        timeMs=clamp(video.getCurrentPosition());updateTime();ui.postDelayed(this,100);
    }};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(34,29,46));getWindow().setNavigationBarColor(Color.rgb(34,29,46));getWindow().getDecorView().setSystemUiVisibility(0);
        store=HairStore.get(this);id=getIntent().getStringExtra("id");
        try{
            JSONObject session=store.find(id);slots=new JSONArray(getIntent().getStringExtra("slots"));
            if(slots.length()==0||slots.length()>25)throw new IOException("撮影方向が正しくありません。");
            sourceFile=VideoFilesProvider.checkedFile(this,getIntent().getStringExtra("path"));
            if(!sourceFile.isFile()||sourceFile.length()==0)throw new IOException("動画が見つかりません。もう一度選んでください。");
            Iterator<String> keys=session.getJSONObject("frames").keys();while(keys.hasNext())filled.add(keys.next());
            String start=getIntent().getStringExtra("key");boolean found=false;
            for(int i=0;i<slots.length();i++)if(slots.getJSONObject(i).getString("key").equals(start)){selected=i;found=true;break;}
            if(!found)for(int i=0;i<slots.length();i++)if(!filled.contains(slots.getJSONObject(i).getString("key"))){selected=i;break;}
            if(saved!=null){selected=Math.max(0,Math.min(slots.length()-1,saved.getInt("selected",selected)));timeMs=saved.getInt("time",0);accepted=saved.getInt("accepted",0);}
        }catch(Exception e){Toast.makeText(this,error(e),Toast.LENGTH_LONG).show();finish();return;}
        buildUI();refreshDirections();loadVideo();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private Button button(String value,boolean primary){
        Button b=new Button(this);b.setText(value);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(primary?Color.rgb(46,34,64):Color.rgb(219,208,234));
        b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(8),dp(4),dp(8),dp(4));
        GradientDrawable bg=new GradientDrawable();bg.setColor(primary?Color.rgb(202,185,240):Color.rgb(47,40,62));bg.setCornerRadius(dp(13));b.setBackground(bg);return b;
    }
    private void buildUI(){
        int muted=Color.rgb(189,180,205);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(34,29,46));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(14),dp(8),dp(14),dp(8));
        done=button("完了",false);header.addView(done,new LinearLayout.LayoutParams(dp(62),dp(42)));done.setOnClickListener(v->finishSaved());
        TextView title=text("動画から写真を選ぶ",17,Color.WHITE);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));
        counter=text("",12,muted);counter.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);header.addView(counter,new LinearLayout.LayoutParams(dp(66),dp(42)));root.addView(header);
        LinearLayout directionPanel=new LinearLayout(this);directionPanel.setOrientation(LinearLayout.VERTICAL);directionPanel.setPadding(dp(18),0,dp(18),dp(6));
        directionPanel.addView(text("この場面を保存する方向",12,muted));
        directions=new Spinner(this);directions.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.rgb(47,40,62)));
        directionPanel.addView(directions,new LinearLayout.LayoutParams(-1,dp(44)));
        hint=text("右・左はお子さんから見た方向です。",12,muted);directionPanel.addView(hint,new LinearLayout.LayoutParams(-1,dp(42)));root.addView(directionPanel);
        directions.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long row){if(updatingSpinner||saving)return;selected=position;updateDirection();}});
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(Color.BLACK);
        video=new VideoView(this);stage.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        still=new ImageView(this);still.setScaleType(ImageView.ScaleType.FIT_CENTER);still.setBackgroundColor(Color.BLACK);stage.addView(still,new FrameLayout.LayoutParams(-1,-1));
        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(dp(18),dp(6),dp(18),dp(12));
        timeLabel=text("動画を準備しています…",12,muted);timeLabel.setGravity(Gravity.CENTER);controls.addView(timeLabel,new LinearLayout.LayoutParams(-1,dp(24)));
        timeline=new SeekBar(this);timeline.setEnabled(false);timeline.setContentDescription("動画の再生位置");controls.addView(timeline,new LinearLayout.LayoutParams(-1,dp(36)));
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){if(saving)return;dragging=true;stopPlayback();}
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){if(!fromUser||!ready||saving)return;timeMs=clamp(progress);save.setEnabled(false);frameGeneration++;updateTimeLabel();if(pendingSeek!=null)ui.removeCallbacks(pendingSeek);pendingSeek=()->requestFrame(timeMs);ui.postDelayed(pendingSeek,140);}
            public void onStopTrackingTouch(SeekBar bar){dragging=false;if(pendingSeek!=null)ui.removeCallbacks(pendingSeek);requestFrame(timeMs);}
        });
        LinearLayout transport=new LinearLayout(this);transport.setGravity(Gravity.CENTER);previous=button("◀ 前のコマ",false);play=button("▶ 再生",false);next=button("次のコマ ▶",false);
        transport.addView(previous,new LinearLayout.LayoutParams(0,dp(42),1));LinearLayout.LayoutParams mid=new LinearLayout.LayoutParams(0,dp(42),1);mid.setMargins(dp(8),0,dp(8),0);transport.addView(play,mid);transport.addView(next,new LinearLayout.LayoutParams(0,dp(42),1));controls.addView(transport);
        previous.setContentDescription("前のコマへ");next.setContentDescription("次のコマへ");previous.setOnClickListener(v->step(-1));next.setOnClickListener(v->step(1));play.setOnClickListener(v->{if(playing)pauseAtCurrent();else startPlayback();});
        status=text("読み込み中…",12,muted);status.setGravity(Gravity.CENTER);controls.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));
        save=button("この場面を写真にして保存",true);save.setEnabled(false);controls.addView(save,new LinearLayout.LayoutParams(-1,dp(48)));save.setOnClickListener(v->saveFrame());
        TextView note=text("写真は1枚ずつ保存。今回の動画のコピーは完了時に削除。",10,muted);note.setGravity(Gravity.CENTER);controls.addView(note,new LinearLayout.LayoutParams(-1,dp(27)));root.addView(controls);setContentView(root);
        setControls(false);
        if(Build.VERSION.SDK_INT>=35){root.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});root.requestApplyInsets();}
    }
    private void loadVideo(){
        io.submit(()->{
            try{
                retriever=new MediaMetadataRetriever();retriever.setDataSource(sourceFile.getPath());
                long duration=Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                if(duration<1||duration>300000)throw new IOException("動画は5分までです。短い動画を選んでください。");
                double fps=30;try{String raw=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);if(raw!=null)fps=Double.parseDouble(raw);}catch(Exception ignored){}
                // Capture rate can differ from playback rate for slow-motion recordings.
                if(Build.VERSION.SDK_INT>=28)try{String raw=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);if(raw!=null){long frames=Long.parseLong(raw);if(frames>0)fps=frames*1000.0/duration;}}catch(Exception ignored){}
                if(Double.isNaN(fps)||Double.isInfinite(fps)||fps<1||fps>240)fps=30;
                final int durationValue=(int)duration,step=Math.max(4,(int)Math.round(1000/fps));
                ui.post(()->{
                    if(closed)return;durationMs=durationValue;frameStepMs=step;timeMs=clamp(timeMs);ready=true;timeline.setMax(Math.max(0,durationMs-1));setControls(true);updateTime();
                    video.setOnPreparedListener(player->{if(closed)return;videoPrepared=true;player.setVolume(0,0);video.seekTo(timeMs);});
                    video.setOnCompletionListener(player->{if(closed)return;timeMs=Math.max(0,durationMs-1);stopPlayback();requestFrame(timeMs);});
                    video.setOnErrorListener((player,what,extra)->{videoPrepared=false;stopPlayback();if(ready){status.setText("動画の再生はできません。スライダーで写真を選べます。");play.setEnabled(false);}return true;});
                    video.setVideoPath(sourceFile.getPath());requestFrame(timeMs);
                });
            }catch(Exception e){ui.post(()->fail(error(e)));}
        });
    }
    private int clamp(int time){return Math.max(0,Math.min(Math.max(0,durationMs-1),time));}
    private void updateTime(){if(!dragging)timeline.setProgress(timeMs);updateTimeLabel();}
    private void updateTimeLabel(){timeLabel.setText(String.format(Locale.JAPAN,"%.2f 秒 / %.2f 秒",timeMs/1000.0,durationMs/1000.0));}
    private void stopPlayback(){
        if(video==null)return;playing=false;video.pause();ui.removeCallbacks(tick);still.setVisibility(View.VISIBLE);play.setText("▶ 再生");
    }
    private void pauseAtCurrent(){if(!ready||saving)return;if(playing)timeMs=clamp(video.getCurrentPosition());stopPlayback();updateTime();requestFrame(timeMs);}
    private void startPlayback(){
        if(!ready||saving)return;if(!videoPrepared){status.setText("動画を再生する準備ができていません。スライダーで場面を選べます。");return;}
        if(timeMs>=durationMs-100)timeMs=0;
        frameGeneration++;if(pendingSeek!=null)ui.removeCallbacks(pendingSeek);save.setEnabled(false);still.setVisibility(View.GONE);playing=true;play.setText("Ⅱ 一時停止");
        video.seekTo(timeMs);video.start();status.setText("良い場面で一時停止してください。");ui.post(tick);
    }
    private void step(int delta){
        if(!ready||saving)return;if(playing)timeMs=clamp(video.getCurrentPosition());stopPlayback();timeMs=clamp(timeMs+delta*frameStepMs);updateTime();requestFrame(timeMs);
    }
    private void requestFrame(int time){
        if(!ready||saving||closed)return;timeMs=clamp(time);updateTime();save.setEnabled(false);status.setText("この場面を読み込んでいます…");
        final int ticket=++frameGeneration,requested=timeMs;
        io.submit(()->{
            if(closed||ticket!=frameGeneration)return;
            Bitmap bitmap=null;
            try{
                // Android's retriever already applies the video's rotation metadata. Do not rotate again.
                if(Build.VERSION.SDK_INT>=27)bitmap=retriever.getScaledFrameAtTime(requested*1000L,MediaMetadataRetriever.OPTION_CLOSEST,1800,1800);
                else bitmap=retriever.getFrameAtTime(requested*1000L,MediaMetadataRetriever.OPTION_CLOSEST);
                if(bitmap==null)throw new IOException("この場面を読み込めません。少し前後へ移動してください。");
                int largest=Math.max(bitmap.getWidth(),bitmap.getHeight());
                if(largest>1800){double factor=1800.0/largest;Bitmap smaller=Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)Math.round(bitmap.getWidth()*factor)),Math.max(1,(int)Math.round(bitmap.getHeight()*factor)),true);if(smaller!=bitmap)bitmap.recycle();bitmap=smaller;}
                final Bitmap result=bitmap;bitmap=null;
                ui.post(()->{
                    if(closed||ticket!=frameGeneration||playing){result.recycle();return;}
                    Bitmap old=frameBitmap;frameBitmap=result;loadedAtMs=requested;still.setImageBitmap(result);still.setVisibility(View.VISIBLE);if(old!=null&&old!=result)old.recycle();
                    save.setEnabled(!saving);status.setText("方向を選び、この場面を保存してください。");
                });
            }catch(Exception|OutOfMemoryError e){if(bitmap!=null)bitmap.recycle();ui.post(()->{if(!closed&&ticket==frameGeneration){save.setEnabled(false);status.setText(e instanceof OutOfMemoryError?"動画の解像度が大きすぎます。小さい動画を選んでください。":error(e));}});}
        });
    }
    private void setControls(boolean enabled){directions.setEnabled(enabled);timeline.setEnabled(enabled);previous.setEnabled(enabled);next.setEnabled(enabled);play.setEnabled(enabled);done.setEnabled(!saving);}
    private void refreshDirections(){
        if(closed)return;updatingSpinner=true;ArrayList<String> labels=new ArrayList<>();
        for(int i=0;i<slots.length();i++){JSONObject s=slots.optJSONObject(i);labels.add((filled.contains(s.optString("key"))?"✓ ":"") + s.optString("label"));}
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,labels){
            @Override public View getView(int p,View v,android.view.ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(Color.WHITE);t.setTextSize(18);return t;}
            @Override public View getDropDownView(int p,View v,android.view.ViewGroup parent){TextView t=(TextView)super.getDropDownView(p,v,parent);t.setTextColor(Color.WHITE);t.setTextSize(16);t.setPadding(dp(16),dp(13),dp(16),dp(13));return t;}
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);directions.setAdapter(adapter);directions.setSelection(selected);updatingSpinner=false;updateDirection();
        int count=0;for(int i=0;i<slots.length();i++)if(filled.contains(slots.optJSONObject(i).optString("key")))count++;counter.setText(count+" / "+slots.length());
    }
    private void updateDirection(){
        JSONObject slot=slots.optJSONObject(selected);if(slot==null)return;
        String key=slot.optString("key");hint.setText(key.equals("top")?"頭の真上からの場面。つむじを中央に。":slot.optInt("row")>0?"この方向から、約35°見下ろした場面を選びます。":"頭と同じ高さの場面。右・左はお子さんから見た方向です。");
        save.setText(filled.contains(key)?"この方向の写真を置き換える":"「"+slot.optString("label")+"」として保存");
    }
    private void saveFrame(){
        if(!ready||saving||playing||frameBitmap==null||loadedAtMs!=timeMs||!save.isEnabled())return;
        saving=true;frameGeneration++;if(pendingSeek!=null)ui.removeCallbacks(pendingSeek);setControls(false);save.setEnabled(false);status.setText("写真を保存しています…");
        final Bitmap chosen=frameBitmap;final String key=slots.optJSONObject(selected).optString("key");final int previousSelection=selected;
        io.submit(()->{
            File temp=new File(getCacheDir(),"video-frame-"+UUID.randomUUID()+".jpg");
            try{
                try(FileOutputStream out=new FileOutputStream(temp)){if(!chosen.compress(Bitmap.CompressFormat.JPEG,94,out))throw new IOException("写真に変換できませんでした。");}
                store.addPhoto(id,key,temp);
                ui.post(()->{
                    if(closed)return;saving=false;accepted++;filled.add(key);
                    for(int offset=1;offset<=slots.length();offset++){int candidate=(previousSelection+offset)%slots.length();if(!filled.contains(slots.optJSONObject(candidate).optString("key"))){selected=candidate;break;}}
                    refreshDirections();setControls(true);save.setEnabled(true);
                    status.setText(filled.size()>=slots.length()?"すべての方向を保存しました。「完了」で戻れます。":"保存しました。次の方向の場面を選んでください。");
                });
            }catch(Exception e){ui.post(()->{if(closed)return;saving=false;setControls(true);save.setEnabled(true);status.setText(error(e));});}
            finally{temp.delete();}
        });
    }
    private void finishSaved(){
        if(saving)return;
        setResult(RESULT_OK,new Intent().putExtra("message",accepted>0?"動画から"+accepted+"枚の写真を保存しました。":"動画の選択を終了しました。保存済みの写真は残っています。"));finish();
    }
    private void fail(String message){if(closed)return;new android.app.AlertDialog.Builder(this).setMessage(message).setPositiveButton("戻る",(d,w)->finishSaved()).setOnCancelListener(d->finishSaved()).show();}
    private static String error(Throwable e){String message=e.getMessage();return message==null||message.length()>180?"この動画を読み込めませんでした。別の動画を選んでください。":message;}
    @Override public void onBackPressed(){finishSaved();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("selected",selected);out.putInt("time",playing?clamp(video.getCurrentPosition()):timeMs);out.putInt("accepted",accepted);}
    @Override protected void onPause(){if(playing)pauseAtCurrent();super.onPause();}
    @Override protected void onDestroy(){
        closed=true;frameGeneration++;ui.removeCallbacksAndMessages(null);if(video!=null)video.stopPlayback();
        final Bitmap bitmap=frameBitmap;frameBitmap=null;final boolean removeSource=isFinishing();
        io.submit(()->{try{if(retriever!=null)retriever.release();}catch(Exception ignored){}if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();if(removeSource&&sourceFile!=null)sourceFile.delete();});
        io.shutdown();super.onDestroy();
    }
}
