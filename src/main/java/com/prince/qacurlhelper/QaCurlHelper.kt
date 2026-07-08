package com.prince.qacurlhelper

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SearchEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A completely self-contained library to capture API logs (cURL and responses)
 * and display/share them via a programmatic BottomSheet.
 * Can be integrated in any application with 0 dependencies.
 */
object QaCurlHelper {

    private var teamsWebhookUrl: String? = null
    private var isInspectorEnabled: Boolean = false
    
    private const val MAX_LOGS = 20
    private val apiLogs = CopyOnWriteArrayList<ApiLog>()

    data class ApiLog(
        val timestamp: String,
        val method: String,
        val url: String,
        val curl: String,
        val responseCode: Int,
        val responseBody: String
    )

    /**
     * Initializes the helper.
     * @param application The Android Application context.
     * @param webhookUrl The Microsoft Teams Webhook URL.
     * @param enableInspector Gated flag to control whether the inspector gesture is active (e.g. only on QA/Debug builds).
     */
    fun init(application: Application, webhookUrl: String, enableInspector: Boolean) {
        this.teamsWebhookUrl = webhookUrl
        this.isInspectorEnabled = enableInspector

        if (enableInspector) {
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    val currentCallback = activity.window.callback
                    if (currentCallback != null) {
                        activity.window.callback = QaTouchInterceptorCallback(currentCallback, activity) {
                            showBottomSheet(activity)
                        }
                    }
                }
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
        }
    }

    /**
     * Records a new API call log.
     */
    fun recordLog(log: ApiLog) {
        if (!isInspectorEnabled) return
        if (apiLogs.size >= MAX_LOGS) {
            apiLogs.removeAt(0)
        }
        apiLogs.add(log)
    }

    private fun showBottomSheet(context: Context) {
        if (apiLogs.isEmpty()) {
            Toast.makeText(context, "No API calls recorded yet", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(context)
        var currentIndex = apiLogs.size - 1

        // Programmatic Layout creation
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            padding(16)
            setBackgroundColor(Color.parseColor("#121212")) // Premium Dark theme
        }

        // Header
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = TextView(context).apply {
            text = "API Inspector"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerLayout.addView(titleTv)
        root.addView(headerLayout)

        // Navigation bar
        val navLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }

        val prevBtn = Button(context).apply {
            text = "< Prev"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        val progressTv = TextView(context).apply {
            setTextColor(Color.LTGRAY)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nextBtn = Button(context).apply {
            text = "Next >"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        navLayout.addView(prevBtn)
        navLayout.addView(progressTv)
        navLayout.addView(nextBtn)
        root.addView(navLayout)

        // API Info
        val infoTv = TextView(context).apply {
            setTextColor(Color.parseColor("#80FFEA")) // Sleek Teal
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
        }
        root.addView(infoTv)

        // Tabs or sections for Curl and Response
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // cURL Section Title
        val curlTitle = TextView(context).apply {
            text = "cURL Command"
            setTextColor(Color.parseColor("#FFB74D")) // Orange Accent
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 8, 0, 4)
        }
        contentLayout.addView(curlTitle)

        // cURL Content Scroll View
        val curlScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 120))
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(8, 8, 8, 8)
        }
        val curlTv = TextView(context).apply {
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        curlScroll.addView(curlTv)
        contentLayout.addView(curlScroll)

        // Response Section Title
        val responseTitle = TextView(context).apply {
            text = "Response Body"
            setTextColor(Color.parseColor("#FFB74D"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 12, 0, 4)
        }
        contentLayout.addView(responseTitle)

        // Response Content Scroll View
        val responseScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 160))
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(8, 8, 8, 8)
        }
        val responseTv = TextView(context).apply {
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        responseScroll.addView(responseTv)
        contentLayout.addView(responseScroll)

        root.addView(contentLayout)

        // Action Buttons Row
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        val copyCurlBtn = Button(context).apply {
            text = "Copy cURL"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#80FFEA"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 4
            }
        }

        val shareTeamsBtn = Button(context).apply {
            text = "Share Teams"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6200EE"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                marginStart = 4
            }
        }

        buttonRow.addView(copyCurlBtn)
        buttonRow.addView(shareTeamsBtn)
        root.addView(buttonRow)

        // Render Current Log
        fun updateUi() {
            val log = apiLogs[currentIndex]
            progressTv.text = "Log ${currentIndex + 1} of ${apiLogs.size}"
            infoTv.text = "${log.method} - Code ${log.responseCode}\nTime: ${log.timestamp}\nURL: ${log.url}"
            curlTv.text = log.curl
            responseTv.text = log.responseBody

            prevBtn.isEnabled = currentIndex > 0
            nextBtn.isEnabled = currentIndex < apiLogs.size - 1

            prevBtn.alpha = if (prevBtn.isEnabled) 1.0f else 0.4f
            nextBtn.alpha = if (nextBtn.isEnabled) 1.0f else 0.4f
        }

        prevBtn.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                updateUi()
            }
        }

        nextBtn.setOnClickListener {
            if (currentIndex < apiLogs.size - 1) {
                currentIndex++
                updateUi()
            }
        }

        copyCurlBtn.setOnClickListener {
            val log = apiLogs[currentIndex]
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("cURL Command", log.curl)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "cURL copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        shareTeamsBtn.setOnClickListener {
            val log = apiLogs[currentIndex]
            shareTeamsBtn.isEnabled = false
            shareTeamsBtn.text = "Sending..."
            postToTeams(log) { success ->
                Handler(Looper.getMainLooper()).post {
                    shareTeamsBtn.isEnabled = true
                    shareTeamsBtn.text = "Share Teams"
                    if (success) {
                        Toast.makeText(context, "Shared to Teams successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to share to Teams", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        updateUi()

        dialog.setContentView(root)
        dialog.show()
    }

    private fun postToTeams(log: ApiLog, callback: (Boolean) -> Unit) {
        val url = teamsWebhookUrl
        if (url == null || url.startsWith("https://outlook.office.com/webhook/placeholder")) {
            callback(false)
            return
        }

        val client = OkHttpClient()

        // Format message card payload
        val escapedUrl = escapeJson(log.url)
        val escapedCurl = escapeJson(log.curl)
        
        // Pretty print response body if it's JSON
        val prettyResponse = prettyPrintJson(log.responseBody)
        val escapedResponse = escapeJson(
            if (prettyResponse.length > 5000) prettyResponse.take(5000) + "\n...[Truncated]"
            else prettyResponse
        )

        val jsonPayload = """
        {
            "@type": "MessageCard",
            "@context": "http://schema.org/extensions",
            "themeColor": "0076D7",
            "summary": "API cURL & Response shared from VineVibe app",
            "sections": [{
                "activityTitle": "QA Network Log Details",
                "activitySubtitle": "Shared at ${log.timestamp}",
                "facts": [
                    {"name": "Method", "value": "${log.method}"},
                    {"name": "Response Code", "value": "${log.responseCode}"},
                    {"name": "Request URL", "value": "$escapedUrl"}
                ],
                "text": "### cURL Command\n```bash\n$escapedCurl\n```\n\n### Response Body\n```json\n$escapedResponse\n```"
            }]
        }
        """.trimIndent()

        val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful)
                response.close()
            }
        })
    }

    private fun prettyPrintJson(json: String): String {
        return try {
            val parser = com.google.gson.JsonParser()
            val jsonElement = parser.parse(json)
            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            gson.toJson(jsonElement)
        } catch (e: Exception) {
            json
        }
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000c", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun View.padding(dp: Int) {
        val px = dpToPx(context, dp)
        setPadding(px, px, px, px)
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}

/**
 * A custom window callback that delegates events and intercepts touch gestures for long presses.
 */
class QaTouchInterceptorCallback(
    private val delegate: Window.Callback,
    private val context: Context,
    private val onLongPress: () -> Unit
) : Window.Callback by delegate {
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            onLongPress()
        }
    })

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        event?.let { gestureDetector.onTouchEvent(it) }
        return delegate.dispatchTouchEvent(event)
    }
}

/**
 * OkHttp Interceptor to capture request/response and store them in QaCurlHelper.
 */
class QaCurlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // Reconstruct cURL
        val curl = toCurlCommand(request)
        
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            QaCurlHelper.recordLog(
                QaCurlHelper.ApiLog(
                    timestamp = timestamp,
                    method = request.method,
                    url = request.url.toString(),
                    curl = curl,
                    responseCode = 0,
                    responseBody = "Network Error: ${e.localizedMessage}"
                )
            )
            throw e
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        var responseBodyString = ""
        
        try {
            val peekedResponse = response.peekBody(1024 * 1024)
            responseBodyString = peekedResponse.string()
        } catch (e: Exception) {
            responseBodyString = "Could not parse response: ${e.localizedMessage}"
        }

        QaCurlHelper.recordLog(
            QaCurlHelper.ApiLog(
                timestamp = timestamp,
                method = request.method,
                url = request.url.toString(),
                curl = curl,
                responseCode = response.code,
                responseBody = responseBodyString
            )
        )

        return response
    }

    private fun toCurlCommand(request: Request): String {
        val builder = StringBuilder("curl -X ").append(request.method)

        for (i in 0 until request.headers.size) {
            val name = request.headers.name(i)
            val value = request.headers.value(i)
            builder.append(" \\\n  -H \"").append(name).append(": ").append(value).append("\"")
        }

        request.body?.let { requestBody ->
            val buffer = Buffer()
            try {
                requestBody.writeTo(buffer)
                val charset = requestBody.contentType()?.charset(Charset.forName("UTF-8")) ?: Charset.forName("UTF-8")
                val bodyString = buffer.readString(charset)
                if (!TextUtils.isEmpty(bodyString)) {
                    val escapedBody = bodyString.replace("\"", "\\\"").replace("\n", "\\n")
                    builder.append(" \\\n  -d \"").append(escapedBody).append("\"")
                }
            } catch (e: Exception) {
                // Ignore body read errors
            }
        }

        builder.append(" \\\n  \"").append(request.url.toString()).append("\"")
        return builder.toString()
    }
}
