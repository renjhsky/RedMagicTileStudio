package io.github.renjhtools.redmagictiles;

public final class DiagnosticsRegressionTest {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  RuntimeDiagnostics status=new RuntimeDiagnostics();
  check(!status.hasCurrentErrors() && "无".equals(status.currentText()),"clean startup must be clean");
  IllegalArgumentException missing=new IllegalArgumentException("Unknown authority io.github.renjhtools.redmagictiles.settings");
  status.failure("配置读取",missing,1000);
  check(status.hasCurrentErrors() && status.currentText().contains("Unknown authority"),"live read failure must remain visible");
  status.failure("配置读取",missing,1500);status.success("配置读取",2000);
  check(!status.hasCurrentErrors() && "无".equals(status.currentText()),"recovered configuration error must leave current status");
  check(status.recoveredCount()==1 && status.historyText().contains("次数：2"),"retries must coalesce in recovered history");
  check(status.historyText().contains("Unknown authority") && status.historyText().contains("已恢复"),"history must retain labelled details");
  status.success("配置读取",2500);check(status.recoveredCount()==1,"repeated successful reads must not duplicate history");
  status.failure("模块执行","real layout failure",3000);status.failure("配置读取",missing,3500);status.success("配置读取",4000);
  check(status.currentText().contains("real layout failure"),"a configuration recovery must not erase an independent layout fault");
  check(!status.currentText().contains("Unknown authority"),"recovered read faults must not leak into current errors");
  status.success("模块执行",4500);check(!status.hasCurrentErrors(),"explicit recovery must clear the matching area");
  for(int i=0;i<30;i++){status.failure("配置读取","temporary "+i,5000+i*10);status.success("配置读取",5001+i*10);}
  check(status.recoveredCount()==RuntimeDiagnostics.HISTORY_LIMIT,"history must stay bounded");
  check(status.historyText().contains("temporary 29") && !status.historyText().contains("temporary 0"),"history must prefer newest incidents");
  RetryPolicy retry=new RetryPolicy();long last=0,total=0;
  for(int i=0;i<8;i++){long delay=retry.nextDelayMillis();check(delay>=500 && delay>=last && delay<=5000,"retry delay bounds");last=delay;total+=delay;}
  check(retry.nextDelayMillis()==-1 && retry.nextDelayMillis()==-1,"absent services must not cause infinite retry polling");
  check(total==27500,"expected bounded startup retry duration");retry.reset();check(retry.nextDelayMillis()==500,"success must reset retry budget for future incidents");
  System.out.println("PASS: "+checks+" diagnostic and retry regression checks");
 }
}
