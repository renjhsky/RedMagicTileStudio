package io.github.renjhtools.redmagictiles;

public final class GridRegressionTest {
 private static int checks;
 private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 public static void main(String[] args){
  check(TargetRules.supported("com.zte.controlcenter.view.ControlCenterTileLayout",true,false,0),"OS 11 circle layout must be accepted");
  check(!TargetRules.supported("com.zte.controlcenter.view.ControlCenterTileLayout",true,false,1),"wide tiles must be excluded");
  check(!TargetRules.supported("com.zte.controlcenter.view.ControlCenterTopPanel",true,true,0),"top panel must be excluded before constructor sets tileType");
  check(TargetRules.supported("com.zte.mifavor.qs.MfvPagedTileLayout$TilePage",false,false,-1),"legacy paged QS must remain accepted");
  check(!TargetRules.supported("com.zte.mifavor.qs.MfvQuickQSPanel$HeaderTileLayout",false,false,-1),"header must be excluded");
  for(int width:new int[]{240,320,360,411,600,900,1080,1440,2400})
   for(int columns=3;columns<=8;columns++)
    for(int gap:new int[]{0,8,24})
     for(int percent:new int[]{65,85,110}){
      GridGeometry g=new GridGeometry(width,12,12,columns,gap,12,56,percent,24,4,4);
      int right=g.columnStart(columns-1,12)+g.cellWidth;
      check(right<=width-12,"last tile outside available width");
      check(g.iconSize<=g.cellWidth,"icon outside hit rectangle");
      check(g.cellWidth>0 && g.cellHeight>0,"nonpositive touch rectangle");
      for(int n:new int[]{0,1,4,5,8,17,24,53}){
       int rows=g.rows(n);check(rows*columns>=n,"capacity loses tiles");
       check(n==0||Math.max(0,rows-1)*columns<n,"spurious empty row");
       check(g.height(rows,4)>=0,"negative grid height");
      }
     }
  GridGeometry five=new GridGeometry(411,0,0,5,8,12,56,85,24,4,4);
  GridGeometry eight=new GridGeometry(900,0,0,8,8,12,56,85,24,4,4);
  check(five.rows(18)==4,"18 tiles at 5 columns must use 4 rows");
  check(eight.columns==8 && eight.rows(18)==3,"landscape 8 columns must not clamp to 7");
  GridGeometry noLabels=new GridGeometry(411,0,0,5,8,12,56,85,0,4,4);
  check(noLabels.cellHeight<five.cellHeight,"hiding labels must reduce row height");
  GridGeometry screenshot=GridGeometry.aligned(695,65,630,5,16,12,88,85,24,4,4);
  check(screenshot.iconStart(0)==65,"first visible circle must align with the left upper card");
  check(screenshot.iconStart(4)+screenshot.iconSize==630,"last visible circle must align with the right upper card");
  for(int width:new int[]{240,320,360,411,600,695,900,1080,1440,2400})
   for(int left:new int[]{0,12,32,48})
    for(int rightInset:new int[]{0,12,32,48})
     for(int columns=3;columns<=8;columns++)
      for(int gap:new int[]{0,8,24})
       for(int percent:new int[]{65,85,110}){
        GridGeometry g=GridGeometry.aligned(width,left,width-rightInset,columns,gap,12,56,percent,24,4,4);
        check(g.iconStart(0)==left,"left visual edge drifts after columns/size changes");
        check(g.iconStart(columns-1)+g.iconSize==width-rightInset,"right visual edge drifts after columns/size changes");
        check(g.columnStart(0,0)>=0,"first touch rectangle crosses screen boundary");
        check(g.columnStart(columns-1,0)+g.cellWidth<=width,"last touch rectangle crosses screen boundary");
        for(int i=1;i<columns;i++)check(g.columnStart(i,0)>=g.columnStart(i-1,0)+g.cellWidth,"touch targets overlap");
       }
  System.out.println("PASS: "+checks+" regression checks");
 }
}
