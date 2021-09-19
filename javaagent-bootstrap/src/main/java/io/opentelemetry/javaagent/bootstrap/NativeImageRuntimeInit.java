package io.opentelemetry.javaagent.bootstrap;

import java.util.concurrent.atomic.AtomicBoolean;

public class NativeImageRuntimeInit {

  private static final AtomicBoolean initialized = new AtomicBoolean();

  public static void init() {
    if (!initialized.getAndSet(true)) {
      System.out.println("hi from init!");
      try {
        AgentInitializer.nativeImageRuntimeInit();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
