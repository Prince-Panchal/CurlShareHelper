# QaCurlHelper

`QaCurlHelper` is a lightweight, self-contained Android library designed to simplify API inspection during QA and development. It intercepts OkHttp network requests, formats them into `cURL` commands, displays logs in a clean programmatic dark-themed `BottomSheetDialog`, and allows sharing them directly to Microsoft Teams via webhooks.

## Features

- **Zero-Dependency UI**: Displays a premium dark-themed BottomSheet with no layout XML dependencies (built programmatically).
- **OkHttp Interceptor**: Captures requests, responses, headers, and automatically generates copyable `cURL` commands.
- **Easy Trigger**: Uses a window callback gesture detector to open the inspector easily (e.g., long press on screen).
- **Microsoft Teams Integration**: Share cURL logs and API responses directly to your Microsoft Teams channel using Webhooks.
- **Production Gated**: Easily enable or disable the helper (e.g., only active on `Debug`/`QA` builds).

---

## Installation

### 1. Add Repository
In your consumer project's `settings.gradle.kts` file:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // If consuming locally:
        mavenLocal()
        // If consuming via JitPack:
        maven("https://jitpack.io")
    }
}
```

### 2. Add Dependency
In your consumer project's app-level `build.gradle.kts` file:

```kotlin
dependencies {
    // If using local mavenLocal publishing:
    implementation("com.prince:qacurlhelper:1.0.0")
    
    // If using JitPack (replace with your GitHub username & release/commit hash):
    // implementation("com.github.YOUR_GITHUB_USERNAME:qacurlhelper:1.0.0")
}
```

---

## Usage

### 1. Initialize the Helper
Initialize `QaCurlHelper` in your `Application` class:

```kotlin
import android.app.Application
import com.prince.qacurlhelper.QaCurlHelper

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize the helper
        QaCurlHelper.init(
            application = this,
            webhookUrl = "https://outlook.office.com/webhook/your-teams-webhook-url",
            enableInspector = BuildConfig.DEBUG // Enable only in debug/QA builds
        )
    }
}
```

### 2. Register the OkHttp Interceptor
Add `QaCurlInterceptor` to your `OkHttpClient` configuration:

```kotlin
import okhttp3.OkHttpClient
import com.prince.qacurlhelper.QaCurlInterceptor

val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(QaCurlInterceptor()) // Add the interceptor here
    .build()
```

### 3. Open the Inspector
Simply **long-press** anywhere on your screen when `enableInspector` is active, and the dark-themed API Inspector BottomSheet will pop up containing your latest API logs!
