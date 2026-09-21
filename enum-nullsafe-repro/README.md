# Runtime reproduction for PR #24894

Standalone reproduction of the `enumUnknownDefaultCase` null-safety defect in the kotlin
generator's moshi serializer, and of the fix in PR #24894.

Not part of the build. Nothing here is wired into CI; it exists so a reviewer can re-run the
before/after by hand.

## What it proves

`SerializerHelper.kt.mustache` registers `EnumJsonAdapter.create(X).withUnknownFallback(...)`
per enum class. `EnumJsonAdapter` (from `moshi-adapters`) is not null-safe, and registering it
shadows moshi's built-in enum adapter, which is. Every non-`required` enum property therefore
breaks on null, in both directions.

`spec.yaml` has a required inline enum, an optional inline enum, an optional `$ref`'d enum, and
an array-of-inline-enum, so one generated client covers all the shapes the template registers.

## Run it

```
# 1. build a CLI jar from whichever commit you want to test
./mvnw -B -pl modules/openapi-generator-cli -am -DskipTests package

# 2. generate
java -jar modules/openapi-generator-cli/target/openapi-generator-cli.jar generate \
  -i enum-nullsafe-repro/spec.yaml -g kotlin -o /tmp/enumrepro \
  --additional-properties=enumUnknownDefaultCase=true

# 3. drop the harness in and give it a main class
cp enum-nullsafe-repro/Repro.kt /tmp/enumrepro/src/main/kotlin/
printf "\napply plugin: 'application'\napplication { mainClass = 'ReproKt' }\n" >> /tmp/enumrepro/build.gradle
chmod +x /tmp/enumrepro/gradlew

# 4. run
(cd /tmp/enumrepro && ./gradlew -q --console=plain run)
```

Exit code is 0 when all five checks pass, 1 otherwise.

## Expected results

Against `master` (no fix), checks 1 and 2 throw:

```
[1-encode-optional-enum-unset]
  actual: THROW-> java.lang.NullPointerException: value was null! Wrap in .nullSafe() to write nullable values.
[2-decode-explicit-null]
  actual: THROW-> com.squareup.moshi.JsonDataException: Expected a string but was NULL at path $.optionalColor
=== FAILURES: 2 ===
```

Against PR #24894, all five pass and `=== FAILURES: 0 ===`.

Checks 3 (unknown value falls back to `unknown_default_open_api`) and 4 (known values decode
unchanged) pass on both, which is the point: the fix does not weaken the feature it is fixing.
