package com.qingguang.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingguang.browser.App
import com.qingguang.browser.data.Bookmark
import com.qingguang.browser.data.BookmarkFolder
import com.qingguang.browser.data.HomeItem
import com.qingguang.browser.web.BrowserController
import kotlinx.coroutines.launch

/** 添加书签：支持选择多层文件夹、新建子文件夹，并可同时添加到首页 */
@Composable
fun AddBookmarkDialog(
    controller: BrowserController,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val folders by app.database.bookmarkFolderDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var title by remember { mutableStateOf(controller.currentTitle.ifBlank { "未命名书签" }) }
    var url by remember { mutableStateOf(controller.currentUrl) }
    var folderId by remember { mutableStateOf(0L) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var newFolderMode by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var addToHome by remember { mutableStateOf(false) }

    val currentPath = folderChain(folders, folderId)
        .joinToString(" / ") { it.name }
        .ifBlank { "根目录" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加书签") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("网址") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showFolderPicker = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("保存到", fontSize = 13.sp)
                    Spacer(Modifier.height(0.dp))
                    Text(
                        currentPath,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            newFolderMode = !newFolderMode
                            if (!newFolderMode) newFolderName = ""
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = newFolderMode, onCheckedChange = null)
                    Text("在当前位置新建子文件夹", fontSize = 13.sp)
                }
                if (newFolderMode) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("新文件夹名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { addToHome = !addToHome }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = addToHome, onCheckedChange = null)
                    Text("同时添加到首页快捷入口", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val dao = app.database.bookmarkFolderDao()
                        val targetFolder = if (newFolderMode && newFolderName.isNotBlank()) {
                            dao.insert(
                                BookmarkFolder(
                                    name = newFolderName.trim(),
                                    parentId = folderId
                                )
                            )
                        } else {
                            folderId
                        }
                        val finalTitle = title.trim().ifBlank { "未命名书签" }
                        app.database.bookmarkDao().insert(
                            Bookmark(
                                title = finalTitle,
                                url = url.trim(),
                                folderId = targetFolder
                            )
                        )
                        if (addToHome) {
                            val homeDao = app.database.homeItemDao()
                            if (homeDao.countOnHome(url.trim()) == 0) {
                                val page = if (homeDao.countRootOnPage(0) < HOME_PAGE_CAPACITY) 0 else 1
                                homeDao.insert(
                                    HomeItem(title = finalTitle, url = url.trim(), page = page)
                                )
                            }
                        }
                        onDismiss()
                    }
                },
                enabled = url.isNotBlank() && (!newFolderMode || newFolderName.isNotBlank())
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showFolderPicker) {
        val flat = flattenFolders(folders)
        AlertDialog(
            onDismissRequest = { showFolderPicker = false },
            title = { Text("选择文件夹") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    item {
                        FolderPickRow("根目录", folderId == 0L) {
                            folderId = 0L
                            showFolderPicker = false
                        }
                    }
                    items(flat, key = { it.first.id }) { (folder, depth) ->
                        FolderPickRow(
                            "    ".repeat(depth) + folder.name,
                            folderId == folder.id
                        ) {
                            folderId = folder.id
                            showFolderPicker = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolderPicker = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FolderPickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp)
    )
}
