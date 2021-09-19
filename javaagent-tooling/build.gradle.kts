plugins {
  `java-library`
}

dependencies {
  implementation(project(":javaagent-bootstrap"))

  implementation("io.opentelemetry:opentelemetry-sdk:1.6.0")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.6.0-alpha")

  implementation("org.slf4j:slf4j-api:1.7.32")

  api("net.bytebuddy:byte-buddy:1.11.16")

  annotationProcessor("com.google.auto.service:auto-service:1.0")
  compileOnly("com.google.auto.service:auto-service:1.0")
}
