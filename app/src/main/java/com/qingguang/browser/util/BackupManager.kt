package com.qingguang.browser.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupInfo(
    val name: String,
    val host: String,
    val createdAt: Long,
    val sizeBytes: Long,
    /** 私有目录备份时有值 */
    val file: File? = null,
    /** 自定义 SAF 目录备份时有值 */
    val uri: Uri? = null
)

/**
 * 站点数据备份。
 * 备份内容：Cookie + localStorage + IndexedDB（库结构、索引与全部记录）。
 * 备份目录可以是应用私有目录（默认），也可以是用户通过 SAF 指定的任意文件夹。
 * 同一站点只保留最新一份，旧文件自动删除。
 */
class BackupManager(
    private val context: Context,
    private val settings: SettingsStore
) {

    val defaultDir: File
        get() = File(context.filesDir, "backups").apply { mkdirs() }

    /** 是否使用用户自定义目录 */
    val usesCustomDir: Boolean
        get() = customRoot() != null

    /** 当前备份目录的可读描述 */
    fun locationLabel(): String {
        val root = customRoot() ?: return defaultDir.absolutePath
        return runCatching { Uri.decode(root.uri.toString()) }.getOrDefault(root.uri.toString())
    }

    /** 设置自定义备份目录并持久化读取权限 */
    fun setCustomDir(uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }.isSuccess.also { ok ->
        if (ok) settings.backupTreeUri = uri.toString()
    }

    /** 恢复为应用私有目录 */
    fun resetToDefault() {
        settings.backupTreeUri = null
    }

    private fun customRoot(): DocumentFile? {
        val raw = settings.backupTreeUri ?: return null
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(raw)) ?: return null
        return tree.takeIf { it.exists() && it.canWrite() }
    }

    suspend fun listBackups(global: Boolean = false): List<BackupInfo> = withContext(Dispatchers.IO) {
        val root = customRoot()
        if (root != null) {
            root.listFiles()
                .filter { it.isFile && it.name?.endsWith(".json") == true }
                .filter { isGlobalName(it.name.orEmpty()) == global }
                .map { doc ->
                    val name = doc.name ?: ""
                    val parts = name.removeSuffix(".json").split("@")
                    BackupInfo(
                        name = name,
                        host = parts.firstOrNull().orEmpty().ifBlank { name },
                        createdAt = parts.getOrNull(1)?.toLongOrNull() ?: doc.lastModified(),
                        sizeBytes = doc.length(),
                        uri = doc.uri
                    )
                }
                .sortedByDescending { it.createdAt }
        } else {
            defaultDir.listFiles()
                ?.filter { it.isFile && it.extension == "json" }
                ?.filter { isGlobalName(it.name) == global }
                ?.map {
                    val parts = it.nameWithoutExtension.split("@")
                    BackupInfo(
                        name = it.name,
                        host = parts.firstOrNull() ?: it.nameWithoutExtension,
                        createdAt = parts.getOrNull(1)?.toLongOrNull() ?: it.lastModified(),
                        sizeBytes = it.length(),
                        file = it
                    )
                }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
        }
    }

    /** 写入一个 JSON 备份文件（全局数据备份复用同一目录） */
    fun writeText(fileName: String, text: String): Boolean = runCatching {
        val root = customRoot()
        if (root != null) {
            val target = root.findFile(fileName)
                ?: root.createFile("application/json", fileName)
                ?: error("无法在所选目录创建文件")
            context.contentResolver.openOutputStream(target.uri)?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("无法写入所选目录")
        } else {
            File(defaultDir, fileName).writeText(text, Charsets.UTF_8)
        }
    }.isSuccess

    private fun isGlobalName(name: String): Boolean = name.startsWith(GLOBAL_PREFIX)

    /** 等待网页回报 IndexedDB 导出结果的回调（同一时间只允许一个备份任务） */
    private var pendingIndexedDb: ((String) -> Unit)? = null
    private var pendingRestoreDone: ((Boolean, String) -> Unit)? = null

    /** 由网页 JS 调用：IndexedDB 导出完成 */
    fun deliverIndexedDb(raw: String?) {
        val callback = pendingIndexedDb ?: return
        pendingIndexedDb = null
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            callback(raw ?: "[]")
        }
    }

    /** 由网页 JS 调用：IndexedDB 恢复完成 */
    fun deliverIndexedDbRestored() {
        val callback = pendingRestoreDone ?: return
        pendingRestoreDone = null
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            callback(true, "已恢复 Cookie、localStorage 与 IndexedDB，正在刷新页面")
        }
    }

    /** 导出当前页面的 Cookie、localStorage 与 IndexedDB。回调在主线程。 */
    fun backupSite(
        webView: WebView,
        url: String,
        title: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        webView.evaluateJavascript(EXPORT_LOCAL_STORAGE_JS) { raw ->
            val localStorageJson = decodeJsString(raw) ?: "{}"
            val host = Uri.parse(url).host ?: "local"
            val safeHost = host.replace(Regex("[^A-Za-z0-9.-]"), "_")
            val now = System.currentTimeMillis()
            val fileName = "$safeHost@$now.json"

            fun write(indexedDbJson: String) {
                val indexedDb = runCatching { org.json.JSONArray(indexedDbJson) }
                    .getOrElse { org.json.JSONArray() }
                val payload = JSONObject().apply {
                    put("version", 2)
                    put("url", url)
                    put("host", host)
                    put("title", title)
                    put("createdAt", now)
                    put("cookies", CookieManager.getInstance().getCookie(url) ?: "")
                    put("localStorage", runCatching { JSONObject(localStorageJson) }.getOrElse { JSONObject() })
                    put("indexedDB", indexedDb)
                }
                if (writeText(fileName, payload.toString())) {
                    pruneOld(safeHost, fileName)
                    onResult(
                        true,
                        "已备份 Cookie、localStorage 与 ${indexedDb.length()} 个 IndexedDB 库到 ${locationLabel()}/$fileName"
                    )
                } else {
                    onResult(false, "备份失败：无法写入备份目录")
                }
            }

            pendingIndexedDb = { indexedDbJson -> write(indexedDbJson) }
            runCatching {
                webView.evaluateJavascript(EXPORT_INDEXED_DB_JS, null)
            }.onFailure {
                pendingIndexedDb = null
                write("[]")
            }
        }
    }

    /** 删除同一站点的旧备份，只保留最新一份 */
    private fun pruneOld(safeHost: String, keepName: String) {
        val root = customRoot()
        if (root != null) {
            root.listFiles()
                .filter {
                    it.isFile && it.name?.endsWith(".json") == true &&
                        it.name != keepName &&
                        it.name?.substringBefore("@") == safeHost
                }
                .forEach { it.delete() }
        } else {
            defaultDir.listFiles()
                ?.filter {
                    it.isFile && it.name.endsWith(".json") &&
                        it.nameWithoutExtension.split("@").firstOrNull() == safeHost &&
                        it.name != keepName
                }
                ?.forEach { it.delete() }
        }
    }

    /** 从备份恢复 Cookie、localStorage 与 IndexedDB。回调在主线程。 */
    fun restore(webView: WebView, info: BackupInfo, onResult: (Boolean, String) -> Unit) {
        val text = runCatching { readText(info) }.getOrNull()
        if (text == null) {
            onResult(false, "备份文件已损坏或无法读取")
            return
        }
        restoreFromText(webView, text, onResult)
    }

    /** 从 JSON 文本恢复（同时兼容「站点数据导出」生成的 JSON）。回调在主线程。 */
    fun restoreFromText(webView: WebView, text: String, onResult: (Boolean, String) -> Unit) {
        val payload = runCatching { JSONObject(text) }.getOrNull()
        if (payload == null) {
            onResult(false, "文件不是有效的 JSON 站点数据")
            return
        }
        // 站点数据导出的 localStorage 也可能是字符串形式，统一兜底
        val url = payload.optString("url").ifBlank { webView.url.orEmpty() }
        val cookies = payload.optString("cookies")
        val localStorage = payload.optJSONObject("localStorage")?.toString() ?: "{}"
        val indexedDb = payload.optJSONArray("indexedDB")?.toString() ?: "[]"

        if (cookies.isNotBlank()) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookies.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                cookieManager.setCookie(url, it)
            }
            cookieManager.flush()
        }

        val localStorageScript = RESTORE_LOCAL_STORAGE_JS.replace("__DATA__", JSONObject.quote(localStorage))
        webView.evaluateJavascript(localStorageScript) {
            pendingRestoreDone = { success, message ->
                webView.reload()
                onResult(success, message)
            }
            val indexedDbScript = RESTORE_INDEXED_DB_JS.replace("__DATA__", JSONObject.quote(indexedDb))
            runCatching {
                webView.evaluateJavascript(indexedDbScript, null)
            }.onFailure {
                pendingRestoreDone = null
                webView.reload()
                onResult(true, "已恢复 Cookie 与 localStorage（IndexedDB 恢复失败），正在刷新页面")
            }
        }
    }

    fun delete(info: BackupInfo): Boolean = runCatching {
        if (info.uri != null) {
            DocumentFile.fromSingleUri(context, info.uri)?.delete() == true
        } else {
            info.file?.delete() == true
        }
    }.getOrDefault(false)

    fun readText(info: BackupInfo): String {
        if (info.uri != null) {
            return context.contentResolver.openInputStream(info.uri)?.bufferedReader()?.use { it.readText() }
                ?: error("无法读取备份文件")
        }
        return info.file?.readText(Charsets.UTF_8) ?: error("备份文件不存在")
    }

    /** evaluateJavascript 返回的是被 JSON 编码过的字符串，这里解出原始字符串 */
    private fun decodeJsString(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
    }

    companion object {
        /** 全局数据备份文件名的固定前缀，与站点备份区分 */
        const val GLOBAL_PREFIX = "global@"

        private const val EXPORT_LOCAL_STORAGE_JS =
            "(function(){var o={};try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);" +
                "o[k]=localStorage.getItem(k);}}catch(e){}return JSON.stringify(o);})()"

        private const val RESTORE_LOCAL_STORAGE_JS =
            "(function(d){try{var o=JSON.parse(d);localStorage.clear();" +
                "for(var k in o){if(o[k]===null||o[k]===undefined)continue;localStorage.setItem(k,o[k]);}" +
                "return 'ok';}catch(e){return 'err:'+e.message;}})('__DATA__')"

        /** 导出全部 IndexedDB（库名、版本、对象仓库、索引、记录） */
        private val EXPORT_INDEXED_DB_JS = """
            (function(){
              function report(list){ try{ QingGuangBackup.onIndexedDb(JSON.stringify(list)); }catch(e){} }
              try{
                function readStore(db,storeName){
                  return new Promise(function(resolve){
                    var info={name:storeName,keyPath:null,autoIncrement:false,indexes:[],records:[]};
                    try{
                      var tx=db.transaction(storeName,'readonly');
                      var store=tx.objectStore(storeName);
                      info.keyPath=store.keyPath||null;
                      info.autoIncrement=!!store.autoIncrement;
                      for(var i=0;i<store.indexNames.length;i++){
                        var ix=store.index(store.indexNames[i]);
                        info.indexes.push({name:ix.name,keyPath:ix.keyPath||null,unique:!!ix.unique,multiEntry:!!ix.multiEntry});
                      }
                      var req=store.getAll();
                      req.onsuccess=function(){info.records=req.result||[];resolve(info);};
                      req.onerror=function(){resolve(info);};
                    }catch(e){resolve(null);}
                  });
                }
                function readDB(item){
                  return new Promise(function(resolve){
                    var req;
                    try{req=indexedDB.open(item.name);}catch(e){resolve(null);return;}
                    req.onerror=function(){resolve(null);};
                    req.onsuccess=function(){
                      var db=req.result;
                      var names=Array.prototype.slice.call(db.objectStoreNames);
                      var out={name:db.name,version:db.version,stores:[]};
                      if(!names.length){db.close();resolve(out);return;}
                      Promise.all(names.map(function(s){return readStore(db,s);})).then(function(list){
                        out.stores=list.filter(Boolean);
                        db.close();
                        resolve(out);
                      }).catch(function(){db.close();resolve(out);});
                    };
                  });
                }
                var dbs=(indexedDB.databases?indexedDB.databases():Promise.resolve([]));
                dbs.then(function(list){return Promise.all((list||[]).map(readDB));})
                  .then(function(all){report((all||[]).filter(Boolean));})
                  .catch(function(){report([]);});
              }catch(e){report([]);}
            })();
        """.trimIndent()

        /** 恢复 IndexedDB：重建库结构与索引后写回记录 */
        private val RESTORE_INDEXED_DB_JS = """
            (function(raw){
              var list=[]; try{list=JSON.parse(raw)||[];}catch(e){list=[];}
              function finish(){try{QingGuangBackup.onIndexedDbRestored();}catch(e){}}
              if(!list.length){finish();return;}
              var dbIndex=0;
              function nextDb(){
                if(dbIndex>=list.length){finish();return;}
                var info=list[dbIndex++];
                try{
                  var del=indexedDB.deleteDatabase(info.name);
                  del.onsuccess=del.onerror=del.onblocked=function(){openDb(info);};
                }catch(e){nextDb();}
              }
              function openDb(info){
                var req;
                try{req=indexedDB.open(info.name, info.version||1);}catch(e){nextDb();return;}
                req.onupgradeneeded=function(){
                  var db=req.result;
                  (info.stores||[]).forEach(function(s){
                    try{
                      if(db.objectStoreNames.contains(s.name)) return;
                      var opts={};
                      if(s.keyPath!==null&&s.keyPath!==undefined) opts.keyPath=s.keyPath;
                      if(s.autoIncrement) opts.autoIncrement=true;
                      var store=db.createObjectStore(s.name,opts);
                      (s.indexes||[]).forEach(function(ix){
                        try{
                          if(!store.indexNames.contains(ix.name)){
                            store.createIndex(ix.name, ix.keyPath, {unique:!!ix.unique, multiEntry:!!ix.multiEntry});
                          }
                        }catch(e){}
                      });
                    }catch(e){}
                  });
                };
                req.onerror=function(){nextDb();};
                req.onsuccess=function(){
                  var db=req.result;
                  var stores=info.stores||[];
                  var si=0;
                  function nextStore(){
                    if(si>=stores.length){db.close();nextDb();return;}
                    var s=stores[si++];
                    if(!db.objectStoreNames.contains(s.name)){nextStore();return;}
                    try{
                      var tx=db.transaction(s.name,'readwrite');
                      var st=tx.objectStore(s.name);
                      (s.records||[]).forEach(function(r){try{st.put(r);}catch(e){}});
                      tx.oncomplete=tx.onerror=tx.onabort=function(){nextStore();};
                    }catch(e){nextStore();}
                  }
                  nextStore();
                };
              }
              nextDb();
            })('__DATA__')
        """.trimIndent()

        fun formatTime(time: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))

        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
        }
    }
}
