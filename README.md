Este es un proyecto Kotlin Multiplatform dirigido a Android, iOS, Web y Server.
• /app/iosApp contiene una aplicación iOS. Incluso si estás compartiendo tu interfaz de usuario con Compose Multiplatform, necesitas este punto de entrada para tu aplicación iOS. Aquí también es donde deberías añadir código SwiftUI para tu proyecto.

• /app/shared es para el código que se compartirá entre tus aplicaciones Compose Multiplatform. Contiene varias subcarpetas:
	• commonMain es para el código que es común para todos los targets.
  
	• Otras carpetas son para código Kotlin que se compilará solo para la plataforma indicada en el nombre de la carpeta. Por ejemplo, si deseas usar el CoreCrypto de Apple para la parte de iOS de tu aplicación Kotlin, la carpeta iosMain sería el lugar adecuado para esas llamadas. Del mismo modo, si deseas editar la parte específica de Desktop (JVM), la carpeta jvmMain es la ubicación apropiada.
• /core es para el código que se compartirá entre todos los targets del proyecto. La subcarpeta más importante es commonMain. Si lo prefieres, también puedes añadir código a las carpetas específicas de cada plataforma aquí.

• /server es para la aplicación de servidor Ktor.

Running the apps (Ejecución de las aplicaciones)

Usa las configuraciones de ejecución proporcionadas por el widget de ejecución en la barra de herramientas de tu IDE. También puedes usar estos comandos y opciones:
• Aplicación Android: ./gradlew :app:androidApp:assembleDebug
• Servidor: ./gradlew :server:run

• Aplicación Web:
	• Target Wasm (más rápido, navegadores modernos): ./gradlew :app:webApp:wasmJsBrowserDevelopmentRun
	• Target JS (más lento, soporta navegadores más antiguos): ./gradlew :app:webApp:jsBrowserDevelopmentRun
• Aplicación iOS: abre el directorio /app/iosApp en Xcode y ejecútalo desde allí.

Running tests (Ejecución de pruebas)

Usa el botón de ejecución en el margen del editor de tu IDE, o ejecuta las pruebas usando tareas de Gradle:
• Pruebas de Android: ./gradlew :app:shared:testAndroidHostTest
• Pruebas de servidor: ./gradlew :server:test
• Pruebas web:
	• Target Wasm: ./gradlew :app:shared:wasmJsTest
	• Target JS: ./gradlew :app:shared:jsTest
• Pruebas de iOS: ./gradlew :app:shared:iosSimulatorArm64Test

Aprende más sobre Kotlin Multiplatform, Compose Multiplatform, Kotlin/Wasm…
Agradeceríamos tus comentarios sobre Compose/Web y Kotlin/Wasm en el canal público de Slack #compose-web. Si encuentras algún problema, por favor repórtalo en YouTrack.
