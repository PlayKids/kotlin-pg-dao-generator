# Kotlin Postgres dao generator
Simple script to generate Postgres DAOs from Kotlin data classes
This is specific to be used with jasync library and some custom extension functions, but is a good boilerplate for any async library for Postgres

To run this script, you can simply execute
```bash
./gradlew run --args="$input $output"
```
with a data class file as `$input` and the path to the DAO as `$output` (if $output already exists, it will be overwritten, so be careful)