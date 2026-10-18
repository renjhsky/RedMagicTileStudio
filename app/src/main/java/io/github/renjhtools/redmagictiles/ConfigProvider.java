package io.github.renjhtools.redmagictiles;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;

/** Configuration is read-only remotely; runtime reports are restricted to SystemUI. */
public final class ConfigProvider extends ContentProvider {
 @Override public boolean onCreate() { return true; }
 @Override public Bundle call(String method, String arg, Bundle extras) {
  if (getContext() == null) return Bundle.EMPTY;
  if ("read".equals(method)) {
   SharedPreferences p = Prefs.get(getContext(), "tiles");
   Bundle b = new Bundle();
   b.putBoolean("enabled",p.getBoolean("enabled",false));
   b.putInt("portraitColumns",p.getInt("portraitColumns",5));
   b.putInt("landscapeColumns",p.getInt("landscapeColumns",6));
   b.putInt("size",p.getInt("size",85));
   b.putInt("gapH",p.getInt("gapH",8));
   b.putInt("gapV",p.getInt("gapV",12));
   b.putInt("fontSp",p.getInt("fontSp",11));
   b.putBoolean("labels",p.getBoolean("labels",true));
   b.putInt("revision",p.getInt("revision",0));
   return b;
  }
  if ("report".equals(method)) {
   requireSystemUi();
   if (extras != null) {
    Prefs.get(getContext(), "runtime").edit()
     .putString("report",extras.getString("report",""))
     .putString("version",extras.getString("version",""))
     .putInt("versionCode",extras.getInt("versionCode",-1))
     .putInt("revision",extras.getInt("revision",-1))
     .putBoolean("configRead",extras.getBoolean("configRead",false))
     .putBoolean("failed",extras.getBoolean("failed",false))
     .putString("history",extras.getString("history","暂无已恢复记录。"))
     .putLong("received",SystemClock.elapsedRealtime()).apply();
   }
   return Bundle.EMPTY;
  }
  if ("status".equals(method)) {
   if (Binder.getCallingUid() != Process.myUid()) throw new SecurityException("own app only");
   SharedPreferences p = Prefs.get(getContext(), "runtime");
   Bundle b = new Bundle();
   b.putString("report",p.getString("report","尚未收到 SystemUI 的运行回报。"));
   b.putString("version",p.getString("version",""));
   b.putInt("versionCode",p.getInt("versionCode",-1));
   b.putInt("revision",p.getInt("revision",-1));
   b.putBoolean("configRead",p.getBoolean("configRead",false));
   b.putBoolean("failed",p.getBoolean("failed",false));
   b.putString("history",p.getString("history","暂无已恢复记录。"));
   b.putLong("received",p.getLong("received",-1));
   return b;
  }
  return Bundle.EMPTY;
 }
 private void requireSystemUi() {
  try {
   int uid = getContext().getPackageManager().getApplicationInfo("com.android.systemui",0).uid;
   if (Binder.getCallingUid() == uid) return;
  } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
  throw new SecurityException("SystemUI reports only");
 }
 static Uri uri() { return Uri.parse("content://"+TileConfig.AUTH+"/config"); }
 @Override public Cursor query(Uri u,String[] p,String s,String[] sa,String o){return null;}
 @Override public String getType(Uri u){return null;}
 @Override public Uri insert(Uri u,ContentValues v){throw new SecurityException("read only");}
 @Override public int delete(Uri u,String s,String[] a){throw new SecurityException("read only");}
 @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new SecurityException("read only");}
}
