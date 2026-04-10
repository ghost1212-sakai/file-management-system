package com.example.file_manager

// =============================================================================
// UML REFERENCE: COMPONENT DIAGRAM
// This file implements two components from the Component Diagram:
//   1. "File Operations" component — create, delete, rename, copy, move
//   2. "Compression Module" component — zipFiles, unzipFile
// Both components interact with the "Storage System" component via the
// underlying Java File API and ZipOutputStream / ZipInputStream.
//
// UML REFERENCE: SEQUENCE DIAGRAM
// FileManagerRepository is the implementation of the "FileSystem" lifeline.
// All methods here correspond to operations on the right side of the Sequence:
//   FileSystem: perform operation  (create, delete, rename)
//   FileSystem: read/write         (copy, move)
//   FileSystem: process data       (compress, decompress)
//   FileSystem: send file          (share — exposed via URI to caller)
//
// UML REFERENCE: COLLABORATION DIAGRAM
// This class is the "Storage System" node plus parts of "Compression Module".
// It receives messages #3–#10 from File Manager:
//   #3  create file      → createFile() / createFolder()
//   #4  delete file      → deleteFile()
//   #5  rename file      → renameFile()
//   #6  copy/paste       → copyFile() / moveFile()
//   #7  compress         → zipFiles()
//   #8  store compressed → zipFiles() writes to destZip
//   #9  decompress       → unzipFile()
//   #10 restore file     → unzipFile() writes to destDir
// =============================================================================

import java.io.File
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.flowOn

// =============================================================================
// UML REFERENCE: CLASS DIAGRAM
// FileItem is the data transfer object that carries File and Folder class
// attributes across layers:
//   File class:   name, size (via file.length()), lastModified → dateModified
//   Folder class: name, itemCount
//
// UML REFERENCE: ER DIAGRAM
// FileItem represents either a File or Folder entity from the ER Diagram.
// - isDirectory = true  → Folder entity
// - isDirectory = false → File entity
// The "Contains" relationship (Folder 1 → N File) is navigated by listing
// children of a directory path.
// =============================================================================
data class FileItem(
    val file: File,
    val name: String,          // CLASS DIAGRAM: File.name / Folder.name attribute
    val isDirectory: Boolean,  // ER DIAGRAM: Distinguishes File vs Folder entity
    val itemCount: Int,        // CLASS DIAGRAM: Folder child count (Folder.addFile / removeFile affects this)
    val dateModified: String   // CLASS DIAGRAM: File.lastModified attribute (formatted)
)

// =============================================================================
// UML REFERENCE: COMPONENT DIAGRAM
// FileManagerRepository is the combined implementation of:
//   - "File Operations" component (CRUD on files/folders)
//   - "Compression Module" component (zip/unzip)
//   - "Storage System" component (the underlying filesystem I/O layer)
//
// UML REFERENCE: CLASS DIAGRAM
// Provides the concrete implementation for:
//   File.+create(), File.+delete(), File.+rename()
//   Folder.+addFile(), Folder.+removeFile()
//   FileManager.+copy(), +paste(), +compress(), +decompress()
// =============================================================================
class FileManagerRepository {
    private val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Returns a sorted list of File / Folder objects for the given directory path.
    // Folders are listed first (matching typical file manager convention), then
    // files — mirroring the Folder → File hierarchy in the Class Diagram.
    //
    // UML REFERENCE: ER DIAGRAM
    // Reads the "Contains" relationship: a Folder (1) contains N Files/subfolders.
    // Each result is a File or Folder entity with its key attributes populated.
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // Called when FileManager sends "perform operation" (directory listing) to FileSystem.
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Part of the Storage System node — responds to File Manager's read requests.
    // ==========================================================================
    fun getFiles(path: String, sortBy: SortBy = SortBy.NAME): List<FileItem> {
        val root = File(path)
        if (!root.exists() || !root.isDirectory) return emptyList()

        val files = root.listFiles()?.toList() ?: emptyList()
        val mappedFiles = files.map { file ->
            // CLASS DIAGRAM: Folder.itemCount — number of direct children
            val itemCount = if (file.isDirectory) file.list()?.size ?: 0 else 0
            // CLASS DIAGRAM: File.lastModified → formatted as dateStr
            val dateStr = dateFormat.format(Date(file.lastModified()))
            FileItem(file, file.name, file.isDirectory, itemCount, dateStr)
        }

        // ER DIAGRAM: Sorted by File entity attributes — NAME, DATE (lastModified), SIZE
        return when (sortBy) {
            SortBy.NAME -> mappedFiles.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            SortBy.DATE -> mappedFiles.sortedWith(compareBy({ !it.isDirectory }, { -it.file.lastModified() }))
            SortBy.SIZE -> mappedFiles.sortedWith(compareBy({ !it.isDirectory }, { -it.file.length() }))
        }
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements the copy behaviour of FileManager.+copy() / +paste().
    // Recursively copies source File/Folder into the destination Folder,
    // matching Folder.+addFile() semantics for the destination.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Copy branch: "Select file" → "Copy to clipboard" → Paste → "Paste file" → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #6 "copy/paste" — copy phase writes to Storage System.
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // FileManager → FileSystem: read/write (copy source to destination)
    // ==========================================================================
    fun copyFile(source: File, destination: File): Boolean {
        return try {
            source.copyRecursively(File(destination, source.name), overwrite = true)
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements move semantics: copy to destination + delete from source.
    // Corresponds to Folder.+removeFile() at source and +addFile() at destination.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Cut = Copy branch + Delete branch combined in a single atomic-like operation.
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #6 "copy/paste" (cut variant): copy to destination, then
    // Message #4 "delete file": remove original from source.
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // FileManager → FileSystem: read/write (move = copy + delete source)
    // ==========================================================================
    fun moveFile(source: File, destination: File): Boolean {
        return try {
            source.copyRecursively(File(destination, source.name), overwrite = true)
            source.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements File.+delete() — removes a file or folder recursively.
    // Matches Folder.+removeFile() for the parent folder.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Delete branch: "Delete file/folder" action → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #4 "delete file" → Storage System
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // FileManager → FileSystem: perform operation (delete)
    //
    // UML REFERENCE: ER DIAGRAM
    // Removes the File or Folder entity and all child entities
    // (cascading delete of the "Contains" relationship).
    // ==========================================================================
    fun deleteFile(file: File): Boolean {
        return try {
            file.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements File.+rename() — changes the File.name attribute on disk.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Rename branch: "Enter new name" → operation completes → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #5 "rename file" → Storage System
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // FileManager → FileSystem: perform operation (rename)
    //
    // UML REFERENCE: ER DIAGRAM
    // Modifies the "name" attribute of a File or Folder entity in place.
    // ==========================================================================
    fun renameFile(file: File, newName: String): Boolean {
        return try {
            val dest = File(file.parentFile, newName)
            if (file.renameTo(dest)) {
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Supports Folder.+addFile() by creating a new folder as a child of the parent.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Create Folder branch: "Enter folder name" → "Folder created" → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #3 "create file" (folder variant) → Storage System
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // FileManager → FileSystem: perform operation (create directory)
    //
    // UML REFERENCE: USE CASE DIAGRAM
    // "Create Folder" use case — the file system action executed here.
    //
    // UML REFERENCE: ER DIAGRAM
    // Creates a new Folder entity linked to the parent Folder via
    // the hierarchical "Contains" relationship.
    // ==========================================================================
    fun createFolder(parent: String, name: String): Boolean {
        return try {
            val file = File(parent, name)
            if (!file.exists()) file.mkdirs() else false
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements File.+create() — creates a new empty File on disk.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Create File branch: "Enter file name" → "File created" → end
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #3 "create file" → Storage System
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // FileManager → FileSystem: perform operation (create file)
    //
    // UML REFERENCE: USE CASE DIAGRAM
    // "Create File" / "Create Text File" use case — the file system action executed here.
    //
    // UML REFERENCE: ER DIAGRAM
    // Creates a new File entity and implicitly links it to the parent Folder
    // via the "Contains" (1 Folder → N Files) relationship.
    // ==========================================================================
    fun createFile(parent: String, name: String): Boolean {
        return try {
            val file = File(parent, name)
            if (!file.exists()) file.createNewFile() else false
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements FileManager.+compress() — creates a ZIP archive from source files.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Compress branch: "Compress file" action → end
    //
    // UML REFERENCE: COMPONENT DIAGRAM
    // This function is the core of the "Compression Module" component.
    // It reads from "File Operations" and writes compressed data to "Storage System".
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #7 "compress"           → Compression Module (this function)
    // Message #8 "store compressed file" → Storage System (writes destZip)
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // FileManager → FileSystem: compress/decompress → FileSystem: process data
    //
    // UML REFERENCE: STATE DIAGRAM
    // This async operation keeps the system in "Compressing" state while running.
    // Emitting progress >= 100f signals the Compressing → Idle transition.
    // Emitting -1f (error) also triggers the → Idle transition.
    // ==========================================================================
    suspend fun zipFiles(sourceFiles: List<File>, destZip: File): kotlinx.coroutines.flow.Flow<Float> = kotlinx.coroutines.flow.flow {
        try {
                // Calculate total bytes for progress reporting (STATE DIAGRAM: progress tracking during Compressing state)
                var totalBytes = 0L
                sourceFiles.forEach { file ->
                    file.walkTopDown().forEach { if (it.isFile) totalBytes += it.length() }
                }

                if (totalBytes == 0L) {
                    emit(100f) // STATE DIAGRAM: Immediate Compressing → Idle for empty input
                    return@flow
                }

                var processedBytes = 0L
                java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(destZip))).use { zos ->
                    sourceFiles.forEach { root ->
                        val rootPath = root.parentFile?.absolutePath ?: ""
                        root.walkTopDown().forEach { file ->
                            if (file.isFile) {
                                // COMPONENT DIAGRAM: Compression Module reads from File Operations layer
                                val zipFileName = file.absolutePath.removePrefix(rootPath).removePrefix(File.separator)
                                zos.putNextEntry(java.util.zip.ZipEntry(zipFileName))
                                java.io.BufferedInputStream(java.io.FileInputStream(file)).use { bis ->
                                    val buffer = ByteArray(1024 * 8)
                                    var count: Int
                                    while (bis.read(buffer).also { count = it } != -1) {
                                        zos.write(buffer, 0, count)
                                        processedBytes += count
                                        // STATE DIAGRAM: Emit progress while in Compressing state
                                        emit((processedBytes.toFloat() / totalBytes) * 100f)
                                    }
                                }
                                zos.closeEntry()
                            } else if (file.isDirectory) {
                                // ER DIAGRAM: Preserves the Folder hierarchy inside the ZIP archive
                                val zipFileName = file.absolutePath.removePrefix(rootPath).removePrefix(File.separator) + "/"
                                zos.putNextEntry(java.util.zip.ZipEntry(zipFileName))
                                zos.closeEntry()
                            }
                        }
                    }
                }
                // STATE DIAGRAM: Compressing → Idle transition (success)
                emit(100f)
            } catch (e: Exception) {
                // STATE DIAGRAM: Compressing → Idle transition (error)
                emit(-1f)
            }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    // ==========================================================================
    // UML REFERENCE: CLASS DIAGRAM
    // Implements FileManager.+decompress() — extracts a ZIP archive to a folder.
    //
    // UML REFERENCE: ACTIVITY DIAGRAM
    // Decompress branch: "Extract file" action → end
    //
    // UML REFERENCE: COMPONENT DIAGRAM
    // Core of the "Compression Module" component — extract path.
    // Restores files to the "Storage System" component (destDir).
    //
    // UML REFERENCE: COLLABORATION DIAGRAM
    // Message #9  "decompress"    → Compression Module (this function)
    // Message #10 "restore file"  → Storage System (writes extracted files to destDir)
    //
    // UML REFERENCE: SEQUENCE DIAGRAM
    // FileManager → FileSystem: compress/decompress → FileSystem: process data
    //
    // UML REFERENCE: STATE DIAGRAM
    // Keeps the system in "Decompressing" state while running.
    // Emitting 100f signals the Decompressing → Idle transition.
    // Emitting -1f (error) also triggers the → Idle transition.
    //
    // UML REFERENCE: ER DIAGRAM
    // Restores File and Folder entities (with their "Contains" hierarchy)
    // from the ZIP into the Storage System.
    // ==========================================================================
    suspend fun unzipFile(zipFile: File, destDir: File): kotlinx.coroutines.flow.Flow<Float> = kotlinx.coroutines.flow.flow {
        try {
                val totalBytes = zipFile.length()
                if (totalBytes == 0L) {
                    emit(100f) // STATE DIAGRAM: Immediate Decompressing → Idle for empty zip
                    return@flow
                }

                var processedBytes = 0L
                java.util.zip.ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(zipFile))).use { zis ->
                    var entry: java.util.zip.ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val newFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            // ER DIAGRAM: Recreates the Folder entity from the ZIP directory entry
                            newFile.mkdirs()
                        } else {
                            // ER DIAGRAM: Recreates the File entity from the ZIP file entry
                            newFile.parentFile?.mkdirs()
                            java.io.BufferedOutputStream(java.io.FileOutputStream(newFile)).use { bos ->
                                val buffer = ByteArray(1024 * 8)
                                var count: Int
                                while (zis.read(buffer).also { count = it } != -1) {
                                    bos.write(buffer, 0, count)
                                }
                            }
                        }
                        zis.closeEntry()
                        // Note: Unzipping progress based on zip size stream reading doesn't strictly match length
                        // Because read reads uncompressed bytes. But zip metadata is hard to read fast.
                        // We will just emit a vague progress based on entry counts, or just emit 100 at end.
                        // For a real progress we might need ZipFile class which is a bit more complex.
                        // We'll update fake progress or skip fine-grained for now.
                        entry = zis.nextEntry
                    }
                }
                // STATE DIAGRAM: Decompressing → Idle transition (success)
                emit(100f)
            } catch (e: Exception) {
                // STATE DIAGRAM: Decompressing → Idle transition (error)
                emit(-1f)
            }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)
}

// =============================================================================
// UML REFERENCE: ER DIAGRAM
// SortBy corresponds to sortable attributes of the File entity in the ER Diagram:
//   NAME → File.name attribute
//   DATE → File.lastModified attribute (date of last modification)
//   SIZE → File.size attribute
// =============================================================================
enum class SortBy { NAME, DATE, SIZE }
