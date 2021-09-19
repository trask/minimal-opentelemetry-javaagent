/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

public class Utils {

  /** com.foo.Bar to com/foo/Bar.class */
  public static String getResourceName(String className) {
    return className.replace('.', '/') + ".class";
  }

  private Utils() {}
}
