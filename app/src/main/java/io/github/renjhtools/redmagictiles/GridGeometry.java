package io.github.renjhtools.redmagictiles;

/** Pixel geometry shared by measuring, positioning and runtime diagnostics. */
final class GridGeometry {
 final int columns, cellWidth, cellHeight, gapH, gapV, iconSize, visualLeft, visualRight;
 private final boolean edgeAligned;
 private final int origin, centerSpan;
 GridGeometry(int width, int start, int end, int columns, int gapH, int gapV,
              int nativeIcon, int percent, int labelHeight, int iconInset, int bottomInset) {
  this(width,start,end,columns,gapH,gapV,nativeIcon,percent,labelHeight,iconInset,bottomInset,false);
 }
 static GridGeometry aligned(int width, int visualLeft, int visualRight, int columns, int gapH, int gapV,
                             int nativeIcon, int percent, int labelHeight, int iconInset, int bottomInset) {
  return new GridGeometry(width,visualLeft,width-visualRight,columns,gapH,gapV,
                          nativeIcon,percent,labelHeight,iconInset,bottomInset,true);
 }
 private GridGeometry(int width, int start, int end, int columns, int gapH, int gapV,
                      int nativeIcon, int percent, int labelHeight, int iconInset, int bottomInset, boolean aligned) {
  edgeAligned=aligned;
  this.columns = Math.max(3, Math.min(8, columns));
  int inner = Math.max(this.columns, width - start - end);
  this.gapH = Math.max(0, Math.min(gapH, Math.max(0, (inner - this.columns) / (this.columns - 1))));
  this.gapV = Math.max(0, gapV);
  int naturalCell = Math.max(1, (inner - this.gapH * (this.columns - 1)) / this.columns);
  iconSize = Math.max(1, Math.min(Math.round(nativeIcon * Math.max(65, Math.min(110, percent)) / 100f),
                                Math.max(1, naturalCell - iconInset)));
  centerSpan = inner - iconSize;
  if(aligned){
   int spacedCell = Math.max(iconSize,centerSpan/(this.columns-1)-this.gapH);
   // Touch rectangles may extend beyond card edges, but never beyond the view or into each other.
   int boundedCell = iconSize+2*Math.max(0,Math.min(start,end));
   cellWidth = Math.min(spacedCell,boundedCell);
   origin = start-(cellWidth-iconSize)/2;
   visualLeft = start; visualRight = width-end;
  }else{
   cellWidth = naturalCell; origin = start;
   visualLeft = start+(cellWidth-iconSize)/2;
   visualRight = start+(this.columns-1)*(cellWidth+this.gapH)+(cellWidth-iconSize)/2+iconSize;
  }
  cellHeight = Math.max(1, iconSize + Math.max(0, labelHeight) + Math.max(0, bottomInset));
 }
 int rows(int tiles) { return Math.max(0, (Math.max(0, tiles) + columns - 1) / columns); }
 int height(int rows, int top) { return Math.max(0, top + rows * cellHeight + Math.max(0, rows - 1) * gapV); }
 int columnStart(int column, int start) {
  return edgeAligned ? origin+Math.round(column*centerSpan/(float)(columns-1)) : start+column*(cellWidth+gapH);
 }
 int iconStart(int column) { return columnStart(column,origin)+(cellWidth-iconSize)/2; }
}
