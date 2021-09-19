/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.SdkTracerProviderConfigurer;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;

@AutoService(SdkTracerProviderConfigurer.class)
public class AgentTracerProviderConfigurer implements SdkTracerProviderConfigurer {

  @Override
  public void configure(
      SdkTracerProviderBuilder sdkTracerProviderBuilder, ConfigProperties config) {

    System.out.println("TODO configure sdk");
  }
}
