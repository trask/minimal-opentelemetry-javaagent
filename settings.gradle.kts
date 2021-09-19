dependencyResolutionManagement {
  repositories {
    mavenCentral()
    mavenLocal()
  }
}

rootProject.name = "otel-javaagent-minimal"

include(":javaagent-bootstrap")
include(":javaagent-tooling")
include(":javaagent")
