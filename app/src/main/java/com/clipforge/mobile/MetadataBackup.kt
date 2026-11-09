package com.clipforge.mobile

import java.io.*
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.sync.Mutex

object MetadataGate { val lock = Mutex() }

/** Curated metadata backup. No videos, model weights, signing keys or permissions. */
object MetadataBackup {
    private val allowed = setOf("projects/last-project.properties", "campaigns/submissions.properties", "speech/last-job.properties")
    private const val LIMIT = 50L * 1024 * 1024
    data class Staged(val directory: File, val paths: Set<String>, val hasProject: Boolean, val submissions: Int, val hasSpeech: Boolean, val projectCount: Int = 0) {
        fun discard() { directory.deleteRecursively() }
    }
    fun create(root: File, destination: OutputStream) {
        ZipOutputStream(destination.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.properties")); zip.write("format=clipforge-metadata-v1\n".toByteArray()); zip.closeEntry()
            var total = 0L
            for (path in allowed + ProjectCatalog.backupPaths(root)) {
                val file = File(root, path)
                if (!file.isFile) continue
                total += file.length(); require(total <= LIMIT) { "Backup metadata exceeds 50 MB" }
                zip.putNextEntry(ZipEntry(path)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }
    fun stage(root: File, input: InputStream): Staged {
        val directory = File(root, "backup-staged-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            val seen = mutableSetOf<String>()
            var total = 0L
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && (entry.name in allowed || ProjectCatalog.isCatalogPath(entry.name) || entry.name == "manifest.properties") && seen.add(entry.name)) { "Unexpected or duplicate backup entry" }
                    val destination = File(directory, entry.name).apply { parentFile?.mkdirs() }
                    destination.outputStream().use { out ->
                        val buffer = ByteArray(65_536)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count; require(total <= LIMIT) { "Backup exceeds 50 MB" }
                            out.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
            require("manifest.properties" in seen) { "Not a ClipForge backup" }
            val manifest = Properties().apply { File(directory, "manifest.properties").reader().use { load(it) } }
            require(manifest.getProperty("format") == "clipforge-metadata-v1") { "Unknown backup version" }
            val project = ProjectStore.read(File(directory, "projects/last-project.properties"))
            for (path in seen.filter { ProjectCatalog.isCatalogPath(it) }) {
                val item = ProjectCatalog.read(directory, File(path).nameWithoutExtension)
                ProjectStore.write(File(directory, path), item)
            }
            val campaigns = CampaignEntryStore.read(File(directory, "campaigns/submissions.properties"))
            val speech = SpeechJobStore.read(File(directory, "speech/last-job.properties"))
            // Re-encode validated records, stripping any approval state and pausing active jobs.
            project?.let { ProjectStore.write(File(directory, "projects/last-project.properties"), it.reviewedAgain()) }
            speech?.let { SpeechJobStore.write(File(directory, "speech/last-job.properties"), it.recovered()) }
            return Staged(directory, seen.filter { it in allowed || ProjectCatalog.isCatalogPath(it) }.toSet(), project != null, campaigns.size, speech != null, seen.count { ProjectCatalog.isCatalogPath(it) })
        } catch (error: Throwable) { directory.deleteRecursively(); throw error }
    }
    fun restore(root: File, staged: Staged) {
        val rollback = File(staged.directory, "rollback").apply { mkdirs() }
        val replaced = mutableListOf<String>()
        try {
            for (path in staged.paths) {
                val target = File(root, path).apply { parentFile?.mkdirs() }
                if (target.exists()) target.copyTo(File(rollback, path).apply { parentFile?.mkdirs() }, overwrite = true)
                val temporary = File(target.parentFile, target.name + ".restore-partial")
                File(staged.directory, path).copyTo(temporary, overwrite = true)
                replaced.add(path)
                check(temporary.renameTo(target)) { "Unable to restore $path" }
            }
        } catch (error: Throwable) {
            for (path in replaced.asReversed()) {
                val target = File(root, path)
                val previous = File(rollback, path)
                if (previous.exists()) previous.copyTo(target, overwrite = true) else target.delete()
                File(target.parentFile, target.name + ".restore-partial").delete()
            }
            throw error
        } finally { staged.discard() }
    }
}

