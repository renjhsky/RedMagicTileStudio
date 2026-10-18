package io.github.renjhtools.redmagictiles;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
 static SharedPreferences get(Context context, String name) {
  Context dp = context.createDeviceProtectedStorageContext();
  if (!context.isDeviceProtectedStorage()) {
   try { dp.moveSharedPreferencesFrom(context, name); } catch (RuntimeException ignored) { }
  }
  return dp.getSharedPreferences(name, Context.MODE_PRIVATE);
 }
 private Prefs() { }
}
