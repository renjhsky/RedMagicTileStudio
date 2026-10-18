package io.github.renjhtools.redmagictiles;

import android.app.Activity;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** No Root required for app UI. All changes are behind explicit user opt-in. */
public class MainActivity extends Activity {
 private static final int ACCENT = 0xff746af4, BG=0xfff6f6f9, INK=0xff242535, MUTED=0xff737583;
 private SharedPreferences prefs;
 private Preview preview;
 private TextView state;
 private boolean editing = false;
 private long lastNotify = 0;
 private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
 private boolean notifyPending;
 private int d(float dp) {return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,dp,getResources().getDisplayMetrics()));}
 @Override public void onCreate(Bundle b){super.onCreate(b);
  prefs=Prefs.get(this,"tiles");
  getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  ScrollView scroll =new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
  LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(d(18),d(26),d(18),d(36));
  scroll.addView(body);setContentView(scroll);
  TextView title=text("红魔磁贴布局",26,INK,true);body.addView(title);
  TextView subtitle=text("v"+BuildConfig.VERSION_NAME+" · NX789J · REDMAGIC OS 11",12,MUTED,false);addSpace(body,5);body.addView(subtitle);
  addSpace(body,18);
  LinearLayout onCard=card(body);
  Switch enabled=new Switch(this);enabled.setText("启用原生控制中心磁贴优化");enabled.setTextColor(INK);enabled.setTextSize(15);
  enabled.setChecked(prefs.getBoolean("enabled",false));
  onCard.addView(enabled);
  enabled.setOnCheckedChangeListener((button,checked)->saveBool("enabled",checked,true));
  TextView warn=text("在 Vector 中启用本模块，作用域勾选系统界面 / SystemUI，然后重启手机。打开此开关后展开控制中心，再点下方“检测实际生效状态”。首次安装默认关闭。",12,MUTED,false);onCard.addView(warn);
  addSpace(body,14);
  LinearLayout previewCard=card(body);
  TextView head=text("磁贴实时预览",17,INK,true);previewCard.addView(head);addSpace(previewCard,9);
  preview=new Preview(this);previewCard.addView(preview,new LinearLayout.LayoutParams(-1,d(256)));
  TextView legend=text("上方仅为布局模拟；实际红魔磁贴由 SystemUI 绘制。调节过程中这里立即更新，SystemUI 同步尝试热刷新。",11,MUTED,false);previewCard.addView(legend);
  addSpace(body,14);
  LinearLayout cfg=card(body);cfg.addView(text("布局设置",17,INK,true));
  cfg.addView(text("圆形磁贴两侧自动对齐上方卡片；横向间距设置最小留白，剩余空间均匀分配。",12,MUTED,false));
  addSlider(cfg,"竖屏每行磁贴列数", "portraitColumns",3,7,5," 列",1);
  addSlider(cfg,"横屏每行磁贴列数", "landscapeColumns",3,8,6," 列",1);
  addSlider(cfg,"圆形磁贴视觉尺寸", "size",65,110,85,"%",5);
  addSlider(cfg,"最小横向间距", "gapH",0,24,8," dp",1);
  addSlider(cfg,"纵向间距", "gapV",0,28,12," dp",1);
  addSlider(cfg,"磁贴文字大小", "fontSp",8,16,11," sp",1);
  addSpace(cfg,9);
  Switch labels=new Switch(this);labels.setText("显示磁贴名称");labels.setTextSize(14);labels.setChecked(prefs.getBoolean("labels",true));labels.setTextColor(INK);
  cfg.addView(labels);
  labels.setOnCheckedChangeListener((b1,value)->saveBool("labels",value,false));
  addSpace(body,14);
  LinearLayout action=card(body);action.addView(text("恢复与诊断",17,INK,true));addSpace(action,10);
  Button reset=button("恢复推荐参数（保留启用状态）");action.addView(reset);
  reset.setOnClickListener(v->{reset(false);recreate();});
  Button stock=button("一键恢复原厂布局（关闭模块效果）");action.addView(stock);
  stock.setOnClickListener(v->{reset(true);recreate();});
  Button status=button("检测实际生效状态");action.addView(status);
  status.setOnClickListener(v->checkRuntime(status));
  state=text("建议先启用 5 列进行测试，不要同时运行其他控制中心布局模块。",12,MUTED,false);addSpace(body,12);body.addView(state);
  addSpace(body,14);
  LinearLayout updates=card(body);updates.addView(text("版本与更新",17,INK,true));
  updates.addView(text("当前版本 v"+BuildConfig.VERSION_NAME+" · 在 GitHub 发布页查看新版安装包和更新说明。",12,MUTED,false));
  addSpace(updates,10);
  Button update=button("查看更新（GitHub）");updates.addView(update);
  update.setOnClickListener(v->openProject(true));
  Button project=button("项目主页");updates.addView(project);
  project.setOnClickListener(v->openProject(false));
 }
 private void openProject(boolean releases){
  if(BuildConfig.GITHUB_REPOSITORY.isEmpty()){
   new android.app.AlertDialog.Builder(this).setTitle("更新页面尚未配置")
    .setMessage("此构建尚未关联 GitHub 仓库。请使用项目发布页提供的安装包。")
    .setPositiveButton("关闭",null).show();return;
  }
  String url="https://github.com/"+BuildConfig.GITHUB_REPOSITORY+(releases?"/releases":"");
  try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}
  catch(ActivityNotFoundException e){
   new android.app.AlertDialog.Builder(this).setTitle("无法打开浏览器").setMessage(url)
    .setPositiveButton("复制链接",(dialog,which)->{
     android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
     clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GitHub",url));
     android.widget.Toast.makeText(this,"链接已复制",android.widget.Toast.LENGTH_SHORT).show();
    }).setNegativeButton("关闭",null).show();
  }
 }
 private void reset(boolean disable){SharedPreferences.Editor e=prefs.edit();
  e.putInt("portraitColumns",5).putInt("landscapeColumns",6).putInt("size",85).putInt("gapH",8).putInt("gapV",12).putInt("fontSp",11).putBoolean("labels",true);
  if(disable)e.putBoolean("enabled",false);e.apply();notifySystem();}
 private void saveInt(String key,int value,boolean notify){prefs.edit().putInt(key,value).apply();if(preview!=null)preview.invalidate();if(notify)throttledNotify();}
 private void saveBool(String key,boolean value,boolean immediate){prefs.edit().putBoolean(key,value).apply();if(preview!=null)preview.invalidate();if(immediate)notifySystem();else throttledNotify();}
 private void throttledNotify(){
  long now=SystemClock.elapsedRealtime();if(now-lastNotify>110){notifySystem();return;}
  if(notifyPending)return;notifyPending=true;
  handler.postDelayed(()->{notifyPending=false;notifySystem();},115);
 }
 private void notifySystem(){
  lastNotify=SystemClock.elapsedRealtime();
  prefs.edit().putInt("revision",prefs.getInt("revision",0)+1).apply();
  try{getContentResolver().notifyChange(ConfigProvider.uri(),null);}catch(Throwable e){if(state!=null)state.setText("通知系统失败："+e);}
 }
 private void checkRuntime(Button button){
  button.setEnabled(false);button.setText("正在等待 SystemUI 应答…");
  long requested=SystemClock.elapsedRealtime();notifySystem();final int revision=prefs.getInt("revision",0);
  handler.postDelayed(()->{
   if(isFinishing()||isDestroyed())return;
   button.setEnabled(true);button.setText("检测实际生效状态");
   String report,history="暂无已恢复记录。";
   try{
    Bundle b=getContentResolver().call(ConfigProvider.uri(),"status",null,null);
    long received=b==null?-1:b.getLong("received",-1);
    boolean fresh=b!=null && received>=requested && received<=SystemClock.elapsedRealtime();
    boolean versionMatches=b!=null && BuildConfig.VERSION_CODE==b.getInt("versionCode",-1);
    boolean synced=b!=null && b.getBoolean("configRead",false) && b.getInt("revision",-1)==revision;
    String result=!fresh?"未收到本次应答":!versionMatches?"SystemUI 仍加载旧版，请重启手机":
      !synced?"SystemUI 已应答，配置同步待恢复":"SystemUI 已应答";
    report="设置程序：v"+BuildConfig.VERSION_NAME+"\n机型："+Build.MODEL+" / Android SDK "+Build.VERSION.SDK_INT+
     "\n期望竖屏 / 横屏列数："+prefs.getInt("portraitColumns",5)+" / "+prefs.getInt("landscapeColumns",6)+
     "\n本次检测："+result+"\n\n"+
     (b==null?"没有运行报告。":b.getString("report","没有运行报告。"));
    if(!fresh)report+="\n\n以上可能是旧记录。请先完全展开一次控制中心，再回来检测；仍无应答时，在 Vector 启用本模块并勾选系统界面后重启手机。";
    if(b!=null)history=b.getString("history","暂无已恢复记录。");
   }catch(Throwable e){report="读取运行报告失败："+e;}
   final String recoveredHistory=history;
   final String diagnostic=report+"\n\n已恢复的历史记录\n"+history;
   new android.app.AlertDialog.Builder(this).setTitle("实际生效诊断").setMessage(report)
    .setNeutralButton("复制诊断",(dialog,which)->{
     android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
     clipboard.setPrimaryClip(android.content.ClipData.newPlainText("RedMagicTileStudio",diagnostic));
     android.widget.Toast.makeText(this,"诊断信息已复制",android.widget.Toast.LENGTH_SHORT).show();
    }).setNegativeButton("历史记录",(dialog,which)->new android.app.AlertDialog.Builder(this)
     .setTitle("已恢复的历史记录").setMessage(recoveredHistory).setPositiveButton("关闭",null).show())
    .setPositiveButton("关闭",null).show();
  },900);
 }
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
 private void addSlider(LinearLayout parent,String label,String key,int min,int max,int defaultVal,String suffix,int step){
  addSpace(parent,11);
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
  TextView name=text(label,14,INK,false);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
  TextView amount=text("",13,ACCENT,true);row.addView(amount);parent.addView(row);
  SeekBar bar=new SeekBar(this);bar.setMax((max-min)/step);
  bar.setProgress((TileConfig.clamp(prefs.getInt(key,defaultVal),min,max)-min)/step);
  amount.setText((min+bar.getProgress()*step)+suffix);
  parent.addView(bar);
  bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   @Override public void onProgressChanged(SeekBar s,int v,boolean fromUser){int val=min+v*step;amount.setText(val+suffix);if(fromUser)saveInt(key,val,true);}
   @Override public void onStartTrackingTouch(SeekBar s){}
   @Override public void onStopTrackingTouch(SeekBar s){notifySystem();}
  });
 }
 private LinearLayout card(LinearLayout parent){
  LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(d(17),d(16),d(17),d(16));
  GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(d(22));c.setBackground(bg);
  parent.addView(c,new LinearLayout.LayoutParams(-1,-2));return c;
 }
 private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
 private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(INK);b.setAllCaps(false);return b;}
 private void addSpace(LinearLayout p,int dp){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(1,d(dp)));}

 private final class Preview extends View {
  final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);final String[] names={"铃声","热点","截屏","手电筒","位置","设备","风扇","互传","旋转","游戏","录屏","勿扰","省电","扫码","护眼","蓝牙","NFC","同步"};
  Preview(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
  @Override protected void onDraw(Canvas c){super.onDraw(c);
   int w=getWidth(),h=getHeight();int columns=prefs.getInt("portraitColumns",5);
   float horizontal=d(prefs.getInt("gapH",8)),vertical=d(prefs.getInt("gapV",12));
   float left=d(2),usable=w-d(4),cell=(usable-(columns-1)*horizontal)/columns;
   boolean labels=prefs.getBoolean("labels",true);int count=Math.min(18,columns*3);
   float size=prefs.getInt("size",85)/100f;
   float radius=Math.min(cell/2-d(3),d(27))*size;
   float textPx=d(prefs.getInt("fontSp",11))*0.85f;
   float rowH= labels? d(64)+vertical : d(52)+vertical;
   for(int i=0;i<count;i++){
    int col=i%columns,row=i/columns;
    float x=left+col*(cell+horizontal)+cell/2;
    float y=d(12)+row*rowH+Math.max(d(25),radius);
    p.setColor(i%6==0?ACCENT:0xffe9e9ef);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,radius,p);
    p.setColor(i%6==0?Color.WHITE:0xff676879);
    p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));p.setTextSize(radius*.91f);p.setTextAlign(Paint.Align.CENTER);
    c.drawText(new String[]{"◉","⌁","▣","✦","●","◎","✳","⇄","↻","⬡","◉","☾","▰","⊞","◐","ᛒ","N","↻"}[i],x,y+radius*.3f,p);
    if(labels){p.setColor(0xff55576a);p.setTextSize(Math.min(textPx,cell*.25f));p.setTypeface(Typeface.DEFAULT);c.drawText(names[i],x,y+radius+d(13),p);}
   }
   p.setTextAlign(Paint.Align.LEFT);
  }
 }
}
