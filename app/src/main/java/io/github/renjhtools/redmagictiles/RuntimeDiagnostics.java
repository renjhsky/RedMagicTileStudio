package io.github.renjhtools.redmagictiles;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Tracks independent current faults and a bounded history of faults that recovered. */
final class RuntimeDiagnostics {
 static final int HISTORY_LIMIT = 8;
 private final LinkedHashMap<String,Event> current = new LinkedHashMap<>();
 private final ArrayDeque<Event> recovered = new ArrayDeque<>();

 synchronized boolean failure(String area,Throwable error,long now) {
  return failure(area,String.valueOf(error),now);
 }
 synchronized boolean failure(String area,String detail,long now) {
  if(detail.length()>512)detail=detail.substring(0,512)+"…";
  Event previous=current.get(area);
  if(previous!=null && previous.detail.equals(detail)){previous.occurrences++;return false;}
  current.put(area,new Event(area,detail,now));return true;
 }
 synchronized void success(String area,long now) {
  Event event=current.remove(area);
  if(event==null)return;
  event.recoveredAt=now;recovered.addFirst(event);
  while(recovered.size()>HISTORY_LIMIT)recovered.removeLast();
 }
 synchronized boolean hasCurrentErrors(){return !current.isEmpty();}
 synchronized int recoveredCount(){return recovered.size();}
 synchronized String currentText(){
  if(current.isEmpty())return "无";
  StringBuilder text=new StringBuilder();
  for(Event event:current.values()){
   if(text.length()>0)text.append('\n');
   text.append(event.area).append("：").append(event.detail);
  }
  return text.toString();
 }
 synchronized String historyText(){
  if(recovered.isEmpty())return "暂无已恢复的错误记录。";
  StringBuilder text=new StringBuilder();
  for(Event event:recovered){
   if(text.length()>0)text.append("\n\n");
   text.append(time(event.recoveredAt)).append(" 已恢复：").append(event.area)
    .append("\n首次发生：").append(time(event.startedAt)).append("；次数：").append(event.occurrences)
    .append('\n').append(event.detail);
  }
  return text.toString();
 }
 private static String time(long time){return new SimpleDateFormat("MM-dd HH:mm:ss",Locale.ROOT).format(new Date(time));}
 private static final class Event {
  final String area,detail;final long startedAt;long recoveredAt;int occurrences=1;
  Event(String area,String detail,long startedAt){this.area=area;this.detail=detail;this.startedAt=startedAt;}
 }
}
