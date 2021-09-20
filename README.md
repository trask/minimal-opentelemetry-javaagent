# Minimal OpenTelemetry Javaagent

For the purpose of native image testing.

## Build the javaagent

Build using Java 11:

`./gradlew assemble`

and then you can find the java agent artifact at

`javaagent/build/libs/javaagent.jar`.

## Build the native image

Using your own (preferably simple for testing) `app.jar`:

```
native-image --no-fallback \
    -J-javaagent:path/to/javaagent.jar \
    --initialize-at-build-time=io.opentelemetry.javaagent.bootstrap,net.bytebuddy.dynamic.loading \
    -jar app.jar
```

## Current problem...

```
Error: Unsupported features in 2 methods
Detailed message:
Error: Detected a ZipFile object in the image heap. A ZipFile object contains pointers to unmanaged C memory and file descriptors, and these resources are no longer available at image runtime.  To see how this object got instantiated use --trace-object-instantiation=java.util.jar.JarFile. The object was probably created by a class initializer and is reachable from a static field. You can request class initialization at image runtime by using the option --initialize-at-run-time=<class-name>. Or you can write your own initialization methods and call them explicitly from your main entry point.
Trace: Object was reached by
        reading field io.opentelemetry.javaagent.bootstrap.AgentClassLoader$AgentClassLoaderUrlStreamHandler.jarFile of
                constant io.opentelemetry.javaagent.bootstrap.AgentClassLoader$AgentClassLoaderUrlStreamHandler@3585659 reached by
        reading field java.net.URL.handler of
                constant java.net.URL@1e13dc4 reached by
        reading field io.opentelemetry.javaagent.bootstrap.AgentClassLoader.jarBase of
                constant io.opentelemetry.javaagent.bootstrap.AgentClassLoader@7946e1f4 reached by
        reading field java.lang.Class.classLoader of
                constant java.lang.Class@2ca923bb reached by
        Hub
Error: Detected a ZipFile object in the image heap. A ZipFile object contains pointers to unmanaged C memory and file descriptors, and these resources are no longer available at image runtime.  To see how this object got instantiated use --trace-object-instantiation=java.util.jar.JarFile. The object was probably created by a class initializer and is reachable from a static field. You can request class initialization at image runtime by using the option --initialize-at-run-time=<class-name>. Or you can write your own initialization methods and call them explicitly from your main entry point.
Trace: Object was reached by
        reading field io.opentelemetry.javaagent.bootstrap.AgentClassLoader.jarFile of
                constant io.opentelemetry.javaagent.bootstrap.AgentClassLoader@7946e1f4 reached by
        reading field java.lang.Class.classLoader of
                constant java.lang.Class@13df2a8c reached by
        reading field java.lang.ref.Reference.referent of
                constant java.util.WeakHashMap$Entry@62498350 reached by
        indexing into array
                constant java.util.WeakHashMap$Entry[]@57e2eb84 reached by
        reading field java.util.WeakHashMap.table of
                constant java.util.WeakHashMap@6fa81529 reached by
        reading field java.util.Collections$SetFromMap.m of
                constant java.util.Collections$SetFromMap@54afdf10 reached by
        scanning method java.lang.ClassLoader$ParallelLoaders.isRegistered(ClassLoader.java:293)
Call path from entry point to java.lang.ClassLoader$ParallelLoaders.isRegistered(Class):
        at java.lang.ClassLoader$ParallelLoaders.isRegistered(ClassLoader.java:293)
        at java.lang.ClassLoader.<init>(ClassLoader.java:379)
        at java.lang.ClassLoader.<init>(ClassLoader.java:457)
        at io.opentelemetry.javaagent.bootstrap.AgentClassLoader$BootstrapClassLoaderProxy.<init>(AgentClassLoader.java:315)
        at io.opentelemetry.javaagent.bootstrap.AgentClassLoader.<init>(AgentClassLoader.java:83)
        at io.opentelemetry.javaagent.bootstrap.AgentInitializer.createAgentClassLoader(AgentInitializer.java:84)
        at io.opentelemetry.javaagent.bootstrap.AgentInitializer.nativeImageRuntimeInit(AgentInitializer.java:52)
        at io.opentelemetry.javaagent.bootstrap.NativeImageRuntimeInit.init(NativeImageRuntimeInit.java:13)
        at com.github.trask.app.Main.main(Main.java)
        at com.oracle.svm.core.JavaMainWrapper.runCore(JavaMainWrapper.java:146)
        at com.oracle.svm.core.JavaMainWrapper.run(JavaMainWrapper.java:182)
        at com.oracle.svm.core.code.IsolateEnterStub.JavaMainWrapper_run_5087f5482cc9a6abc971913ece43acb471d2631b(generated:0)
```