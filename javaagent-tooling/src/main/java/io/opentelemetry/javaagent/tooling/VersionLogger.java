/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VersionLogger {

  private static final Logger logger = LoggerFactory.getLogger(VersionLogger.class);

  public static void logAllVersions() {
    logger.info(
        "opentelemetry-javaagent - version: {}",
        VersionLogger.class.getPackage().getImplementationVersion());
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Running on Java {}. JVM {} - {} - {}",
          System.getProperty("java.version"),
          System.getProperty("java.vm.name"),
          System.getProperty("java.vm.vendor"),
          System.getProperty("java.vm.version"));
    }
  }

  private VersionLogger() {}
}
