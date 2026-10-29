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
    val allowMic: Boolean = true,
    val allowPopup: Boolean = true,
    val allowDownload: Boolean = true,
    val allowJs: Boolean = true,
    val allowImages: Boolean = true,
    /** 麦克风/摄像头按次询问（默认关闭，开启后每次网页请求都需要确认） */
    val askEachTime: Boolean = false,
    /** 夜间网页调色：-1 跟随全局，0 强制关闭，1 强制开启 */
    val nightMode: Int = -1,
    /** 授权有效期（到期自动失效）；0 表示永久 */
    val expiresAt: Long = 0L
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt in 1 until now

    fun summary(): String = buildList {
        if (allowGeo) add("定位") else add("禁定位")
        if (allowCamera) add("摄像头") else add("禁摄像头")
        if (allowMic) add("麦克风") else add("禁麦克风")
        if (askEachTime) add("每次询问")
        if (allowPopup) add("弹窗") else add("禁弹窗")
        if (allowDownload) add("下载") else add("禁下载")
        if (allowJs) add("JS") else add("禁JS")
        if (allowImages) add("图片") else add("禁图片")
        if (nightMode == 1) add("夜间调色") else if (nightMode == 0) add("禁夜间调色")
        if (isExpired()) add("已过期")
        else if (expiresAt > 0) add("有期限")
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
                    allowMic = obj.optBoolean("allowMic", true),
                    allowPopup = obj.optBoolean("allowPopup", true),
                    allowDownload = obj.optBoolean("allowDownload", true),
                    allowJs = obj.optBoolean("allowJs", true),
                    allowImages = obj.optBoolean("allowImages", true),
                    askEachTime = obj.optBoolean("askEachTime", false),
                    nightMode = obj.optInt("nightMode", -1),
                    expiresAt = obj.optLong("expiresAt", 0L)
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
                put("allowMic", permission.allowMic)
                put("allowPopup", permission.allowPopup)
                put("allowDownload", permission.allowDownload)
                put("allowJs", permission.allowJs)
                put("allowImages", permission.allowImages)
                put("askEachTime", permission.askEachTime)
                put("nightMode", permission.nightMode)
                put("expiresAt", permission.expiresAt)
            })
        }
        settings.sitePermissionsJson = array.toString()
    }

    /**
     * 查找站点策略：先精确匹配，再匹配通配条目（`.example.com` 覆盖其全部子域）。
     * 未配置时返回 null。
     */
    fun find(settings: SettingsStore, host: String): SitePermission? {
        val key = host.trim().lowercase()
        if (key.isBlank()) return null
        // 到期条目直接视为未配置（回退到默认策略）
        val list = load(settings).filterNot { it.isExpired() }
        list.firstOrNull { it.host == key }?.let { return it }
        val wildcard = list.firstOrNull { entry ->
            val raw = entry.host
            if (!raw.startsWith(".") && !raw.startsWith("*.")) return@firstOrNull false
            val suffix = raw.removePrefix("*").removePrefix(".")
            key == suffix || key.endsWith(".$suffix")
        }
        return wildcard?.copy(host = key)
    }

    /** 取站点策略；未单独设置时返回默认全允许 */
    fun policyFor(settings: SettingsStore, host: String): SitePermission {
        val key = host.trim().lowercase()
        return find(settings, key) ?: SitePermission(host = key)
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
