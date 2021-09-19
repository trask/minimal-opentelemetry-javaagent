plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
  maven {
    url = uri("https://oss.sonatype.org/content/repositories/snapshots")
  }
}

dependencies {
  implementation(gradleApi())
  implementation(localGroovy())

  implementation("gradle.plugin.com.github.jengelman.gradle.plugins:shadow:7.0.0")
}