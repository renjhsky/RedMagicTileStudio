package io.github.renjhtools.redmagictiles;

/** Pure selection rule: circular control-center grids and QS pages, never top cards. */
final class TargetRules {
 static boolean supported(String className, boolean controlSubclass, boolean topSubclass, int tileType) {
  return "com.zte.mifavor.qs.MfvPagedTileLayout$TilePage".equals(className)
    || (controlSubclass && !topSubclass && tileType != 1);
 }
 private TargetRules() { }
}
