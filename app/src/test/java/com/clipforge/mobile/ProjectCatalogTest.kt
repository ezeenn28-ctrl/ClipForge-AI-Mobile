package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files

class ProjectCatalogTest {
    private fun project(id: String, name: String) = EditorProject(
        "content://videos/1", 60_000, 0, 20_000, projectId = id, projectName = name,
        drafts = listOf(HighlightDraft(0, 20_000, approved = true)))

    @Test fun separateDraftsFromTheSameSourceKeepTheirEditsAndNeverRestoreApproval() {
        val root = Files.createTempDirectory("catalog").toFile()
        try {
            val first = ProjectCatalog.newId(); val second = ProjectCatalog.newId()
            ProjectCatalog.save(root, project(first, "First cut").copy(hook = "Original"))
            ProjectCatalog.save(root, project(second, "Second cut").copy(hook = "Another version"))
            val entries = ProjectCatalog.list(root).entries
            assertEquals(setOf(first, second), entries.map { it.id }.toSet())
            assertEquals("Original", ProjectCatalog.read(root, first).hook)
            assertEquals("Another version", ProjectCatalog.read(root, second).hook)
            assertFalse(ProjectCatalog.read(root, first).drafts.single().approved)
            assertEquals(second, ProjectStore.read(File(root, "projects/last-project.properties"))!!.projectId)
            ProjectCatalog.save(root, ProjectCatalog.read(root, first).copy(projectName = "Renamed"))
            assertEquals(2, ProjectCatalog.list(root).entries.size)
            assertEquals("Renamed", ProjectCatalog.read(root, first).projectName)
        } finally { root.deleteRecursively() }
    }

    @Test fun corruptedDraftIsReportedAndKeptWhileOtherDraftsRemainUsable() {
        val root = Files.createTempDirectory("catalog-corrupt").toFile()
        try {
            val id = ProjectCatalog.newId()
            ProjectCatalog.save(root, project(id, "Good"))
            val corrupt = File(root, "projects/catalog/" + ProjectCatalog.newId() + ".properties").apply { writeText("broken") }
            val listing = ProjectCatalog.list(root)
            assertEquals(1, listing.entries.size); assertEquals(1, listing.unreadable)
            assertEquals("broken", corrupt.readText())
            assertFalse(ProjectCatalog.isCatalogPath("projects/catalog/../../other.properties"))
            try { ProjectCatalog.read(root, "../last-project"); fail("Invalid path accepted") }
            catch (_: IllegalArgumentException) {}
        } finally { root.deleteRecursively() }
    }

    @Test fun metadataBackupRestoresAllNamedDraftsWithoutMediaOrApproval() {
        val root = Files.createTempDirectory("catalog-backup").toFile()
        val destination = Files.createTempDirectory("catalog-restore").toFile()
        try {
            val first = ProjectCatalog.newId(); val second = ProjectCatalog.newId()
            ProjectCatalog.save(root, project(first, "Kumusta 👋"))
            ProjectCatalog.save(root, project(second, "Second").copy(captionPreset = "HIGHLIGHT"))
            val bytes = ByteArrayOutputStream().also { MetadataBackup.create(root, it) }.toByteArray()
            val staged = MetadataBackup.stage(destination, ByteArrayInputStream(bytes))
            assertEquals(2, staged.projectCount)
            MetadataBackup.restore(destination, staged)
            assertEquals(2, ProjectCatalog.list(destination).entries.size)
            assertEquals("Kumusta 👋", ProjectCatalog.read(destination, first).projectName)
            assertEquals("HIGHLIGHT", ProjectCatalog.read(destination, second).captionPreset)
            assertFalse(ProjectCatalog.read(destination, second).drafts.single().approved)
        } finally { root.deleteRecursively(); destination.deleteRecursively() }
    }

    @Test fun oldProjectFilesStillLoadWithoutCatalogFields() {
        val root = Files.createTempDirectory("legacy-project").toFile()
        try {
            val file = File(root, "legacy.properties")
            ProjectStore.write(file, project(ProjectCatalog.newId(), "Legacy"))
            file.writeText(file.readText().lineSequence().filterNot { it.startsWith("project.") }.joinToString("\n"))
            val saved = ProjectStore.read(file)!!
            assertEquals("", saved.projectId); assertEquals("", saved.projectName)
            ProjectCatalog.save(root, saved.copy(projectId = ProjectCatalog.newId(), projectName = "Migrated"))
            assertEquals("Migrated", ProjectCatalog.list(root).entries.single().name)
        } finally { root.deleteRecursively() }
    }
}
