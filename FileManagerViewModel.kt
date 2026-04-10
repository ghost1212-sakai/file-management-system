package com.example.file_manager

// =============================================================================
// UML REFERENCE: CLASS DIAGRAM
// This ViewModel wraps the "FileManager" class from the Class Diagram:
//   FileManager
//     - clipboard         → clipboardFiles + isCut
//     + copy()            → copy()
//     + paste()           → paste()
//     + compress()        → zipFiles()
//     + decompress()      → unzipFile()
//     + share()           → exposed to UI via events
//
// UML REFERENCE: COMPONENT DIAGRAM
// FileManagerViewModel is the implementation of the "File Manager" component,
// sitting between the "User Interface" component and the sub-components:
//   File Operations, Compression Module, Sharing Module, and Storage System.
//
// UML REFERENCE: SEQUENCE DIAGRAM
// This class is the "FileManager" lifeline in the Sequence Diagram.
// Every public method here corresponds to a message arrow from User to FileManager:
//   create/delete/rename → perform operation → FileSystem
//   copy/paste           → read/write        → FileSystem
//   compress/decompress  → process data      → FileSystem
//   share                → send file         → FileSystem
//
// UML REFERENCE: COLLABORATION DIAGRAM
// This is the central "File Manager" node that receives message #2 "request action"
// from User Interface and dispatches messages #3–#11 to Storage System,
// Compression Module, and Sharing Service.
// =============================================================================

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.collections.emptyList

// =============================================================================
// UML REFERENCE: CLASS DIAGRAM
// FileManagerViewModel is the runtime representation of the "FileManager" class.
// It holds a reference to FileManagerRepository (the FileSystem layer), matching
// the association arrow: FileManager → Folder → File in the Class Diagram.
//
// UML REFERENCE: STATE DIAGRAM
// The ViewModel manages the data that drives state transitions:
//   - _selectedFiles empty  → Idle or Selecting state
//   - _zipProgress non-null → Compressing or Decompressing state
//   - _hasClipboard true    → clipboard ready for Paste
// =============================================================================
class FileManagerViewModel : ViewModel() {

    // COMPONENT DIAGRAM: FileManagerRepository implements the "Storage System" +
    // "File Operations" components. All I/O is delegated here.
    private val repository = FileManagerRepository()

    val rootPath = Environment.getExternalStorageDirectory().absolutePath

    // SEQUENCE DIAGRAM: currentPath drives which directory the FileSystem reads from.
    private val _currentPath = MutableStateFlow(rootPath)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    // CLASS DIAGRAM: Represents the collection of File/Folder objects currently
    // visible — corresponds to the Folder.addFile() / removeFile() operations
    // maintaining the folder's children list.
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    // STATE DIAGRAM: Non-empty set means the system is in "Selecting" state.
    // Empty set means the system transitions back to "Idle".
    private val _selectedFiles = MutableStateFlow<Set<FileItem>>(emptySet())
    val selectedFiles: StateFlow<Set<FileItem>> = _selectedFiles.asStateFlow()

    // STATE DIAGRAM: "select file/folder" → Selecting state transition
    fun toggleSelection(fileItem: FileItem) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.contains(fileItem)) current.remove(fileItem) else current.add(fileItem)
        _selectedFiles.value = current
    }

    // STATE DIAGRAM: "cancel" transition → returns to Idle state
    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    // CLASS DIAGRAM: FileManager.-clipboard attribute
    // Stores the files staged for copy or cut (clipboard).
    private var clipboardFiles: List<File> = emptyList()
    private var isCut: Boolean = false

    // ACTIVITY DIAGRAM: Controls visibility of the Paste action in the toolbar.
    // Only shown after a Copy or Cut operation (clipboard is non-empty).
    private val _hasClipboard = MutableStateFlow(false)
    val hasClipboard: StateFlow<Boolean> = _hasClipboard.asStateFlow()

    // ER DIAGRAM: Sort operates on File entity attributes: NAME, DATE (lastModified), SIZE
    private val _sortBy = MutableStateFlow(SortBy.NAME)
    val sortBy: StateFlow<SortBy> = _sortBy.asStateFlow()

    // USE CASE DIAGRAM: "Search" use case — filter string applied to File.name
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        // STATE DIAGRAM: Initial state is "Idle" — load the root directory listing.
        loadFiles()
    }

    // USE CASE DIAGRAM: "Search" use case — filters the file list by name
    // ER DIAGRAM: Searches on the File entity's "name" attribute
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        loadFiles()
    }

    // ER DIAGRAM: Re-sorts File entities by their attributes (name, lastModified, size)
    fun setSort(sort: SortBy) {
        _sortBy.value = sort
        loadFiles()
    }

    // ==========================================================================
    // UML REFERENCE: SEQUENCE DIAGRAM
    // loadFiles() is called whenever the FileManager needs to query the FileSystem.
    // It maps to the "perform operation" message from FileManager → FileSystem.
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Corresponds to the path: File Manager → Storage System (read directory listing)
    //
    // UML REFERENCE: CLASS DIAGRAM
    // Calls repository.getFiles() which returns a list of FileItem objects,
    // representing the File and Folder class instances in the current directory.
    // ==========================================================================
    fun loadFiles(path: String = _currentPath.value) {
        _currentPath.value = path
        val raw = repository.getFiles(path, _sortBy.value)
        // USE CASE DIAGRAM: Search filter applied here if "Search" use case is active
        _files.value = if (_searchQuery.value.isNotEmpty()) {
            raw.filter { it.name.contains(_searchQuery.value, ignoreCase = true) }
        } else raw
    }

    // ACTIVITY DIAGRAM: Navigating up is implicitly the "back" path in the
    // Activity Diagram's flow — returns to parent directory listing.
    // ER DIAGRAM: Traverses the Folder hierarchy upward (parent Folder entity).
    fun navigateUp() {
        if (_currentPath.value != rootPath) {
            val parent = File(_currentPath.value).parent
            if (parent != null) loadFiles(parent)
        }
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements FileManager.+copy() — stores source files in the clipboard.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Copy branch: "Select file" → "Copy to clipboard" → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #6 "copy/paste" path — copy phase stores files in clipboard.
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // User → FileManager: copy/paste → FileSystem: read/write
    // ==========================================================================
    fun copy(files: List<FileItem>) {
        clipboardFiles = files.map { it.file }
        isCut = false
        _hasClipboard.value = clipboardFiles.isNotEmpty()
        clearSelection()
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements the cut variant of FileManager.+copy() — marks clipboard as cut.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Cut is modeled as Copy + Delete after paste (same Copy branch in diagram).
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #6 "copy/paste" — cut phase; source is deleted after paste().
    // ==========================================================================
    fun cut(files: List<FileItem>) {
        clipboardFiles = files.map { it.file }
        isCut = true
        _hasClipboard.value = clipboardFiles.isNotEmpty()
        clearSelection()
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements FileManager.+paste() — writes clipboard contents to current directory.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Paste branch: "Paste file" action → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #6 "copy/paste" completes here → Storage System reads/writes data.
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // FileManager → FileSystem: read/write (for both copy and move operations)
    // ==========================================================================
    fun paste() {
        val dest = File(_currentPath.value)
        clipboardFiles.forEach { source ->
            if (isCut) {
                // COLLABORATION DIAGRAM: Move = copy to destination + delete from source
                repository.moveFile(source, dest)
            } else {
                repository.copyFile(source, dest)
            }
        }
        if (isCut) {
            clipboardFiles = emptyList()
            _hasClipboard.value = false
        }
        loadFiles()
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements File.+delete() — removes files/folders from the filesystem.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Delete branch: "Delete file/folder" action → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #4 "delete file" → Storage System
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // User → FileManager: create/delete/rename → FileSystem: perform operation
    //
    // UML REFERENCE: ER DIAGRAM
    // Removes File or Folder entities and their relationships from the system.
    // ==========================================================================
    fun delete(files: List<FileItem>) {
        files.forEach { repository.deleteFile(it.file) }
        clearSelection()
        loadFiles()
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements File.+rename() — changes the name attribute of a File or Folder.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Rename branch: "Enter new name" → operation complete → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #5 "rename file" → Storage System
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // User → FileManager: create/delete/rename → FileSystem: perform operation
    // ==========================================================================
    fun rename(fileItem: FileItem, newName: String) {
        if (repository.renameFile(fileItem.file, newName)) {
            loadFiles()
        }
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements Folder creation — matches Folder class with +addFile() method
    // (a new Folder is added to the parent directory listing).
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Create Folder branch: "Enter folder name" → "Folder created" → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #3 "create file" (folder variant) → Storage System
    //
    // UML REFERENCE: USE CASE DIAGRAM
    // "Create Folder" use case — triggered from the FAB menu
    //
    // UML REFERENCE: ER DIAGRAM
    // Creates a new Folder entity in the storage model.
    // ==========================================================================
    fun createFolder(name: String) {
        if (repository.createFolder(_currentPath.value, name)) {
            loadFiles()
        }
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements File.+create() — creates a new File in the current Folder.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Create File branch: "Enter file name" → "File created" → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #3 "create file" → Storage System
    //
    // UML REFERENCE: USE CASE DIAGRAM
    // "Create File" / "Create Text File" use case — triggered from the FAB menu
    //
    // UML REFERENCE: ER DIAGRAM
    // Creates a new File entity, linked to the current Folder via "Contains" relationship.
    // ==========================================================================
    fun createFile(name: String) {
        if (repository.createFile(_currentPath.value, name)) {
            loadFiles()
        }
    }

    // STATE DIAGRAM: _zipProgress drives the Compressing / Decompressing sub-states.
    // Non-null = in progress; null = returned to Idle after completion.
    private val _zipProgress = MutableStateFlow<Float?>(null)
    val zipProgress: StateFlow<Float?> = _zipProgress.asStateFlow()

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements FileManager.+compress() — delegates to repository ZIP logic.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Compress branch: "Compress file" action → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #7 "compress" → Compression Module
    // Message #8 "store compressed file" → Storage System
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // User → FileManager: compress/decompress → FileSystem: process data
    //
    // UML REFERENCE: STATE DIAGRAM
    // Triggers: Processing → Compressing state
    // On completion (progress >= 100): Compressing → Idle
    // On error (progress < 0):         Compressing → Idle
    // ==========================================================================
    fun zipFiles(files: List<FileItem>, zipName: String) {
        val zipFile = File(_currentPath.value, "$zipName.zip")
        val sources = files.map { it.file }
        viewModelScope.launch {
            repository.zipFiles(sources, zipFile).collect { progress ->
                _zipProgress.value = progress
                if (progress >= 100f || progress < 0f) {
                    // STATE DIAGRAM: Compressing → Idle transition
                    _zipProgress.value = null
                    loadFiles()
                }
            }
        }
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements FileManager.+decompress() — delegates to repository unzip logic.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Decompress branch: "Extract file" action → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #9 "decompress" → Compression Module
    // Message #10 "restore file" → Storage System
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // User → FileManager: compress/decompress → FileSystem: process data
    //
    // UML REFERENCE: STATE DIAGRAM
    // Triggers: Processing → Decompressing state
    // On completion: Decompressing → Idle
    // ==========================================================================
    fun unzipFile(fileItem: FileItem) {
        val dest = File(_currentPath.value, fileItem.name.removeSuffix(".zip"))
        viewModelScope.launch {
            repository.unzipFile(fileItem.file, dest).collect { progress ->
                _zipProgress.value = progress
                if (progress >= 100f || progress < 0f) {
                    // STATE DIAGRAM: Decompressing → Idle transition
                    _zipProgress.value = null
                    loadFiles()
                }
            }
        }
    }
}
