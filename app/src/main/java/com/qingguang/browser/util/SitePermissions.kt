package com.qingguang.browser.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * 站点级权限：每个域名一份，未记录的站点使用默认策略（全部允许）。
 * 用户可随时在「权限中心」查看与修改，浏览器不做任何隐式决定。
 */
data class SitePermission(
    val host: String,
    val allowGeo: Boolean = true,
    val allowCamera: Boolean = true,
    val allowPopup: Boolean = true,
    val allowDownload: Boolean = true,
    val allowJs: Boolean = true,
    val allowImages: Boolean = true
) {
    fun summary(): String = buildList {
        if (allowGeo) add("定位") else add("禁定位")
        if (allowCamera) add("摄像头/麦克风") else add("禁摄像头")
        if (allowPopup) add("弹窗") else add("禁弹窗")
        if (allowDownload) add("下载") else add("禁下载")
        if (allowJs) add("JS") else add("禁JS")
        if (allowImages) add("图片") else add("禁图片")
    }.joinToString(" · ")
}

object SitePermissionStore {

    fun load(settings: SettingsStore): List<SitePermission> {
        val raw = settings.sitePermissionsJson
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val host = obj.optString("host").trim().lowercase()
                if (host.isBlank()) return@mapNotNull null
                SitePermission(
                    host = host,
                    allowGeo = obj.optBoolean("allowGeo", true),
                    allowCamera = obj.optBoolean("allowCamera", true),
                    allowPopup = obj.optBoolean("allowPopup", true),
                    allowDownload = obj.optBoolean("allowDownload", true),
                    allowJs = obj.optBoolean("allowJs", true),
                    allowImages = obj.optBoolean("allowImages", true)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(settings: SettingsStore, permissions: List<SitePermission>) {
        val array = JSONArray()
        permissions.distinctBy { it.host }.forEach { permission ->
            array.put(JSONObject().apply {
                put("host", permission.host)
                put("allowGeo", permission.allowGeo)
                put("allowCamera", permission.allowCamera)
                put("allowPopup", permission.allowPopup)
                put("allowDownload", permission.allowDownload)
                put("allowJs", permission.allowJs)
                put("allowImages", permission.allowImages)
            })
        }
        settings.sitePermissionsJson = array.toString()
    }

    /** 取站点策略；未单独设置时返回默认全允许 */
    fun policyFor(settings: SettingsStore, host: String): SitePermission {
        val key = host.trim().lowercase()
        return load(settings).firstOrNull { it.host == key } ?: SitePermission(host = key)
    }

    /** 写入或更新一条站点策略 */
    fun upsert(settings: SettingsStore, permission: SitePermission) {
        val host = permission.host.trim().lowercase()
        if (host.isBlank()) return
        val list = load(settings).toMutableList()
        val index = list.indexOfFirst { it.host == host }
        if (index >= 0) list[index] = permission.copy(host = host) else list.add(permission.copy(host = host))
        save(settings, list)
    }

    fun remove(settings: SettingsStore, host: String) {
        save(settings, load(settings).filterNot { it.host == host.lowercase() })
    }
}

/** 从 URL 里取主机名，用于匹配站点权限 */
fun hostOf(url: String): String {
    val text = url.trim()
    if (text.isBlank()) return ""
    return runCatching {
        val withoutScheme = text.substringAfter("://", text)
        val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@')
        host.substringBefore(':').lowercase()
    }.getOrDefault("")
}
