/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import net.bytebuddy.agent.builder.AgentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;

public class AgentInstaller {

  private static final Logger logger;

  static {
    LoggingConfigurer.configureLogger();
    logger = LoggerFactory.getLogger(AgentInstaller.class);
  }

  public static void installBytebuddyAgent(Instrumentation inst) {
    logVersionInfo();

    inst.addTransformer(new InitClassFileTransformer());

    // this is temporary
    System.setProperty("otel.traces.exporter", "none");

    OpenTelemetrySdkAutoConfiguration.initialize(true);

    AgentBuilder agentBuilder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.DescriptionStrategy.Default.POOL_ONLY);

    agentBuilder.installOn(inst);
  }

  private static final AtomicBoolean initialized = new AtomicBoolean();

  public static void nativeImageRuntimeInit() {
    if (initialized.getAndSet(true)) {
      return;
    }

    logVersionInfo();

    System.out.println("nativeImageRuntimeInit:");
  }

  private static void logVersionInfo() {
    VersionLogger.logAllVersions();
    logger.debug(
        "{} loaded on {}", AgentInstaller.class.getName(), AgentInstaller.class.getClassLoader());
  }

  private AgentInstaller() {}
}
