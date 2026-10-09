@echo off
set APP_HOME=%~dp0
if not exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  echo ERROR: gradle\wrapper\gradle-wrapper.jar is missing. Regenerate the Gradle wrapper in Android Studio.
  exit /b 1
)
java %JAVA_OPTS% %GRADLE_OPTS% -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
