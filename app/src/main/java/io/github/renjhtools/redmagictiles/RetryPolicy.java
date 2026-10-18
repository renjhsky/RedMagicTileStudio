package io.github.renjhtools.redmagictiles;

/** Eight retries per failure period; no unbounded polling when the provider is absent. */
final class RetryPolicy {
 private int attempts;
 long nextDelayMillis(){
  if(attempts>=8)return -1;
  return Math.min(5000L,500L << attempts++);
 }
 void reset(){attempts=0;}
}
