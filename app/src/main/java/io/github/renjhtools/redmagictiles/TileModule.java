package io.github.renjhtools.redmagictiles;

import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** Hooks only NX789J circular tile grids, including the OS 11 control center. */
public final class TileModule extends XposedModule {
 static final String VERSION = BuildConfig.VERSION_NAME;
 private static final String LAYOUT = "com.zte.mifavor.qs.MfvTileLayout";
 private static final String PAGED = "com.zte.mifavor.qs.MfvPagedTileLayout";
 private static final String CONTROL = "com.zte.controlcenter.view.ControlCenterTileLayout";
 private static final String TOP = "com.zte.controlcenter.view.ControlCenterTopPanel";
 private static final String PANEL = "com.zte.controlcenter.view.ControlCenterPanelView";
 private static final String CIRCLE = "com.zte.qs.tileimpl.QSTileViewCircle";
 private static final Handler MAIN = new Handler(Looper.getMainLooper());
 private static final ArrayList<WeakReference<View>> VIEWS = new ArrayList<>();
 private static final WeakHashMap<View,GridGeometry> GEOMETRIES = new WeakHashMap<>();
 private static final WeakHashMap<View,Integer> ORIGINAL_MAX = new WeakHashMap<>();
 private static final WeakHashMap<View,TileStyle> STYLES = new WeakHashMap<>();
 private static TileModule instance;
 private static Context context;
 private static TileConfig config = new TileConfig();
 private static boolean ready, failed, observing, configRead, hasConfig, retryScheduled;
 private static int hookCount;
 private static String layoutName = "尚未测量磁贴布局";
 private static final RuntimeDiagnostics DIAGNOSTICS = new RuntimeDiagnostics();
 private static final RetryPolicy CONFIG_RETRY = new RetryPolicy();
 private static final String CONFIG_ERROR="配置读取", BRIDGE_ERROR="配置监听", REFRESH_ERROR="布局刷新", MODULE_ERROR="模块执行";
 private static final Runnable RETRY_CONFIG = () -> {
  retryScheduled=false;
  if(context==null || failed)return;
  if(reload())refreshAll();
  MAIN.postDelayed(()->report(true),220);
 };
 private static int actualColumns, actualRows, actualCount, actualWidth, actualHeight;
 private static int actualLeft, actualRight;
 private static long lastReport;
 private static Method setMeasured;

 @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
  if (!"com.android.systemui".equals(param.getPackageName()) || ready) return;
  instance = this;
  if (!("NX789J".equalsIgnoreCase(Build.MODEL) || "NX789J".equalsIgnoreCase(Build.DEVICE))) {
   note("Unsupported device: " + Build.MODEL); return;
  }
  try {
   ClassLoader cl = param.getClassLoader();
   Class<?> layout = Class.forName(LAYOUT,false,cl);
   Class<?> control = Class.forName(CONTROL,false,cl);
   Class<?> circle = Class.forName(CIRCLE,false,cl);
   Class<?> paged = Class.forName(PAGED,false,cl);
   for (String f : new String[]{"mColumns","mRows","mCellWidth","mCellHeight","mCellMarginHorizontal",
                                "mCellMarginVertical","mCellMarginTop","mRecords","mMaxColumns"}) layout.getDeclaredField(f);
   control.getDeclaredField("tileType");
   for (String name : new String[]{"updateColumns","updateResources","getTileLayoutHeight"}) find(layout,name);
   find(layout,"layoutChildren",int.class,int.class);
   find(layout,"getColumnStart",int.class);
   find(layout,"updateMaxRows",int.class,int.class);
   find(control,"onMeasure",int.class,int.class);
   find(circle,"onMeasure",int.class,int.class);
   find(paged,"onMeasure",int.class,int.class);
   find(paged,"setMaxColumns",int.class);
   setMeasured = find(View.class,"setMeasuredDimension",int.class,int.class);

   intercept(layout,"updateColumns",new Class<?>[0],c -> {
    Object result = c.proceed(); View v = (View)c.getThisObject();
    if (eligible(v)) {
     bootstrap(v);
     if (config.enabled && !failed) {
      int want = columns(v), old = number(v,"mColumns",want);
      put(v,"mColumns",want); return old != want || Boolean.TRUE.equals(result);
     }
    }
    return result;
   });
   intercept(layout,"updateResources",new Class<?>[0],c -> {
    Object result = c.proceed(); View v = (View)c.getThisObject();
    if (eligible(v)) { bootstrap(v); if (config.enabled && !failed) prepare(v,0); }
    return result;
   });
   // The OS 11 control center calls layoutChildren directly, bypassing base onMeasure.
   intercept(layout,"layoutChildren",new Class<?>[]{int.class,int.class},c -> {
    View v = (View)c.getThisObject();
    if (eligible(v)) {
     bootstrap(v);
     if (config.enabled && !failed && measureGrid(v,(Integer)c.getArg(0),(Integer)c.getArg(1))) return null;
    }
    return c.proceed();
   });
   intercept(layout,"getColumnStart",new Class<?>[]{int.class},c -> {
    View v = (View)c.getThisObject(); GridGeometry g = GEOMETRIES.get(v);
    if (config.enabled && eligible(v) && g != null) return g.columnStart((Integer)c.getArg(0),v.getPaddingStart());
    return c.proceed();
   });
   intercept(layout,"getTileLayoutHeight",new Class<?>[0],c -> {
    View v = (View)c.getThisObject(); GridGeometry g = GEOMETRIES.get(v);
    if (config.enabled && eligible(v) && g != null) return g.height(number(v,"mRows",0),number(v,"mCellMarginTop",0));
    return c.proceed();
   });
   intercept(layout,"updateMaxRows",new Class<?>[]{int.class,int.class},c -> {
    View v = (View)c.getThisObject();
    if (eligible(v)) {
     bootstrap(v);
     if (config.enabled && !failed) {
      GridGeometry g = prepare(v,0);
      if (g != null && isControl(v)) {
       int old = number(v,"mRows",0), rows = g.rows((Integer)c.getArg(1));
       put(v,"mRows",rows); return old != rows;
      }
     }
    }
    return c.proceed();
   });
   intercept(control,"onMeasure",new Class<?>[]{int.class,int.class},c -> {
    View v = (View)c.getThisObject();
    if (eligible(v)) { bootstrap(v); if (config.enabled && !failed) prepare(v,(Integer)c.getArg(0)); }
    Object result = c.proceed(); if (eligible(v)) reportLayout(v); return result;
   });
   intercept(circle,"onMeasure",new Class<?>[]{int.class,int.class},c -> {
    View v = (View)c.getThisObject(); ViewParent parent = v.getParent();
    boolean owned = parent instanceof View && eligible((View)parent);
    if (owned) {
     bootstrap((View)parent);
     if (config.enabled) style(v,GEOMETRIES.get((View)parent)); else restoreStyle(v);
    }
    Object result = c.proceed();
    if (owned && config.enabled && !failed) {
     // QSTileViewCircle resets its measured dimensions to stock QsDimenUtils values.
     int w = (Integer)c.getArg(0), h = (Integer)c.getArg(1);
     if (View.MeasureSpec.getMode(w) == View.MeasureSpec.EXACTLY && View.MeasureSpec.getMode(h) == View.MeasureSpec.EXACTLY)
      setMeasured.invoke(v,View.MeasureSpec.getSize(w),View.MeasureSpec.getSize(h));
    }
    return result;
   });
   intercept(paged,"onMeasure",new Class<?>[]{int.class,int.class},c -> {
    View v = (View)c.getThisObject(); bootstrap(v);
    if (config.enabled && !failed) for (Object page : pages(v)) if (page instanceof View) prepare((View)page,(Integer)c.getArg(0));
    return c.proceed();
   });
   intercept(paged,"setMaxColumns",new Class<?>[]{int.class},c -> {
    View v = (View)c.getThisObject(); bootstrap(v);
    return config.enabled ? c.proceed(new Object[]{columns(v)}) : c.proceed();
   });
   ready = true;
   for (Method m : new Method[]{find(control,"onMeasure",int.class,int.class),find(layout,"onMeasure",int.class,int.class),
                               find(layout,"layoutTileRecords",int.class),find(layout,"updateResources")}) {
    try { deoptimize(m); } catch (Throwable t) { note("Deoptimize: " + t); }
   }
   note("v" + VERSION + " installed " + hookCount + " hooks; target=" + CONTROL);
  } catch (Throwable t) { fail("Firmware/API mismatch",t); }
 }

 private interface Intercept { Object run(XposedInterface.Chain c) throws Throwable; }
 private void intercept(Class<?> cls,String name,Class<?>[] sig,Intercept action) throws Exception {
  Method method = find(cls,name,sig);
  hook(method).setId("rmt-"+BuildConfig.VERSION_CODE+"-"+cls.getSimpleName()+"-"+name).intercept(c -> failed ? c.proceed() : action.run(c));
  hookCount++;
 }
 private static boolean eligible(View v) {
  return TargetRules.supported(v.getClass().getName(),isControl(v),isA(v,TOP),number(v,"tileType",-1));
 }
 private static boolean isControl(View v) { return isA(v,CONTROL); }
 private static boolean isA(Object object,String name) {
  for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass()) if (name.equals(c.getName())) return true;
  return false;
 }
 private static int columns(View v) {
  return config.cols(v.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
 }
 private static int px(View v,float dp) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,dp,v.getResources().getDisplayMetrics())); }
 private static int sp(View v,float size) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,size,v.getResources().getDisplayMetrics())); }
 private static GridGeometry prepare(View v,int spec) {
  if (!eligible(v)) return null;
  try {
   int width = spec == 0 ? v.getWidth() : View.MeasureSpec.getSize(spec);
   if (width <= px(v,80)) width = v.getResources().getDisplayMetrics().widthPixels;
   int nativeIcon = px(v,56);
   for (Object r : records(v)) {
    Object tile = value(r,"tileView");
    if (tile instanceof View && isA(tile,CIRCLE)) {
     Object nativeSize = call(tile,"getIconBgSize");
     if (nativeSize instanceof Integer && (Integer)nativeSize > 0) nativeIcon = (Integer)nativeSize;
     break;
    }
   }
   int labelHeight=config.labels ? Math.round(sp(v,config.fontSp)*1.45f)+px(v,7) : 0;
   GridGeometry g;
   if(isControl(v)){
    int[] edges=upperCardEdges(v,width);
    g=GridGeometry.aligned(width,edges[0],edges[1],columns(v),px(v,config.gapH),px(v,config.gapV),
                           nativeIcon,config.size,labelHeight,px(v,4),px(v,4));
   }else{
    g=new GridGeometry(width,v.getPaddingStart(),v.getPaddingEnd(),columns(v),px(v,config.gapH),px(v,config.gapV),
                       nativeIcon,config.size,labelHeight,px(v,4),px(v,4));
   }
   GEOMETRIES.put(v,g);
   put(v,"mColumns",g.columns); put(v,"mCellWidth",g.cellWidth); put(v,"mCellHeight",g.cellHeight);
   put(v,"mCellMarginHorizontal",g.gapH); put(v,"mCellMarginVertical",g.gapV);
   if (isControl(v)) put(v,"mCellMarginTop",px(v,4));
   return g;
  } catch (Throwable t) { fail("Prepare grid",t); return null; }
 }
 private static int[] upperCardEdges(View grid,int width){
  // Use the vendor's actual top-card positioning, not a percentage of screen width.
  try{
   for(ViewParent p=grid.getParent();p instanceof View;p=p.getParent()){
    if(!isA(p,PANEL))continue;
    Object object=value(p,"topPanel");
    if(!(object instanceof View))break;
    View top=(View)object;
    int offset=layoutX(top)-layoutX(grid),left=Integer.MAX_VALUE,right=Integer.MIN_VALUE;
    List<?> list=records(top);
    for(int i=0;i<Math.min(4,list.size());i++){
     Object tile=value(list.get(i),"tileView");
     if(!(tile instanceof View))continue;
     int tileWidth=((View)tile).getMeasuredWidth();
     if(tileWidth<=0)tileWidth=number(top,"mCellWidth",0);
     if(tileWidth<=0)continue;
     int x=(Integer)call(top,"getTileLeft",new Class<?>[]{int.class},i);
     left=Math.min(left,offset+x);right=Math.max(right,offset+x+tileWidth);
    }
    // In split landscape the top panel can occupy a separate column; do not use its screen coordinates there.
    if(left!=Integer.MAX_VALUE && left>=0 && right<=width && right-left>=px(grid,80))return new int[]{left,right};
    break;
   }
  }catch(Exception e){note("Card alignment fallback: "+e);}
  int resource=grid.getResources().getIdentifier("control_center_quick_qs_wide_tile_margin_start","dimen","com.android.systemui");
  int inset=resource==0?Math.max(grid.getPaddingLeft(),grid.getPaddingRight()):grid.getResources().getDimensionPixelSize(resource);
  inset=Math.max(0,Math.min(inset,width/4));
  return new int[]{inset,width-inset};
 }
 private static int layoutX(View view){
  int x=0;
  for(View v=view;;){
   x+=v.getLeft();ViewParent parent=v.getParent();
   if(!(parent instanceof View))return x;
   v=(View)parent;x-=v.getScrollX();
  }
 }
 private static boolean measureGrid(View v,int widthSpec,int heightSpec) {
  try {
   GridGeometry g = prepare(v,widthSpec); if (g == null) return false;
   List<?> list = records(v);
   if (isControl(v) || View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.UNSPECIFIED) put(v,"mRows",g.rows(list.size()));
   View previous = v;
   for (Object record : list) {
    Object object = value(record,"tileView"); if (!(object instanceof View)) continue;
    View tile = (View)object; if (tile.getVisibility() == View.GONE) continue;
    if (isA(tile,CIRCLE)) style(tile,g);
    tile.measure(View.MeasureSpec.makeMeasureSpec(g.cellWidth,View.MeasureSpec.EXACTLY),
                 View.MeasureSpec.makeMeasureSpec(g.cellHeight,View.MeasureSpec.EXACTLY));
    Object next = call(tile,"updateAccessibilityOrder",new Class<?>[]{View.class},previous);
    if (next instanceof View) previous = (View)next;
   }
   reportLayout(v); return true;
  } catch (Throwable t) { fail("Measure grid",t); return false; }
 }
 private static void style(View tile,GridGeometry geometry) {
  if (geometry == null) return;
  try {
   TileStyle s = STYLES.get(tile); if (s == null) { s = new TileStyle(tile); STYLES.put(tile,s); }
   s.apply(config,geometry);
  } catch (Throwable t) { fail("Tile style",t); }
 }
 private static void restoreStyle(View tile) {
  TileStyle s = STYLES.remove(tile); if (s != null) try { s.restore(); call(tile,"updateResources"); } catch (Throwable t) { note("Restore style: "+t); }
 }
 private static final class TileStyle {
  final View icon, wrapper, title;
  final int width,height,visibility;
  final float scaleX,scaleY;
  final int left,top,right,bottom;
  final WeakHashMap<TextView,Float> fonts = new WeakHashMap<>();
  TileStyle(View tile) throws Exception {
   icon = (View)call(tile,"getIcon");
   wrapper = icon != null && icon.getParent() instanceof View ? (View)icon.getParent() : null;
   Object t = call(tile,"getMTitleGroup"); if (!(t instanceof View)) t = call(tile,"getLabelContainer");
   title = t instanceof View ? (View)t : null;
   ViewGroup.LayoutParams lp = wrapper == null ? null : wrapper.getLayoutParams();
   width = lp == null ? 0 : lp.width; height = lp == null ? 0 : lp.height;
   scaleX = icon == null ? 1f : icon.getScaleX(); scaleY = icon == null ? 1f : icon.getScaleY();
   visibility = title == null ? View.VISIBLE : title.getVisibility();
   ViewGroup.LayoutParams tp = title == null ? null : title.getLayoutParams();
   ViewGroup.MarginLayoutParams m = tp instanceof ViewGroup.MarginLayoutParams ? (ViewGroup.MarginLayoutParams)tp : null;
   left=m==null?0:m.leftMargin; top=m==null?0:m.topMargin; right=m==null?0:m.rightMargin; bottom=m==null?0:m.bottomMargin;
   if (title != null) rememberFonts(title);
  }
  void rememberFonts(View v) {
   if (v instanceof TextView) fonts.put((TextView)v,((TextView)v).getTextSize());
   if (v instanceof ViewGroup) { ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++) rememberFonts(g.getChildAt(i)); }
  }
  void apply(TileConfig c,GridGeometry g) {
   if (wrapper != null) { ViewGroup.LayoutParams p=wrapper.getLayoutParams();p.width=g.iconSize;p.height=g.iconSize; }
   if (icon != null) { float ratio=width>0 ? g.iconSize/(float)width : c.size/100f;icon.setScaleX(scaleX*ratio);icon.setScaleY(scaleY*ratio); }
   if (title != null) {
    int want=c.labels ? visibility : View.GONE;if(title.getVisibility()!=want)title.setVisibility(want);
    ViewGroup.LayoutParams tp=title.getLayoutParams();
    if (tp instanceof ViewGroup.MarginLayoutParams) {
     ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)tp;m.leftMargin=0;m.rightMargin=0;m.topMargin=px(title,5);m.bottomMargin=0;
    }
    for (TextView text : fonts.keySet()) {
     float wanted=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,c.fontSp,text.getResources().getDisplayMetrics());
     if(Math.abs(text.getTextSize()-wanted)>.5f)text.setTextSize(TypedValue.COMPLEX_UNIT_SP,c.fontSp);
    }
   }
  }
  void restore() {
   if(wrapper!=null){ViewGroup.LayoutParams p=wrapper.getLayoutParams();p.width=width;p.height=height;wrapper.requestLayout();}
   if(icon!=null){icon.setScaleX(scaleX);icon.setScaleY(scaleY);}
   if(title!=null){title.setVisibility(visibility);ViewGroup.LayoutParams tp=title.getLayoutParams();
    if(tp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)tp;m.setMargins(left,top,right,bottom);}
    for(TextView text:fonts.keySet())text.setTextSize(TypedValue.COMPLEX_UNIT_PX,fonts.get(text));title.requestLayout();}
  }
 }

 private static void bootstrap(View v) {
  if (!ready || failed) return;
  boolean known=false;for(WeakReference<View> r:VIEWS)if(r.get()==v){known=true;break;}
  if(!known){VIEWS.removeIf(r->r.get()==null);VIEWS.add(new WeakReference<>(v));ORIGINAL_MAX.put(v,number(v,"mMaxColumns",100));}
  if (observing) return;
  try {
   Context application=v.getContext().getApplicationContext();context=application==null?v.getContext():application;reload();
   context.getContentResolver().registerContentObserver(ConfigProvider.uri(),false,new ContentObserver(MAIN){
    @Override public void onChange(boolean selfChange,Uri uri) {
     MAIN.post(()->{reload();refreshAll();MAIN.postDelayed(()->report(true),220);});
    }
   });
   observing=true;DIAGNOSTICS.success(BRIDGE_ERROR,System.currentTimeMillis());report(true);
  } catch (Throwable t) {
   if(DIAGNOSTICS.failure(BRIDGE_ERROR,t,System.currentTimeMillis()))note("配置监听失败："+t);
  }
 }
 private static boolean reload() {
  try {
   Bundle b=context.getContentResolver().call(ConfigProvider.uri(),"read",null,null);
   if(b==null)throw new IllegalStateException("empty configuration");
   TileConfig next=TileConfig.from(b);if(failed)next.enabled=false;config=next;configRead=true;hasConfig=true;
   DIAGNOSTICS.success(CONFIG_ERROR,System.currentTimeMillis());
   CONFIG_RETRY.reset();MAIN.removeCallbacks(RETRY_CONFIG);retryScheduled=false;
   return true;
  } catch(Throwable t){
   configRead=false;if(!hasConfig)config=new TileConfig();
   if(DIAGNOSTICS.failure(CONFIG_ERROR,t,System.currentTimeMillis()))note("配置读取失败："+t);
   if(!retryScheduled && !failed){
    long delay=CONFIG_RETRY.nextDelayMillis();
    if(delay>=0){retryScheduled=true;MAIN.postDelayed(RETRY_CONFIG,delay);}
   }
   return false;
  }
 }
 private static void refreshAll() {
  boolean success=true;
  for(int i=VIEWS.size()-1;i>=0;i--){
   View v=VIEWS.get(i).get();if(v==null){VIEWS.remove(i);continue;}
   try {
    if(!config.enabled){Integer old=ORIGINAL_MAX.get(v);if(old!=null)put(v,"mMaxColumns",old);GEOMETRIES.remove(v);}
    call(v,"updateResources");
    if(isA(v,PAGED)){
     putBoolean(v,"mDistributeTiles",true);
     for(Object page:pages(v))if(page instanceof View){
      View p=(View)page;if(!config.enabled)GEOMETRIES.remove(p);
      for(Object record:records(p)){Object tile=value(record,"tileView");if(tile instanceof View){if(!config.enabled)restoreStyle((View)tile);((View)tile).requestLayout();}}
     }
    }else if(eligible(v)){
     if(config.enabled && !failed)prepare(v,0);
     for(Object record:records(v)){Object tile=value(record,"tileView");if(tile instanceof View){if(!config.enabled)restoreStyle((View)tile);((View)tile).requestLayout();}}
    }
    v.requestLayout();v.invalidate();
   }catch(Throwable t){success=false;if(DIAGNOSTICS.failure(REFRESH_ERROR,t,System.currentTimeMillis()))note("刷新失败："+t);}
  }
  if(success)DIAGNOSTICS.success(REFRESH_ERROR,System.currentTimeMillis());
 }
 private static void reportLayout(View v) {
  if(!isControl(v) && layoutName.contains("ControlCenter"))return;
  layoutName=v.getClass().getName();actualColumns=number(v,"mColumns",0);actualRows=number(v,"mRows",0);
  actualWidth=number(v,"mCellWidth",0);actualHeight=number(v,"mCellHeight",0);actualCount=records(v).size();
  GridGeometry g=GEOMETRIES.get(v);actualLeft=g==null?0:g.visualLeft;actualRight=g==null?0:g.visualRight;
  report(false);
 }
 private static void report(boolean force) {
  if(context==null)return;long now=SystemClock.elapsedRealtime();if(!force && now-lastReport<1200)return;lastReport=now;
  Bundle b=new Bundle();b.putString("version",VERSION);b.putInt("versionCode",BuildConfig.VERSION_CODE);b.putInt("revision",config.revision);
  b.putBoolean("configRead",configRead);b.putBoolean("failed",failed);b.putString("history",DIAGNOSTICS.historyText());
  b.putString("report","模块版本："+VERSION+"\nSystemUI PID："+Process.myPid()+"\nHook："+hookCount+" 个"+
   "\n配置读取："+(configRead?"成功":hasConfig?"暂不可用，保留上次配置":"等待配置服务")+
   "\n优化状态："+(failed?"发生错误，已回退原厂":config.enabled?"开启":"关闭")+
   "\n实际布局："+layoutName+"\n实际列数："+actualColumns+"\n实际行数："+actualRows+"\n磁贴数量："+actualCount+
   "\n单元格："+actualWidth+" × "+actualHeight+" px\n磁贴左右边缘："+actualLeft+" / "+actualRight+
   " px\n当前运行错误："+DIAGNOSTICS.currentText()+"\n已恢复记录："+DIAGNOSTICS.recoveredCount()+" 项");
  try{context.getContentResolver().call(ConfigProvider.uri(),"report",null,b);}catch(Throwable t){note("Runtime report failed: "+t);}
 }
 private static List<?> records(View v){Object r=value(v,"mRecords");return r instanceof List?(List<?>)r:java.util.Collections.emptyList();}
 private static List<?> pages(View v){Object r=value(v,"mPages");return r instanceof List?(List<?>)r:java.util.Collections.emptyList();}
 private static Method find(Class<?> c,String name,Class<?>... sig) throws NoSuchMethodException {
  for(Class<?> k=c;k!=null;k=k.getSuperclass())try{Method m=k.getDeclaredMethod(name,sig);m.setAccessible(true);return m;}catch(NoSuchMethodException ignored){}
  throw new NoSuchMethodException(c.getName()+"."+name);
 }
 private static Field field(Object object,String name) throws NoSuchFieldException {
  for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 private static Object value(Object object,String name){try{return field(object,name).get(object);}catch(ReflectiveOperationException ignored){return null;}}
 private static int number(Object object,String name,int fallback){Object v=value(object,name);return v instanceof Integer?(Integer)v:fallback;}
 private static void put(Object object,String name,int value) throws ReflectiveOperationException{field(object,name).setInt(object,value);}
 private static void putBoolean(Object object,String name,boolean value) throws ReflectiveOperationException{field(object,name).setBoolean(object,value);}
 private static Object call(Object object,String name) throws Exception{return call(object,name,new Class<?>[0]);}
 private static Object call(Object object,String name,Class<?>[] sig,Object... args) throws Exception{return find(object.getClass(),name,sig).invoke(object,args);}
 private static void fail(String where,Throwable t){
  boolean first=!failed;failed=true;config.enabled=false;
  MAIN.removeCallbacks(RETRY_CONFIG);retryScheduled=false;
  DIAGNOSTICS.failure(MODULE_ERROR,where+": "+t,System.currentTimeMillis());note(where+": "+t);report(true);
  if(first && context!=null)MAIN.post(TileModule::refreshAll);
 }
 private static void note(String s){try{if(instance!=null)instance.log(4,"RedMagicTileStudio",s);}catch(Throwable ignored){}}
}
