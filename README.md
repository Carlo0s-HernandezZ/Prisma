## 🏗️ Arquitectura del Sistema

El sistema sigue una arquitectura moderna, distribuida y basada en microservicios, optimizada para el procesamiento asíncrono y la integración con Inteligencia Artificial.


sequenceDiagram
    autonumber
    actor Usuario
    participant App as App Cliente<br/>(KMP)
    participant Backend as Backend<br/>(Kotlin REST API)
    participant BD as Base de Datos

   Usuario->>App: Ingresa email y contraseña
    App->>Backend: POST /auth/login con email y password
    Backend->>BD: Consulta usuario por email
    BD-->>Backend: Datos del usuario y hash
    
 *   alt credenciales incorrectas
        Backend-->>App: HTTP 401 no autorizado
        App->>Usuario: Muestra "Usuario o contraseña incorrectos"
    else credenciales correctas
        Backend-->>App: HTTP 200 estado PENDING_2FA con temp_token
        App-->>Usuario: Solicita código 2FA
    end

  *  Usuario->>App: Introduce código TOTP de 6 dígitos
    App->>Backend: POST /auth/verify-2fa con código y temp_token
    Backend->>BD: Consulta semilla 2FA del usuario
    BD-->>Backend: Semilla criptográfica

   * alt código inválido o expirado
        Backend-->>App: HTTP 400 código incorrecto
        App->>Usuario: Muestra "Código de autenticación inválido"
    else código válido
        Backend-->>App: HTTP 200 access_token y refresh_token
        Note over App: Guarda tokens en Keychain o KeyStore
        App->>Usuario: Redirige al dashboard de tarjetas
    end


### 🗺️ Diagrama de Componentes y Flujo

erDiagram
    USERS ||--o{ USER_INCOMES : deposits
    USERS ||--o{ CREDIT_CARDS : owns
    USERS ||--o{ FINANCIAL_ADVICES : receives
    CREDIT_CARDS ||--o{ TRANSACTIONS : generates

   * USERS {
        bigint id PK
        string email UK
        string password_hash
        string secret_2fa
        string region
        timestamp created_at
    }

  *  USER_INCOMES {
        bigint id PK
        bigint user_id FK
        decimal amount_net
        boolean is_audited_payroll
        timestamp recorded_at
    }

   * CREDIT_CARDS {
        bigint id PK
        bigint user_id FK
        string bank_name
        string card_alias
        string last_four_digits
    }

   * FINANCIAL_ADVICES {
        bigint id PK
        bigint user_id FK
        text advice_text
        string advice_hash
        timestamp updated_at
    }

   * TRANSACTIONS {
        bigint id PK
        bigint card_id FK
        date transaction_date
        string description
        decimal amount_mxn
        string original_currency
    }


### 📦 Capas y Componentes Clave

#### 1. Capa de Cliente (Frontend)
* **App Web & Android (KMP):** Clientes desarrollados con **Kotlin Multiplatform**, compartiendo la lógica de negocio central entre plataformas.
* **Seguridad y Comunicación:** Conexión mediante **REST/gRPC** protegida bajo el estándar **OAuth2**.

#### 2. Gateway y Enrutamiento
* **API Gateway (Ktor + Coroutines):** Punto único de entrada encargado de la verificación de tokens, enrutamiento de peticiones y gestión de solicitudes asíncronas de manera no bloqueante.

#### 3. Servicios del Backend (Microservicios)
* **Servicio de Autenticación (Auth):** Gestiona la seguridad mediante tokens **JWT** y autenticación de dos factores (**2FA**).
* **Servicio de IA:** Encargado de la integración con **Google Gemini (API REST)** para el procesamiento de prompts. Trabaja de forma asíncrona mediante eventos.
* **Servicio de Finanzas:** Motor de libro mayor que ejecuta transacciones críticas con propiedades **ACID**.
* **Servicio de Notificaciones:** Sistema encargado de enviar alertas push en tiempo real a los dispositivos de los usuarios.

#### 4. Mensajería y Persistencia
* **Cola de Mensajes (RabbitMQ / Redis):** Intermediario (*Message Broker*) que desacopla los servicios permitiendo una comunicación síncrona/asíncrona mediante publicación y suscripción de eventos.
* **Base de Datos (PostgreSQL):** Base de datos relacional central con almacenamiento **cifrado**, soporte de caché y persistencia segura de datos financieros y de usuario.
* **Servicios Push Externos:** Integración con **FCM** (Firebase Cloud Messaging) y **APNs** (Apple Push Notification service) para la entrega final de alertas.a

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

Pruebas web:
	• Target Wasm: ./gradlew :app:shared:wasmJsTest
	
	• Target JS: ./gradlew :app:shared:jsTest
Pruebas de iOS: 
./gradlew :app:shared:iosSimulatorArm64Test

Aprende más sobre Kotlin Multiplatform, Compose Multiplatform, Kotlin/Wasm…
Agradeceríamos tus comentarios sobre Compose/Web y Kotlin/Wasm en el canal público de Slack #compose-web. Si encuentras algún problema, por favor repórtalo en YouTrack.
