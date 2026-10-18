package io.github.renjhtools.redmagictiles;

import android.os.Bundle;

final class TileConfig {
 static final String AUTH = "io.github.renjhtools.redmagictiles.settings";
 static final String[] KEYS = { "enabled", "portraitColumns", "landscapeColumns", "size", "gapH", "gapV", "fontSp", "labels" };
 boolean enabled = false; // Explicit opt-in to prevent boot loops on first launch
 int portraitColumns = 5;
 int landscapeColumns = 6;
 int size = 85;
 int gapH = 8;
 int gapV = 12;
 int fontSp = 11;
 boolean labels = true;
 static int clamp(int n, int low, int high) { return Math.max(low, Math.min(high,n)); }
 int revision = 0;
 int cols(boolean landscape) { return clamp(landscape ? landscapeColumns : portraitColumns,3,landscape ? 8 : 7); }
 static TileConfig from(Bundle b) {
  TileConfig c = new TileConfig();
  if (b == null) return c;
  c.enabled = b.getBoolean("enabled",false);
  c.portraitColumns = clamp(b.getInt("portraitColumns",5),3,7);
  c.landscapeColumns = clamp(b.getInt("landscapeColumns",6),3,8);
  c.size = clamp(b.getInt("size",85),65,110);
  c.gapH = clamp(b.getInt("gapH",8),0,24);
  c.gapV = clamp(b.getInt("gapV",12),0,28);
  c.fontSp = clamp(b.getInt("fontSp",11),8,16);
  c.labels = b.getBoolean("labels",true);
  c.revision = b.getInt("revision",0);
  return c;
 }
}
