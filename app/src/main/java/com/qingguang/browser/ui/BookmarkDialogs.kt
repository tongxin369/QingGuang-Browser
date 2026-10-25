package com.qingguang.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingguang.browser.App
import com.qingguang.browser.data.Bookmark
import com.qingguang.browser.data.BookmarkFolder
import com.qingguang.browser.web.BrowserController
import kotlinx.coroutines.launch

/** 添加书签：可选择已有文件夹，或当场新建文件夹 */
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
    var newFolderMode by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

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
                Spacer(Modifier.height(14.dp))
                Text("保存到文件夹", fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = folderId == 0L && !newFolderMode,
                            onClick = {
                                folderId = 0L
                                newFolderMode = false
                            },
                            label = { Text("未分类") }
                        )
                    }
                    items(folders, key = { it.id }) { folder ->
                        FolderChip(folder = folder, folderId = folderId, newFolderMode = newFolderMode) {
                            folderId = folder.id
                            newFolderMode = false
                        }
                    }
                    item {
                        FilterChip(
                            selected = newFolderMode,
                            onClick = { newFolderMode = true },
                            label = { Text("新建文件夹") },
                            leadingIcon = {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.height(16.dp))
                            }
                        )
                    }
                }
                if (newFolderMode) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("新文件夹名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val targetFolder = if (newFolderMode && newFolderName.isNotBlank()) {
                            app.database.bookmarkFolderDao()
                                .insert(BookmarkFolder(name = newFolderName.trim()))
                        } else {
                            folderId
                        }
                        app.database.bookmarkDao().insert(
                            Bookmark(
                                title = title.trim().ifBlank { "未命名书签" },
                                url = url.trim(),
                                folderId = targetFolder
                            )
                        )
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
}

@Composable
private fun FolderChip(
    folder: BookmarkFolder,
    folderId: Long,
    newFolderMode: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = folderId == folder.id && !newFolderMode,
        onClick = onClick,
        label = { Text(folder.name, fontSize = 13.sp) }
    )
}
