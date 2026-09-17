# Servidor y Simulador Bancario ISO 8583

Sistema de procesamiento de transacciones financieras basado en el estándar internacional **ISO 8583:1987**, implementado sobre **Java 21**, **Spring Boot**, el framework de mensajería bancaria **jPOS** y persistencia transaccional en **Microsoft SQL Server**.

El sistema integra un servidor de sockets TCP multihilo de alto rendimiento y un cliente emulador interactivo de terminales punto de venta (POS / Datáfonos), incorporando patrones avanzados de resiliencia y seguridad bancaria: **Idempotencia**, **Rate Limiting** y **Circuit Breaker**.

---

## 🚀 Tecnologías Utilizadas

| Categoría | Tecnología / Librería | Versión | Propósito en el Proyecto |
|---|---|:---:|---|
| **Lenguaje** | **Java** | 21 (LTS) | Plataforma de desarrollo principal. |
| **Framework Backend** | **Spring Boot** | 4.x | Inyección de dependencias, gestión del ciclo de vida y configuración sin servidor web (`web-application-type=none`). |
| **Mensajería Bancaria** | **jPOS** | 2.1.9 | Protocolo ISO 8583: empaquetado, desempaquetado, servidor socket TCP (`ISOServer`) y canales ASCII (`ASCIIChannel`). |
| **Base de Datos** | **Microsoft SQL Server** | 2014+ | Motor relacional para almacenamiento de cuentas, balances e historial de idempotencia. |
| **Acceso a Datos** | **Spring JDBC (JdbcTemplate)** | - | Ejecución optimizada de procedimientos almacenados con `SimpleJdbcCall` y pool de conexiones **HikariCP**. |
| **Tolerancia a Fallos** | **Resilience4j** | - | Patrón **Circuit Breaker** sobre el Core Banking para evitar caídas en cascada ante indisponibilidad de la base de datos. |
| **Productividad** | **Lombok** | - | Generación de getters, setters, constructores, builders y logs con `@Slf4j`. |
| **Construcción** | **Gradle** | 9.x | Gestión de dependencias, compilación y empaquetado. |
| **Testing** | **JUnit 5 / Spring Test** | - | Pruebas de integración automatizadas para flujos de red, idempotencia y reversos. |

---

## 🏛️ Arquitectura del Sistema

El proyecto está diseñado bajo una arquitectura desacoplada de dos capas (Cliente Emulador y Servidor Socket TCP):

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        LADO CLIENTE (EMULADOR)                         │
│                                                                        │
│   [ ClientEmulatorRunner ]  ──>  Consola interactiva de usuario        │
│              │                                                         │
│              ▼                                                         │
│   [ SimulatorController ]   ──>  Fachada orquestadora del cliente      │
│              │                                                         │
│              ▼                                                         │
│   [ ClientSimulatorService ]──>  Motor cliente del datáfono / POS       │
│                                  (Empaqueta objetos ISOMsg a bytes)    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                         SOCKET TCP (PUERTO 9999)
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                        LADO SERVIDOR (BACKEND)                         │
│                                                                        │
│   [ IsoTcpServer ]          ──>  Receptor de red en hilo background    │
│              │                   (Desempaqueta bytes a objetos ISOMsg) │
│              ▼                                                         │
│   [ IsoMessageHandler ]     ──>  Pipeline y enrutador del servidor:    │
│              │                   • Filtro 1: Rate Limiting             │
│              │                   • Filtro 2: Idempotencia (Duplicados) │
│              │                   • Filtro 3: Enrutador de negocio      │
│              ▼                                                         │
│   [ CoreBankingService ]    ──>  Protegido con @CircuitBreaker         │
│              │                                                         │
│              ▼                                                         │
│   [ SQL Server (ISO8583DB)] ──>  Procedimientos Almacenados ACID       │
│                                  (sp_ProcesarCompra, sp_ConsultarSaldo)│
└────────────────────────────────────────────────────────────────────────┘
```

---

## 💳 Protocolo y Estándar ISO 8583

### 1. Mensajes Implementados (MTIs)

* **`0200` (Financial Transaction Request):** Solicitud de autorización de compra o consulta de saldo enviada por el datáfono.
* **`0210` (Financial Transaction Response / Advice):** Respuesta de aprobación del servidor bancario y mensaje de confirmación del cliente.
* **`0400` (Reversal Request):** Solicitud de anulación/reverso para reintegrar el dinero a la cuenta del cliente.
* **`0410` (Reversal Response / Advice):** Confirmación de anulación procesada con éxito.
* **`0800` / `0810`:** Mensajes de prueba y gestión de red (*Network Management*).

### 2. Códigos de Respuesta ISO 8583 (Campo 39)

| Código | Significado Bancario | Situación en la Aplicación |
|:---:|---|---|
| **`00`** | **Approved (Aprobada)** | Compra debitada exitosamente o reverso completado. |
| **`13`** | **Invalid Transaction (Rate Limit)** | Terminal bloqueada por superar el límite de peticiones por minuto. |
| **`14`** | **Invalid Card Number** | La tarjeta no existe en la base de datos SQL Server. |
| **`51`** | **Insufficient Funds** | El saldo disponible es menor al monto de la compra. |
| **`91`** | **Issuer Inoperative (Circuit Breaker)** | Falla inducida o base de datos no disponible; circuito abierto. |
| **`96`** | **System Malfunction** | Error imprevisto de socket o excepción no controlada. |

### 3. Campos de Datos Activos (`CustomIsoPackager`)

| Campo | Formato | Nombre ISO | Uso en el Sistema |
|:---:|---|---|---|
| **0** | `NUMERIC(4)` | MTI | Identificador del tipo de mensaje (`0200`, `0210`, `0400`, `0410`). |
| **1** | `BITMAP(16)` | Bit Map | Mapa hexadecimal que define qué campos viajan en la trama. |
| **2** | `LLNUM(19)` | PAN (Primary Account Number) | Número de tarjeta/cuenta (ej. `4000000000000001`). |
| **3** | `NUMERIC(6)` | Processing Code | `000000` para Compras; `310000` para Consulta de Saldo. |
| **4** | `NUMERIC(12)` | Amount, Transaction | Monto en centavos relleno con ceros a la izquierda (ej. `000000050000`). |
| **7** | `NUMERIC(10)` | Transmission Date & Time | Fecha y hora de transmisión (`MMddHHmmss`). |
| **11** | `NUMERIC(6)` | STAN | *System Trace Audit Number* de 6 dígitos para auditoría. |
| **37** | `CHAR(12)` | RRN | *Retrieval Reference Number* único de 12 dígitos para **idempotencia**. |
| **39** | `CHAR(2)` | Response Code | Código de resultado devuelto por el servidor (`00`, `14`, `51`, etc.). |
| **41** | `CHAR(8)` | Terminal ID | Identificador de la terminal (ej. `TERM0001`) para **Rate Limiting**. |
| **42** | `CHAR(15)` | Merchant ID | Código del comercio adquirente (`MERCHANT000001`). |
| **43** | `CHAR(40)` | Card Acceptor Name/Location | Nombre y ubicación comercial (`AVENIDA PRINCIPAL 123`). |
| **49** | `NUMERIC(3)` | Currency Code | Código numérico ISO 4217 de la divisa (`484` = Pesos). |
| **54** | `LLLCHAR(120)` | Additional Amounts | Transporta el saldo devuelto en la consulta de saldo. |

---

## 🛡️ Mecanismos de Seguridad y Resiliencia

### 1. Idempotencia Transaccional
* **Objetivo:** Evitar cobros dobles si el usuario presiona varias veces el botón de pago o si hay reintentos de red.
* **Funcionamiento:** En cada compra se genera una llave única `idempotency:STAN:RRN:TERMINAL`.
  * La primera solicitud ejecuta el débito en SQL Server y almacena el resultado en `dbo.IDEMPOTENCIA`.
  * Los envíos duplicados posteriores son interceptados por `sp_ConsultarIdempotencia`: devuelven la misma respuesta aprobada (`RC 00`) desde el historial sin volver a descontar dinero.

### 2. Rate Limiting (Protección contra Spam / Fraude)
* **Configuración:** Máximo **4 transacciones** por ventana de **60 segundos** por cada terminal (`TERM0001`).
* **Comportamiento:** Implementado en memoria con `ConcurrentHashMap` y ventana deslizante. Al 5° clic dentro del mismo minuto, la petición es rechazada de inmediato con **`RC 13`** antes de llegar a la base de datos.

### 3. Circuit Breaker (Resilience4j)
* **Objetivo:** Proteger al servidor ISO 8583 y a SQL Server ante caídas o saturación.
* **Configuración (`application.properties`):**
  * `failureRateThreshold = 50%`
  * `minimumNumberOfCalls = 4`
  * `waitDurationInOpenState = 10s`
* **Demostración en vivo:** Al ingresar el monto especial de prueba **`$999.99`**, se induce una excepción controlada en `CoreBankingService`. Resilience4j activa el método fallback y responde con **`RC 91`** sin colapsar el socket ni la aplicación.

---

## 🗄️ Base de Datos y Procedimientos Almacenados (SQL Server)

El archivo `schema-sqlserver.sql` estructura la base de datos `ISO8583DB`:

### Tablas:
1. **`dbo.CUENTAS`:** Almacena el número de tarjeta (`pan` PK), saldo en centavos (`balance BIGINT`) y fecha de creación.
2. **`dbo.IDEMPOTENCIA`:** Almacena la llave de idempotencia (`idempotency_key` PK), STAN, RRN, terminal, código de respuesta y fecha.

### Procedimientos Almacenados:
* **`dbo.sp_ProcesarCompra`:** Ejecuta una transacción atómica con bloqueo pesimista `WITH (UPDLOCK)`. Valida existencia de tarjeta (`RC 14`), suficiencia de fondos (`RC 51`), descuenta el balance (`UPDATE`) y confirma con `COMMIT`.
* **`dbo.sp_ConsultarSaldo`:** Consulta segura de solo lectura que devuelve el saldo actual en centavos.
* **`dbo.sp_GuardarIdempotencia`:** Inserta de forma segura el resultado de una transacción para auditoría y caché.
* **`dbo.sp_ConsultarIdempotencia`:** Comprueba si una transacción ya fue procesada anteriormente.

---

## 🕹️ Flujo y Menú de la Aplicación

Al iniciar la aplicación, se despliega la consola interactiva (`ClientEmulatorRunner`):

```text
----------------------------- MENÚ -----------------------------
 [1] Realizar compra (0200) 
 [2] Consultar Saldo actual de una cuenta
 [3] Realizar compra (0400)
 [q] Salir
Selecciona una opción: 
```

### Opción [1] - Realizar compra (0200) [Demostración de Idempotencia y Rate Limit]
1. Solicita Tarjeta y Monto en pesos (ej. `$500.00`).
2. Valida previamente en la base de datos con `sp_ConsultarSaldo`. Si la tarjeta no existe o no tiene saldo, cancela la operación antes de cobrar.
3. Simula una ráfaga de **5 clics** de usuario sobre la misma transacción:
   * **Clic 1:** Compra autorizada (`RC 00`), descuento aplicado en SQL Server y notificación `0210` enviada al servidor.
   * **Clics 2, 3 y 4:** Respuestas devueltas por **idempotencia** (`RC 00`) sin cobrar de nuevo.
   * **Clic 5:** Bloqueado por **Rate Limiting** (`RC 13`).

### Opción [2] - Consultar Saldo actual de una cuenta
* Envía un mensaje ISO `0200` con `Processing Code 310000` y muestra el balance actual de la tarjeta seleccionada.

### Opción [3] - Realizar compra con Anulación (0400)
1. Pre-valida la tarjeta y muestra el **Saldo ANTES**.
2. Procesa la compra `0200` y muestra el **Saldo DURANTE** (con el descuento aplicado).
3. Solicita confirmación del usuario para cancelar la operación.
4. Envía la solicitud de reverso **`0400`**, reintegrando el dinero a la cuenta en SQL Server.
5. Envía la confirmación de anulación **`0410`** al servidor.
6. Muestra el **Saldo DESPUÉS** demostrando que el balance regresó exactamente a su estado inicial.

---

## ⚙️ Requisitos e Instalación

### Requisitos Previos:
* **Java Development Kit (JDK):** Versión 21 o superior.
* **Microsoft SQL Server:** Instancia local (ej. SQLEXPRESS en puerto 1433).
* **Gradle:** Incluido a través de `./gradlew` (Linux/Mac) o `.\gradlew.bat` (Windows).

### 1. Configuración de Base de Datos
1. Abre SQL Server Management Studio (SSMS) o la terminal con `sqlcmd`.
2. Crea la base de datos y ejecuta el script:
   ```sql
   CREATE DATABASE ISO8583DB;
   GO
   ```
3. Ejecuta el archivo [`schema-sqlserver.sql`](ISO8583/src/main/resources/schema-sqlserver.sql) para crear las tablas, procedimientos almacenados y tarjetas de prueba.

### 2. Configuración de Credenciales
Verifica en [`application.properties`](ISO8583/src/main/resources/application.properties):
```properties
iso8583.server.port=9999
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=ISO8583DB;encrypt=false;trustServerCertificate=true;sslProtocol=TLSv1
spring.datasource.username=iso_user
spring.datasource.password=IsoPassword123!
```

---

## 🧪 Ejecución y Pruebas

### Iniciar la Aplicación:
Desde la terminal en la carpeta del proyecto `ISO8583`:
```powershell
.\gradlew.bat bootRun
```
O ejecutando la clase principal [`Iso8583Application.java`](ISO8583/src/main/java/servidor/ISO8583/Iso8583Application.java) desde tu IDE (IntelliJ IDEA / Eclipse).

### Ejecutar Pruebas Automatizadas:
El proyecto cuenta con una suite completa de pruebas de integración que valida el socket TCP, la idempotencia y las operaciones en SQL Server:
```powershell
.\gradlew.bat test
```

### Consultar Logs:
Los eventos detallados de red, desempaquetado de tramas y base de datos se registran en:
* `logs/iso8583-server.log`

---

## 👥 Tarjetas de Prueba Preconfiguradas

| Tarjeta (PAN) | Saldo Inicial | Propósito en las Pruebas |
|---|:---:|---|
| **`4000000000000001`** | $50,000.00 | Tarjeta principal para compras exitosas, idempotencia y anulaciones. |
| **`4000000000000002`** | $1,000.00 | Tarjeta secundaria para pruebas de compras menores. |
| **`4000000000000003`** | $0.00 | Tarjeta sin saldo para verificar el rechazo por **Saldo Insuficiente (`RC 51`)**. |
| **`9999999999999999`** | Inexistente | Número no registrado para verificar el rechazo por **Cuenta Incorrecta (`RC 14`)**. |
