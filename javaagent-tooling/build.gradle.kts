plugins {
  `java-library`
}

dependencies {
  implementation(project(":javaagent-bootstrap"))

  implementation("io.opentelemetry:opentelemetry-sdk:1.6.0")

  api("net.bytebuddy:byte-buddy:1.11.16")
}
