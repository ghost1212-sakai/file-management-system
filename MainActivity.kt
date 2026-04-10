package com.example.file_manager

// =============================================================================
// UML REFERENCE: COMPONENT DIAGRAM
// This file represents the "User Interface" component — the top-level component
// in the Component Diagram that receives user actions and passes them to the
// File Manager component.
//
// UML REFERENCE: USE CASE DIAGRAM
// All use cases (Create File, Create Folder, Copy, Paste, Cut, Rename, Delete,
// Compress, Decompress, Share, Search) are initiated from this UI layer.
// The single actor "User" in the Use Case Diagram interacts exclusively through
// this screen.
// =============================================================================

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.file_manager.FileManagerViewModel
import androidx.core.net.toUri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.core.content.FileProvider
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility

// =============================================================================
// UML REFERENCE: CLASS DIAGRAM
// MainActivity acts as the entry point that instantiates the "User" class
// concept from the Class Diagram. It holds a reference to FileManagerViewModel
// which wraps the "FileManager" class (with operations: copy, paste, compress,
// decompress, share).
//
// UML REFERENCE: SEQUENCE DIAGRAM
// The lifecycle of this Activity represents the start of the sequence:
//   User → FileManager → FileSystem
// =============================================================================
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<FileManagerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure you ask for All Files access runtime permission on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = "package:$packageName".toUri()
                startActivity(intent)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF1E1E1E),
                surface = Color(0xFF1E1E1E)
            )) {
                FileManagerScreen(viewModel)
            }
        }
    }
}

// =============================================================================
// UML REFERENCE: COLLABORATION DIAGRAM
// FileManagerScreen is the "User Interface" object (node 1: "select operation").
// All user interactions here trigger message #2 "request action" to FileManager
// (the ViewModel). The collaboration flow is:
//   User → (1: select operation) → User Interface → (2: request action) → File Manager
//
// UML REFERENCE: STATE DIAGRAM
// The screen manages transitions between these states from the State Diagram:
//   Idle → Selecting (on long press / tap icon)
//   Selecting → Processing (on choosing an operation)
//   Processing → Compressing / Sharing / Decompressing (depending on action)
//   Any state → Idle (on cancel or operation completion)
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(viewModel: FileManagerViewModel) {
    val files by viewModel.files.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val hasClipboard by viewModel.hasClipboard.collectAsState()
    
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<FileItem?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    var isFabExpanded by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val zipProgress by viewModel.zipProgress.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()

    // STATE DIAGRAM: When selectedFiles is non-empty, the UI is in "Selecting" state.
    val isSelectionMode = selectedFiles.isNotEmpty()

    var showRenameDialog by remember { mutableStateOf(false) }

    // USE CASE DIAGRAM: "Search" use case — toggled by isUserSearching flag.
    var isUserSearching by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    // STATE DIAGRAM: Back press cancels navigation (transition back toward Idle).
    BackHandler(enabled = currentPath != viewModel.rootPath) {
        viewModel.navigateUp()
    }
    
    // USE CASE DIAGRAM: "Create Folder" use case dialog.
    if (showCreateFolderDialog) {
        CreateItemDialog(
            title = "New folder",
            label = "New folder name:",
            initialText = "New Folder",
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                // SEQUENCE DIAGRAM: User → FileManager: create
                // COLLABORATION DIAGRAM: Message #3 "create file" to Storage System via File Manager
                viewModel.createFolder(name)
                showCreateFolderDialog = false
                isFabExpanded = false
            }
        )
    }

    // USE CASE DIAGRAM: "Create File" / "Create Text File" use case dialog.
    if (showCreateFileDialog) {
        CreateItemDialog(
            title = "New file",
            label = "New file name:",
            initialText = "text.txt",
            onDismiss = { showCreateFileDialog = false },
            onConfirm = { name ->
                // SEQUENCE DIAGRAM: User → FileManager: create
                // COLLABORATION DIAGRAM: Message #3 "create file" to Storage System via File Manager
                viewModel.createFile(name)
                showCreateFileDialog = false
                isFabExpanded = false
            }
        )
    }

    if (showInfoDialog && selectedItem != null) {
        // CLASS DIAGRAM: Displays File attributes: name, size, lastModified (mapped from File class)
        InformationDialog(
            fileItem = selectedItem!!,
            onDismiss = { showInfoDialog = false }
        )
    }

    // USE CASE DIAGRAM: "Rename" use case dialog.
    if (showRenameDialog && selectedItem != null) {
        CreateItemDialog(
            title = "Rename",
            label = "New name:",
            initialText = selectedItem!!.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                // ACTIVITY DIAGRAM: Rename branch — "Enter new name" → operation complete
                // SEQUENCE DIAGRAM: User → FileManager: rename → FileSystem: perform operation
                // COLLABORATION DIAGRAM: Message #5 "rename file" to Storage System
                viewModel.rename(selectedItem!!, newName)
                showRenameDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isUserSearching) {
                        // USE CASE DIAGRAM: "Search" use case — active search input field
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search files...", color = Color.Gray) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (isSelectionMode) {
                        // STATE DIAGRAM: "Selecting" state — top bar shows selection count
                        Text("${selectedFiles.size} selected", color = Color.White)
                    } else {
                        Column {
                            Text("${files.size} items", fontSize = 16.sp)
                            Text(currentPath.takeLast(25), fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    if (isUserSearching) {
                        IconButton(onClick = { 
                            isUserSearching = false
                            viewModel.setSearchQuery("")
                        }) { Icon(Icons.Default.ArrowBack, contentDescription = "Close Search") }
                    } else if (isSelectionMode) {
                        // STATE DIAGRAM: "cancel" transition — Selecting → Idle
                        IconButton(onClick = { viewModel.clearSelection() }) { Icon(Icons.Default.Close, contentDescription = "Close") }
                    } else {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        // STATE DIAGRAM: "choose operation" transition — Selecting → Processing

                        // USE CASE DIAGRAM: "Compress" use case — triggered from selection mode toolbar
                        // ACTIVITY DIAGRAM: Compress branch — "Compress file" action
                        // COLLABORATION DIAGRAM: Message #7 "compress" to Compression Module
                        IconButton(onClick = { viewModel.zipFiles(selectedFiles.toList(), selectedFiles.firstOrNull()?.name ?: "Archive") }) { Icon(Icons.Default.Archive, "Zip") }

                        // USE CASE DIAGRAM: "Copy" use case
                        // ACTIVITY DIAGRAM: Copy branch — "Select file" → "Copy to clipboard"
                        // COLLABORATION DIAGRAM: Message #6 "copy/paste" via File Manager
                        IconButton(onClick = { viewModel.copy(selectedFiles.toList()) }) { Icon(Icons.Default.ContentCopy, "Copy") }

                        // USE CASE DIAGRAM: "Copy" (Cut variant) use case
                        // SEQUENCE DIAGRAM: User → FileManager: copy/paste → FileSystem: read/write
                        IconButton(onClick = { viewModel.cut(selectedFiles.toList()) }) { Icon(Icons.Default.ContentCut, "Cut") }

                        // USE CASE DIAGRAM: "Delete" use case
                        // ACTIVITY DIAGRAM: Delete branch — "Delete file/folder" action
                        // COLLABORATION DIAGRAM: Message #4 "delete file" to Storage System
                        // SEQUENCE DIAGRAM: User → FileManager: create/delete/rename → FileSystem: perform operation
                        IconButton(onClick = { viewModel.delete(selectedFiles.toList()) }) { Icon(Icons.Default.Delete, "Delete") }
                    } else {
                        // USE CASE DIAGRAM: "Paste" use case — only visible when clipboard has content
                        // ACTIVITY DIAGRAM: Paste branch — "Paste file" action
                        if (hasClipboard) {
                            IconButton(onClick = { viewModel.paste() }) { Icon(Icons.Default.ContentPaste, "Paste") }
                        }
                        if (!isUserSearching) {
                            // USE CASE DIAGRAM: "Search" use case — enters search mode
                            IconButton(onClick = { isUserSearching = true }) { Icon(Icons.Default.Search, "Search") }
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                            androidx.compose.material3.DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(Color(0xFF2E2E2E))
                            ) {
                                // ER DIAGRAM: Sorting operates on the File entity attributes:
                                // name, lastModified (date), and size — all attributes of the File entity
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Sort by Name", color = Color.White) },
                                    onClick = { viewModel.setSort(com.example.file_manager.SortBy.NAME); showSortMenu = false }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Sort by Date Modified", color = Color.White) },
                                    onClick = { viewModel.setSort(com.example.file_manager.SortBy.DATE); showSortMenu = false }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Sort by Size", color = Color.White) },
                                    onClick = { viewModel.setSort(com.example.file_manager.SortBy.SIZE); showSortMenu = false }
                                )
                                androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Exit App", color = Color.Red) },
                                    onClick = { showSortMenu = false; (context as? android.app.Activity)?.finish() }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2E2E2E),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            // USE CASE DIAGRAM: FAB exposes Create File, Create Folder use cases
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(visible = isFabExpanded) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        // USE CASE DIAGRAM: "Create Folder" use case entry point
                        // ACTIVITY DIAGRAM: CreateFolder branch — "Enter folder name" → "Folder created"
                        SmallFloatingActionButton(
                            onClick = { showCreateFolderDialog = true },
                            containerColor = Color(0xFF757575),
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SmallFloatingActionButton(
                            onClick = { /* Handle 2nd option if needed */ },
                            containerColor = Color(0xFF757575),
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // USE CASE DIAGRAM: "Create File" / "Create Text File" use case entry point
                        // ACTIVITY DIAGRAM: CreateFile branch — "Enter file name" → "File created"
                        SmallFloatingActionButton(
                            onClick = { showCreateFileDialog = true },
                            containerColor = Color(0xFF757575),
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "New File")
                        }
                    }
                }
                
                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = Color(0xFF757575),
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = if (isFabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Toggle Actions"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // CLASS DIAGRAM: Renders the list of FileItem objects (mapped from the File and Folder classes)
            // ER DIAGRAM: Each item represents either a File or Folder entity from the ER model.
            //             Folders "contain" Files (1-to-N "Contains" relationship in ER Diagram).
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
            ) {
                item {
                    FileListItem(
                        name = "..",
                        isDirectory = true,
                        info = "",
                        date = "",
                        isUpNavigation = true,
                        onClick = { viewModel.navigateUp() },
                        onLongClick = {}
                    )
                }

                items(files) { fileItem ->
                    FileListItem(
                        name = fileItem.name,
                        isDirectory = fileItem.isDirectory,
                        isSelected = selectedFiles.contains(fileItem),
                        info = if (fileItem.isDirectory) {
                            if (fileItem.itemCount == 0) "Empty" else "${fileItem.itemCount} items"
                        } else {
                            "${fileItem.file.length() / 1024} KB"
                        },
                        date = fileItem.dateModified,
                        onClick = {
                            if (isSelectionMode) {
                                // STATE DIAGRAM: Additional tap in "Selecting" state toggles item selection
                                viewModel.toggleSelection(fileItem)
                            } else {
                                if (fileItem.isDirectory) {
                                    // STATE DIAGRAM: Navigating into folder stays in Idle state
                                    // ER DIAGRAM: Traversing the Folder → Contains → File relationship
                                    viewModel.loadFiles(fileItem.file.absolutePath)
                                } else {
                                    // SEQUENCE DIAGRAM: File open triggers FileSystem read
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            fileItem.file
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "*/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onIconClick = {
                            // STATE DIAGRAM: Tapping icon triggers "select file/folder" → Selecting state
                            viewModel.toggleSelection(fileItem)
                        },
                        onLongClick = {
                            if (isSelectionMode) {
                                viewModel.toggleSelection(fileItem)
                            } else {
                                // STATE DIAGRAM: Long press → "select file/folder" → Selecting state
                                // COLLABORATION DIAGRAM: Triggers the bottom sheet (User Interface node)
                                selectedItem = fileItem
                                showBottomSheet = true
                            }
                        }
                    )
                }
            }

            // =================================================================
            // UML REFERENCE: ACTIVITY DIAGRAM
            // The BottomSheet is the "Operation?" decision node — the user picks
            // which branch to follow: Copy, Cut, Delete, Rename, Compress,
            // Decompress, Share, or get Information.
            //
            // UML REFERENCE: USE CASE DIAGRAM
            // All use cases (Copy, Paste, Delete, Rename, Compress, Decompress,
            // Share) are accessible from this bottom sheet for a single item.
            //
            // UML REFERENCE: COLLABORATION DIAGRAM
            // Selecting an action here triggers the corresponding numbered
            // message to File Manager (node: File Manager → Storage System,
            // Compression Module, or Sharing Service).
            // =================================================================
            if (showBottomSheet && selectedItem != null) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = sheetState,
                    containerColor = Color(0xFF2E2E2E)
                ) {
                    Column(modifier = Modifier.padding(bottom = 32.dp)) {
                        Text(
                            text = selectedItem!!.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(16.dp)
                        )
                        HorizontalDivider(color = Color.DarkGray)

                        // CLASS DIAGRAM: Displays File attributes (name, size, lastModified)
                        BottomSheetItem(icon = Icons.Default.Info, text = "Information") { 
                            showInfoDialog = true
                            showBottomSheet = false 
                        }

                        // STATE DIAGRAM: "select file/folder" → Selecting state
                        BottomSheetItem(icon = Icons.Default.CheckCircle, text = "Select") { 
                            viewModel.toggleSelection(selectedItem!!)
                            showBottomSheet = false 
                        }

                        // USE CASE DIAGRAM: "Compress" use case
                        // ACTIVITY DIAGRAM: Compress branch — triggers "Compress file" action
                        // COLLABORATION DIAGRAM: Message #7 "compress" → Compression Module
                        //                        Message #8 "store compressed file" → Storage System
                        // STATE DIAGRAM: Processing → Compressing state
                        // SEQUENCE DIAGRAM: User → FileManager: compress/decompress → FileSystem: process data
                        BottomSheetItem(icon = Icons.Default.Archive, text = "Compress") { 
                            viewModel.zipFiles(listOf(selectedItem!!), selectedItem!!.name)
                            showBottomSheet = false 
                        }

                        // USE CASE DIAGRAM: "Decompress" use case (only for .zip files)
                        // ACTIVITY DIAGRAM: Decompress branch — "Extract file" action
                        // COLLABORATION DIAGRAM: Message #9 "decompress" → Compression Module
                        //                        Message #10 "restore file" → Storage System
                        // STATE DIAGRAM: Processing → Decompressing state
                        if (!selectedItem!!.isDirectory && selectedItem!!.name.endsWith(".zip")) {
                            BottomSheetItem(icon = Icons.Default.Unarchive, text = "Extract") { 
                                viewModel.unzipFile(selectedItem!!)
                                showBottomSheet = false 
                            }
                        }

                        // USE CASE DIAGRAM: "Copy" use case
                        // ACTIVITY DIAGRAM: Copy branch — "Select file" → "Copy to clipboard"
                        // COLLABORATION DIAGRAM: Message #6 "copy/paste" via File Manager
                        // SEQUENCE DIAGRAM: User → FileManager: copy/paste → FileSystem: read/write
                        BottomSheetItem(icon = Icons.Default.ContentCopy, text = "Copy") {
                            viewModel.copy(listOf(selectedItem!!))
                            showBottomSheet = false
                        }

                        // USE CASE DIAGRAM: "Copy" (Cut variant) use case
                        BottomSheetItem(icon = Icons.Default.ContentCut, text = "Cut") {
                            viewModel.cut(listOf(selectedItem!!))
                            showBottomSheet = false
                        }

                        // USE CASE DIAGRAM: "Delete" use case
                        // ACTIVITY DIAGRAM: Delete branch — "Delete file/folder" action
                        // COLLABORATION DIAGRAM: Message #4 "delete file" → Storage System
                        // SEQUENCE DIAGRAM: User → FileManager: create/delete/rename → FileSystem: perform operation
                        BottomSheetItem(icon = Icons.Default.Delete, text = "Delete") {
                            viewModel.delete(listOf(selectedItem!!))
                            showBottomSheet = false
                        }

                        // USE CASE DIAGRAM: "Rename" use case
                        // ACTIVITY DIAGRAM: Rename branch — "Enter new name" action
                        // COLLABORATION DIAGRAM: Message #5 "rename file" → Storage System
                        // SEQUENCE DIAGRAM: User → FileManager: create/delete/rename → FileSystem: perform operation
                        BottomSheetItem(icon = Icons.Default.Edit, text = "Rename") { 
                            showRenameDialog = true
                            showBottomSheet = false 
                        }

                        // USE CASE DIAGRAM: "Share" use case
                        // ACTIVITY DIAGRAM: Share branch — "Select sharing method" action
                        // COLLABORATION DIAGRAM: Message #11 "share file" → Sharing Service
                        // SEQUENCE DIAGRAM: User → FileManager: share → FileSystem: send file
                        // STATE DIAGRAM: Processing → Sharing state
                        BottomSheetItem(icon = Icons.Default.Share, text = "Share") {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", selectedItem!!.file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share file"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot share file", Toast.LENGTH_SHORT).show()
                            }
                            showBottomSheet = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomSheetItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = text, tint = Color.White)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, color = Color.White, fontSize = 16.sp)
    }
}

// =============================================================================
// UML REFERENCE: CLASS DIAGRAM
// FileListItem renders a single "File" or "Folder" object from the Class Diagram.
// - Folder class attributes rendered: name, itemCount
// - File class attributes rendered: name, size (length), lastModified (dateModified)
//
// UML REFERENCE: ER DIAGRAM
// Each row represents either a File or Folder entity.
// The isDirectory flag distinguishes the two — matching the Folder → Contains → File
// hierarchy in the ER Diagram.
// =============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    name: String,
    isDirectory: Boolean,
    isSelected: Boolean = false,
    info: String,
    date: String,
    isUpNavigation: Boolean = false,
    onClick: () -> Unit,
    onIconClick: () -> Unit = {},
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFF383838) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(45.dp)
                .clickable { onIconClick() }
                .background(if (isDirectory) Color(0xFFFFA000) else if (!isDirectory && name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".jpeg") }) Color(0xFF4CAF50) else Color.Gray, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isUpNavigation) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFF7CB342))
            } else if (isDirectory) {
                // CLASS DIAGRAM: Represents a "Folder" object (Folder class)
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
            } else if (!isDirectory && name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".jpeg") }) {
                // CLASS DIAGRAM: Represents a "File" object (File class) — image type
                Icon(Icons.Default.Image, contentDescription = null, tint = Color.White)
            } else {
                // CLASS DIAGRAM: Represents a "File" object (File class) — generic type
                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            // CLASS DIAGRAM: File.name / Folder.name attribute
            Text(text = name, color = Color.White, fontSize = 16.sp)
            if (info.isNotEmpty()) {
                // CLASS DIAGRAM: File.size (for files) or Folder item count (for folders)
                Text(text = info, color = Color.Gray, fontSize = 12.sp)
            }
        }

        if (date.isNotEmpty()) {
            // CLASS DIAGRAM: File.lastModified attribute (shown as formatted date string)
            Text(text = date, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun CreateItemDialog(
    title: String,
    label: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                Text(text = label, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.Gray
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("OK", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = Color(0xFF2E2E2E)
    )
}

// =============================================================================
// UML REFERENCE: CLASS DIAGRAM
// InformationDialog exposes all key attributes of the "File" class:
//   - name       → displayed in title
//   - size       → sizeFormatted
//   - lastModified → lastMod (formatted date)
// Also exposes Folder.name and its item count.
//
// UML REFERENCE: ER DIAGRAM
// The dialog distinguishes File vs Folder entities and shows type-specific
// attributes, matching the File and Folder entity definitions in the ER Diagram.
// =============================================================================
@Composable
fun InformationDialog(
    fileItem: FileItem,
    onDismiss: () -> Unit
) {
    var hasNoMedia by remember { mutableStateOf(java.io.File(fileItem.file, ".nomedia").exists()) }
    var sizeBytes by remember { mutableStateOf(0L) }
    var fileCount by remember { mutableStateOf(fileItem.itemCount) }
    
    LaunchedEffect(fileItem) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (fileItem.isDirectory) {
                var sz = 0L
                var count = 0
                fileItem.file.walkTopDown().forEach { 
                    if (it.isFile) { sz += it.length(); count++ }
                }
                sizeBytes = sz
                fileCount = count
            } else {
                sizeBytes = fileItem.file.length()
            }
        }
    }

    val r = if (fileItem.file.canRead()) "r" else "-"
    val w = if (fileItem.file.canWrite()) "w" else "-"
    val x = if (fileItem.file.canExecute()) "x" else "-"
    val perms = "$r$w$x $r$w$x ---"

    // CLASS DIAGRAM: File.size attribute — formatted for display
    val sizeFormatted = if (sizeBytes > 1024 * 1024) "${sizeBytes / (1024 * 1024)} MB"
                        else if (sizeBytes > 1024) "${sizeBytes / 1024} KB"
                        else "$sizeBytes B"

    // CLASS DIAGRAM: File.lastModified attribute — formatted for display
    val dateFormat = java.text.SimpleDateFormat("dd/MM/yy HH:mm:ss", java.util.Locale.getDefault())
    val lastMod = java.util.Date(fileItem.file.lastModified()).let { dateFormat.format(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text("File information", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                // CLASS DIAGRAM: File.name attribute
                Text(fileItem.name, color = Color.White, fontSize = 16.sp)
            }
        },
        text = {
            Column {
                Text("General information", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                // ER DIAGRAM: Type distinguishes between File and Folder entities
                Row { Text("Type:", color = Color.Gray, modifier = Modifier.width(130.dp)); Text(if (fileItem.isDirectory) "Folder" else "File", color = Color.White) }
                if (fileItem.isDirectory) {
                    // CLASS DIAGRAM: Folder.itemCount (number of children)
                    Row { Text("File count:", color = Color.Gray, modifier = Modifier.width(130.dp)); Text("$fileCount", color = Color.White) }
                }
                // CLASS DIAGRAM: File.size attribute
                Row { Text("Size:", color = Color.Gray, modifier = Modifier.width(130.dp)); Text("$sizeFormatted ($sizeBytes B)", color = Color.White) }
                // CLASS DIAGRAM: File.lastModified attribute
                Row { Text("Last modification:", color = Color.Gray, modifier = Modifier.width(130.dp)); Text(lastMod, color = Color.White) }
                Row { Text("Permissions:", color = Color.Gray, modifier = Modifier.width(130.dp)); Text(perms, color = Color.White) }
                Row { Text("FS path:", color = Color.Gray, modifier = Modifier.width(130.dp)); Text(fileItem.file.absolutePath, color = Color.White) }
                Row { Text("FS type:", color = Color.Gray, modifier = Modifier.width(130.dp)); Text("fuse", color = Color.White) }

                if (fileItem.isDirectory) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Checkbox(
                            checked = hasNoMedia,
                            onCheckedChange = { checked ->
                                hasNoMedia = checked
                                val nomedia = java.io.File(fileItem.file, ".nomedia")
                                if (checked) nomedia.createNewFile() else nomedia.delete()
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color.Black, uncheckedColor = Color.Gray)
                        )
                        Text("Disable media scanning", color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Copy", color = Color.White) }
        },
        containerColor = Color(0xFF2E2E2E)
    )
}
