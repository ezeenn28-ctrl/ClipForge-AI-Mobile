package com.clipforge.mobile

import java.io.File

/** Named local drafts. Metadata only: source media stays in its original location. */
object ProjectCatalog {
    data class Entry(val id: String, val name: String, val source: String, val startMs: Long,
                     val durationMs: Long, val updatedMs: Long)
    data class Listing(val entries: List<Entry> = emptyList(), val unreadable: Int = 0)
    private val idPattern = Regex("[a-f0-9]{32}")
    private val pathPattern = Regex("projects/catalog/[a-f0-9]{32}\\.properties")
    fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "")
    fun isCatalogPath(path: String): Boolean = pathPattern.matches(path)
    private fun file(root: File, id: String): File {
        require(idPattern.matches(id)) { "Invalid project identity" }
        return File(root, "projects/catalog/$id.properties")
    }
    /** Caller serializes autosave, switching, and backup with MetadataGate. */
    fun save(root: File, project: EditorProject) {
        require(project.projectId.isNotEmpty()) { "Project needs an identity before saving" }
        // Preserve the named draft before publishing it as the last-open project.
        ProjectStore.write(file(root, project.projectId), project)
        ProjectStore.write(File(root, "projects/last-project.properties"), project)
    }
    fun read(root: File, id: String): EditorProject {
        val project = ProjectStore.read(file(root, id)) ?: error("Saved project is missing")
        require(project.projectId == id) { "Saved project identity does not match its file" }
        return project.reviewedAgain()
    }
    fun list(root: File): Listing {
        var unreadable = 0
        val entries = File(root, "projects/catalog").listFiles().orEmpty()
            .filter { it.isFile && isCatalogPath("projects/catalog/" + it.name) }
            .mapNotNull { file ->
                try {
                    val project = read(root, file.nameWithoutExtension)
                    Entry(project.projectId, project.projectName.ifBlank { "Untitled project" }, project.source, project.startMs,
                        if (project.timelineMode && project.timelineClips.isNotEmpty()) TimelineModel.duration(project.timelineClips)
                        else project.endMs - project.startMs, file.lastModified())
                } catch (_: Exception) { unreadable++; null }
            }.sortedWith(compareByDescending<Entry> { it.updatedMs }.thenBy { it.id })
        return Listing(entries, unreadable)
    }
    fun backupPaths(root: File): Set<String> = File(root, "projects/catalog").listFiles().orEmpty()
        .filter { it.isFile && isCatalogPath("projects/catalog/" + it.name) }
        .map { "projects/catalog/" + it.name }.toSet()
}
