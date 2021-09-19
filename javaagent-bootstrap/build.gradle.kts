plugins {
  `java-library`
}

dependencies {
  implementation("io.opentelemetry:opentelemetry-api:1.6.0")
  implementation("org.slf4j:slf4j-simple:1.7.32")

  compileOnly("org.checkerframework:checker-qual:3.18.0")
}
