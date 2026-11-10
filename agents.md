# Maven build instructions

The Java application and its `pom.xml` are in `core/`, not the project root. Run Maven from the project root with `-f core/pom.xml`, or change into `core/` before running Maven.

The project targets Java 26 (`core/pom.xml`). Check `java -version` before building and make sure Maven uses a JDK 26 installation; an older JDK can report `release version 26 not supported`.

## Efficient validation

From the project root in PowerShell, start with the narrowest check for the change:

```powershell
# Compile production code; catches compilation and annotation-processing errors quickly.
mvn -f core\pom.xml -DskipTests compile

# Compile tests too, without running them.
mvn -f core\pom.xml -DskipTests test-compile

# Run one focused test class.
mvn -f core\pom.xml -Dtest=RssToolsTests test

# Run unit tests (same group used by CI).
mvn -f core\pom.xml '-Dgroups=Unit' test

# Build the package without running tests (same package check used by CI).
mvn -f core\pom.xml -DskipTests package

# Run the ContinuousIntegration test group (may require external services or containers).
mvn -f core\pom.xml '-Dgroups=ContinuousIntegration' test
```

From `core/`, omit the `-f core\pom.xml` argument. For a final local check, run the unit-test group and then `mvn package` to include the full default test suite.

## Build troubleshooting

- If Maven reports `release version 26 not supported`, select JDK 26 for Maven; changing the Maven command alone will not fix an older compiler.
- If Lombok-generated methods such as `getTitle()` or `setTitle()` are missing, check that the Maven Compiler Plugin's annotation processor paths in `core/pom.xml` still include Lombok. `mvn -f core\pom.xml -DskipTests compile` catches this before running tests.
- The Maven wrapper scripts are under `core/`. Use `.\core\mvnw.cmd` from the root only when `core\.mvn\wrapper\maven-wrapper.properties` is present; otherwise use an installed `mvn`.
