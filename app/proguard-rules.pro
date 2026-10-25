# 保留 WebView JS 接口与数据层
-keep class com.qingguang.browser.web.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
