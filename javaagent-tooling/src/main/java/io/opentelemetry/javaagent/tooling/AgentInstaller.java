/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.Utils.getResourceName;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentInstaller {

  private static final Logger logger;

  private static final String JAVAAGENT_ENABLED_CONFIG = "otel.javaagent.enabled";

  // This property may be set to force synchronous AgentListener#afterAgent() execution: the
  // condition for delaying the AgentListener initialization is pretty broad and in case it covers
  // too much javaagent users can file a bug, force sync execution by setting this property to true
  // and continue using the javaagent
  private static final String FORCE_SYNCHRONOUS_AGENT_LISTENERS_CONFIG =
      "otel.javaagent.experimental.force-synchronous-agent-listeners";

  private static final String STRICT_CONTEXT_STRESSOR_MILLIS =
      "otel.javaagent.testing.strict-context-stressor-millis";

  private static final Map<String, List<Runnable>> CLASS_LOAD_CALLBACKS = new HashMap<>();

  static {
    LoggingConfigurer.configureLogger();
    logger = LoggerFactory.getLogger(AgentInstaller.class);

    addByteBuddyRawSetting();

    // ensure java.lang.reflect.Proxy is loaded, as transformation code uses it internally
    // loading java.lang.reflect.Proxy after the bytebuddy transformer is set up causes
    // the internal-proxy instrumentation module to transform it, and then the bytebuddy
    // transformation code also tries to load it, which leads to a ClassCircularityError
    // loading java.lang.reflect.Proxy early here still allows it to be retransformed by the
    // internal-proxy instrumentation module after the bytebuddy transformer is set up
    Proxy.class.getName();

    // caffeine can trigger first access of ForkJoinPool under transform(), which leads ForkJoinPool
    // not to get transformed itself.
    // loading it early here still allows it to be retransformed as part of agent installation below
    ForkJoinPool.class.getName();

    // caffeine uses AtomicReferenceArray, ensure it is loaded to avoid ClassCircularityError during
    // transform.
    AtomicReferenceArray.class.getName();

    Integer strictContextStressorMillis = Integer.getInteger(STRICT_CONTEXT_STRESSOR_MILLIS);
    if (strictContextStressorMillis != null) {
      io.opentelemetry.context.ContextStorage.addWrapper(
          storage -> new StrictContextStressor(storage, strictContextStressorMillis));
    }
  }

  public static void installBytebuddyAgent(Instrumentation inst) {
    logVersionInfo();

    OpenTelemetrySdkAutoConfiguration.initialize(true);

    AgentBuilder agentBuilder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(new RedefinitionDiscoveryStrategy())
            .with(AgentBuilder.DescriptionStrategy.Default.POOL_ONLY)
            .with(new ClassLoadListener());

    if (logger.isDebugEnabled()) {
      agentBuilder =
          agentBuilder
              .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
              .with(new RedefinitionDiscoveryStrategy())
              .with(new RedefinitionLoggingListener())
              .with(new TransformLoggingListener());
    }

    agentBuilder.installOn(inst);
  }

  private static void addByteBuddyRawSetting() {
    String savedPropertyValue = System.getProperty(TypeDefinition.RAW_TYPES_PROPERTY);
    try {
      System.setProperty(TypeDefinition.RAW_TYPES_PROPERTY, "true");
      boolean rawTypes = TypeDescription.AbstractBase.RAW_TYPES;
      if (!rawTypes) {
        logger.debug("Too late to enable {}", TypeDefinition.RAW_TYPES_PROPERTY);
      }
    } finally {
      if (savedPropertyValue == null) {
        System.clearProperty(TypeDefinition.RAW_TYPES_PROPERTY);
      } else {
        System.setProperty(TypeDefinition.RAW_TYPES_PROPERTY, savedPropertyValue);
      }
    }
  }

  static class RedefinitionLoggingListener implements AgentBuilder.RedefinitionStrategy.Listener {

    private static final Logger logger = LoggerFactory.getLogger(RedefinitionLoggingListener.class);

    @Override
    public void onBatch(int index, List<Class<?>> batch, List<Class<?>> types) {}

    @Override
    public Iterable<? extends List<Class<?>>> onError(
        int index, List<Class<?>> batch, Throwable throwable, List<Class<?>> types) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Exception while retransforming {} classes: {}", batch.size(), batch, throwable);
      }
      return Collections.emptyList();
    }

    @Override
    public void onComplete(
        int amount, List<Class<?>> types, Map<List<Class<?>>, Throwable> failures) {}
  }

  static class TransformLoggingListener implements AgentBuilder.Listener {

    private static final Logger logger = LoggerFactory.getLogger(TransformLoggingListener.class);

    @Override
    public void onError(
        String typeName,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        Throwable throwable) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Failed to handle {} for transformation on classloader {}",
            typeName,
            classLoader,
            throwable);
      }
    }

    @Override
    public void onTransformation(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        DynamicType dynamicType) {
      logger.debug("Transformed {} -- {}", typeDescription.getName(), classLoader);
    }

    @Override
    public void onIgnored(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded) {}

    @Override
    public void onComplete(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}

    @Override
    public void onDiscovery(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}
  }

  /**
   * Register a callback to run when a class is loading.
   *
   * <p>Caveats:
   *
   * <ul>
   *   <li>This callback will be invoked by a jvm class transformer.
   *   <li>Classes filtered out by {@link AgentInstaller}'s skip list will not be matched.
   * </ul>
   *
   * @param className name of the class to match against
   * @param callback runnable to invoke when class name matches
   */
  public static void registerClassLoadCallback(String className, Runnable callback) {
    synchronized (CLASS_LOAD_CALLBACKS) {
      List<Runnable> callbacks =
          CLASS_LOAD_CALLBACKS.computeIfAbsent(className, k -> new ArrayList<>());
      callbacks.add(callback);
    }
  }

  private static class ClassLoadListener implements AgentBuilder.Listener {
    @Override
    public void onDiscovery(
        String typeName, ClassLoader classLoader, JavaModule javaModule, boolean b) {}

    @Override
    public void onTransformation(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule javaModule,
        boolean b,
        DynamicType dynamicType) {}

    @Override
    public void onIgnored(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule javaModule,
        boolean b) {}

    @Override
    public void onError(
        String s, ClassLoader classLoader, JavaModule javaModule, boolean b, Throwable throwable) {}

    @Override
    public void onComplete(
        String typeName, ClassLoader classLoader, JavaModule javaModule, boolean b) {
      synchronized (CLASS_LOAD_CALLBACKS) {
        List<Runnable> callbacks = CLASS_LOAD_CALLBACKS.get(typeName);
        if (callbacks != null) {
          for (Runnable callback : callbacks) {
            callback.run();
          }
        }
      }
    }
  }

  private static class RedefinitionDiscoveryStrategy
      implements AgentBuilder.RedefinitionStrategy.DiscoveryStrategy {
    private static final AgentBuilder.RedefinitionStrategy.DiscoveryStrategy delegate =
        AgentBuilder.RedefinitionStrategy.DiscoveryStrategy.Reiterating.INSTANCE;

    @Override
    public Iterable<Iterable<Class<?>>> resolve(Instrumentation instrumentation) {
      // filter out our agent classes and injected helper classes
      return () ->
          streamOf(delegate.resolve(instrumentation))
              .map(RedefinitionDiscoveryStrategy::filterClasses)
              .iterator();
    }

    private static Iterable<Class<?>> filterClasses(Iterable<Class<?>> classes) {
      return () -> streamOf(classes).filter(c -> !isIgnored(c)).iterator();
    }

    private static <T> Stream<T> streamOf(Iterable<T> iterable) {
      return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static boolean isIgnored(Class<?> c) {
      ClassLoader cl = c.getClassLoader();
      if (cl instanceof AgentClassLoader || cl instanceof ExtensionClassLoader) {
        return true;
      }
      // ignore generate byte buddy helper class
      if (c.getName().startsWith("java.lang.ClassLoader$ByteBuddyAccessor$")) {
        return true;
      }

      return false;
    }
  }

  /** Detect if the instrumented application is using a custom JUL LogManager. */
  private static boolean isAppUsingCustomLogManager() {
    String jbossHome = System.getenv("JBOSS_HOME");
    if (jbossHome != null) {
      logger.debug("Found JBoss: {}; assuming app is using custom LogManager", jbossHome);
      // JBoss/Wildfly is known to set a custom log manager after startup.
      // Originally we were checking for the presence of a jboss class,
      // but it seems some non-jboss applications have jboss classes on the classpath.
      // This would cause AgentListener#afterAgent() calls to be delayed indefinitely.
      // Checking for an environment variable required by jboss instead.
      return true;
    }

    String customLogManager = System.getProperty("java.util.logging.manager");
    if (customLogManager != null) {
      logger.debug(
          "Detected custom LogManager configuration: java.util.logging.manager={}",
          customLogManager);
      boolean onSysClasspath =
          ClassLoader.getSystemResource(getResourceName(customLogManager)) != null;
      logger.debug(
          "Class {} is on system classpath: {}delaying AgentInstaller#afterAgent()",
          customLogManager,
          onSysClasspath ? "not " : "");
      // Some applications set java.util.logging.manager but never actually initialize the logger.
      // Check to see if the configured manager is on the system classpath.
      // If so, it should be safe to initialize AgentInstaller which will setup the log manager:
      // LogManager tries to load the implementation first using system CL, then falls back to
      // current context CL
      return !onSysClasspath;
    }

    return false;
  }

  private static void logVersionInfo() {
    VersionLogger.logAllVersions();
    logger.debug(
        "{} loaded on {}", AgentInstaller.class.getName(), AgentInstaller.class.getClassLoader());
  }

  private AgentInstaller() {}

  private static class StrictContextStressor implements ContextStorage, AutoCloseable {

    private final ContextStorage contextStorage;
    private final int sleepMillis;

    private StrictContextStressor(ContextStorage contextStorage, int sleepMillis) {
      this.contextStorage = contextStorage;
      this.sleepMillis = sleepMillis;
    }

    @Override
    public Scope attach(Context toAttach) {
      return wrap(contextStorage.attach(toAttach));
    }

    @Nullable
    @Override
    public Context current() {
      return contextStorage.current();
    }

    @Override
    public void close() throws Exception {
      if (contextStorage instanceof AutoCloseable) {
        ((AutoCloseable) contextStorage).close();
      }
    }

    private Scope wrap(Scope scope) {
      return () -> {
        try {
          Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        scope.close();
      };
    }
  }
}
