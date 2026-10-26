package com.qingguang.browser.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingguang.browser.App
import com.qingguang.browser.util.LocalSiteManager
import com.qingguang.browser.web.BrowserController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 可在编辑器中打开的文本文件后缀 */
private val TEXT_EXTENSIONS = setOf(
    "html", "htm", "css", "js", "mjs", "json", "xml", "svg", "txt", "md", "csv", "ts", "vue"
)

private data class LocalTarget(val siteId: String, val root: File, val file: File)

/** 解析本地站点 URL；在线网页返回 null */
private fun resolveLocalTarget(context: Context, url: String): LocalTarget? {
    val prefix = LocalSiteManager.ASSET_HOST + "/files/sites/"
    if (!url.startsWith(prefix)) return null
    val rest = url.removePrefix(prefix).substringBefore('?').substringBefore('#')
    val segments = rest.split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null
    val siteId = Uri.decode(segments[0])
    val root = LocalSiteManager(context).siteDir(siteId)
    if (!root.isDirectory) return null
    val relative = segments.drop(1).joinToString("/") { Uri.decode(it) }
    var file = if (relative.isBlank()) root else File(root, relative)
    if (file.isDirectory) file = File(file, "index.html")
    if (!file.isFile) {
        file = root.walkTopDown()
            .firstOrNull { it.isFile && it.extension.lowercase() in TEXT_EXTENSIONS }
            ?: root
    }
    return LocalTarget(siteId, root, file)
}

private fun textFiles(root: File): List<File> = root.walkTopDown()
    .filter { it.isFile && it.extension.lowercase() in TEXT_EXTENSIONS }
    .sortedBy { it.relativeTo(root).path }
    .toList()

private fun relativePath(root: File, file: File): String =
    file.relativeTo(root).path.replace(File.separatorChar, '/')

/** 网页源码查看 / 本地网页代码编辑：修改会直接写入本地站点文件并重载页面 */
@Composable
fun SourceEditorOverlay(controller: BrowserController, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val url = controller.currentUrl
    val target = remember(url) { resolveLocalTarget(context, url) }

    var files by remember(url) { mutableStateOf(target?.let { textFiles(it.root) } ?: emptyList()) }
    var current by remember(url) { mutableStateOf(target?.file) }
    var text by remember(url) { mutableStateOf("") }
    var original by remember(url) { mutableStateOf("") }
    var remote by remember(url) { mutableStateOf("") }
    var loading by remember(url) { mutableStateOf(true) }
    var showList by remember(url) { mutableStateOf(target?.file?.isFile != true) }
    var showRename by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf("") }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun openFile(file: File) {
        scope.launch {
            loading = true
            text = withContext(Dispatchers.IO) {
                runCatching { file.readText() }.getOrDefault("")
            }
            original = text
            current = file
            showList = false
            loading = false
        }
    }

    fun save() {
        val file = current ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    file.writeText(text)
                    true
                }.getOrDefault(false)
            }
            if (ok) {
                original = text
                toast("已保存：${file.name}")
                controller.reload()
            } else {
                toast("保存失败")
            }
        }
    }

    fun rename() {
        val file = current ?: return
        val root = target?.root ?: return
        val name = renameName.trim()
        if (name.isBlank() || name.contains('/') || name.contains('\\')) {
            toast("文件名不合法")
            return
        }
        val dest = File(file.parentFile, name)
        if (dest.exists()) {
            toast("同名文件已存在")
            return
        }
        if (!file.renameTo(dest)) {
            toast("重命名失败")
            return
        }
        val siteId = target.siteId
        val oldRel = relativePath(root, file)
        val newRel = relativePath(root, dest)
        scope.launch {
            val dao = app.database.localPageDao()
            val page = dao.all().firstOrNull { it.siteId == siteId }
            if (page != null && page.entryUrl == LocalSiteManager.urlFor(siteId, oldRel)) {
                val newUrl = LocalSiteManager.urlFor(siteId, newRel)
                dao.upsert(page.copy(entryUrl = newUrl))
                controller.loadUrl(newUrl)
            } else {
                controller.reload()
            }
            files = textFiles(root)
            current = dest
            showRename = false
            toast("已重命名为：$name")
        }
    }

    LaunchedEffect(url) {
        if (target != null) {
            val file = target.file
            text = withContext(Dispatchers.IO) {
                runCatching { file.readText() }.getOrDefault("")
            }
            original = text
            loading = false
        } else {
            controller.fetchPageSource { source ->
                remote = source
                loading = false
            }
        }
    }

    BackHandler { onClose() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text("网页源码", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (target != null) {
                            current?.let { relativePath(target.root, it) } ?: "本地网页"
                        } else {
                            "在线网页（只读）"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (target != null) {
                    TextButton(onClick = { showList = !showList }) {
                        Text("文件", fontSize = 13.sp)
                    }
                    TextButton(
                        enabled = current != null,
                        onClick = {
                            renameName = current?.name ?: ""
                            showRename = true
                        }
                    ) {
                        Text("重命名", fontSize = 13.sp)
                    }
                    TextButton(
                        enabled = current != null && text != original,
                        onClick = { save() }
                    ) {
                        Text("保存", fontSize = 13.sp)
                    }
                }
            }
            HorizontalDivider()
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                target != null && showList -> LazyColumn(Modifier.fillMaxSize()) {
                    items(files, key = { it.absolutePath }) { file ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { openFile(file) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                relativePath(target.root, file),
                                fontSize = 13.sp,
                                color = if (file == current) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${file.length()} B",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (files.isEmpty()) {
                        item {
                            Text(
                                "该站点下没有可编辑的文本文件",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                else -> OutlinedTextField(
                    value = if (target != null) text else remote,
                    onValueChange = { if (target != null) text = it },
                    readOnly = target == null,
                    placeholder = {
                        Text(
                            if (target != null) "在此编辑代码" else "在线网页只能查看源码，本地网页才可编辑",
                            fontSize = 12.sp
                        )
                    },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("重命名文件") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    singleLine = true,
                    label = { Text("文件名") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rename()
                        showRename = false
                    },
                    enabled = renameName.isNotBlank()
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            }
        )
    }
}
