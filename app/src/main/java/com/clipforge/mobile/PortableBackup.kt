package com.clipforge.mobile

import android.content.Context
import android.net.Uri
import java.io.*
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import java.util.zip.*

/** Bounded optional media archive. Restore remaps only source fields, never permissions or approval. */
object PortableBackup {
    private const val LIMIT = 1024L * 1024 * 1024
    private fun sourceKey(key: String) = key == "source" || key.endsWith(".source")
    private fun props(file: File) = Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }
    private fun copy(input: InputStream, output: OutputStream, remaining: Long, digest: MessageDigest? = null): Long {
        var total=0L; val buffer=ByteArray(65536)
        while(true) {
            if(Thread.currentThread().isInterrupted) throw InterruptedIOException("Backup interrupted")
            val count=input.read(buffer); if(count<0)break
            total+=count; require(total<=remaining) { "Portable backup exceeds 1 GB" }
            digest?.update(buffer,0,count); output.write(buffer,0,count)
        }; return total
    }
    fun create(context: Context, destination: OutputStream) {
        val root=context.filesDir
        val sources=MetadataBackup.paths(root).flatMap { path ->
            val p=props(File(root,path)); p.stringPropertyNames().filter(::sourceKey).map { p.getProperty(it) }
        }.filter { it.startsWith("content://") || it.startsWith("file://") }.filterNot { it in listOf("file:///preset-template","file:///music-template") }.distinct()
        require(sources.size<=2000) { "Too many media files" }
        ZipOutputStream(destination.buffered()).use { zip ->
            var used=0L; val manifest=Properties().apply { setProperty("format","clipforge-portable-v1");setProperty("count",sources.size.toString()) }
            val metadata=ByteArrayOutputStream().also { MetadataBackup.create(root,it) }.toByteArray()
            zip.putNextEntry(ZipEntry("settings.zip"));zip.write(metadata);zip.closeEntry();used+=metadata.size
            sources.forEachIndexed { index, source ->
                val name="media/$index.bin";val digest=MessageDigest.getInstance("SHA-256")
                zip.putNextEntry(ZipEntry(name))
                context.contentResolver.openInputStream(Uri.parse(source))?.use { used+=copy(it,zip,LIMIT-used,digest) } ?: error("Cannot read media. Relink the missing source before backing up.")
                zip.closeEntry();manifest.setProperty("$index.source",source);manifest.setProperty("$index.sha256",digest.digest().joinToString("") { "%02x".format(it) })
            }
            zip.putNextEntry(ZipEntry("portable.properties"));manifest.store(zip,"ClipForge portable media backup");zip.closeEntry()
        }
    }
    fun stage(context: Context, input: InputStream): MetadataBackup.Staged {
        val temp=File(context.cacheDir,"backup-${UUID.randomUUID()}.zip")
        val work=File(context.cacheDir,"backup-media-${UUID.randomUUID()}").apply { check(mkdirs()) }
        var staged: MetadataBackup.Staged?=null
        try {
            input.use { source -> temp.outputStream().use { copy(source,it,LIMIT) } }
            ZipFile(temp).use { zip ->
                val manifestEntry=zip.getEntry("portable.properties") ?: return temp.inputStream().use { MetadataBackup.stage(context.filesDir,it) }
                require(manifestEntry.size in 1..2_000_000) { "Invalid portable manifest" }
                val manifest=Properties().apply { zip.getInputStream(manifestEntry).reader().use { load(it) } }
                require(manifest.getProperty("format")=="clipforge-portable-v1")
                val count=manifest.getProperty("count").toInt();require(count in 0..2000)
                val expected=(0 until count).map { "media/$it.bin" }.toSet()+setOf("settings.zip","portable.properties")
                val entries=zip.entries().asSequence().toList()
                require(entries.size==expected.size && entries.map { it.name }.toSet()==expected && entries.none { it.isDirectory }) { "Unexpected portable entries" }
                var used=0L
                for(entry in entries) {
                    val target=File(work,entry.name).apply { parentFile.mkdirs() }
                    zip.getInputStream(entry).use { source -> target.outputStream().use { used+=copy(source,it,minOf(LIMIT-used,if(entry.name=="settings.zip")50L*1024*1024 else LIMIT)) } }
                }
                val result=File(work,"settings.zip").inputStream().use { MetadataBackup.stage(context.filesDir,it) };staged=result
                val mapping=mutableMapOf<String,String>();val added=mutableSetOf<String>()
                repeat(count) { index ->
                    val source=manifest.getProperty("$index.source") ?: error("Missing media source")
                    require((source.startsWith("file://")||source.startsWith("content://")) && source.length<=10000 && source !in mapping) { "Invalid media source" }
                    val file=File(work,"media/$index.bin");val digest=MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { copy(it,object : OutputStream() { override fun write(value: Int) {} override fun write(buffer: ByteArray, offset: Int, length: Int) {} },LIMIT,digest) }
                    require(digest.digest().joinToString("") { "%02x".format(it) }==manifest.getProperty("$index.sha256")) { "Media checksum mismatch" }
                    MediaAssets.inspect(context,Uri.fromFile(file),false)
                    val path="portable-media/${UUID.randomUUID().toString().replace("-","")}.bin"
                    file.copyTo(File(result.directory,path).apply { parentFile.mkdirs() })
                    mapping[source]=Uri.fromFile(File(context.filesDir,path)).toString();added+=path
                }
                for(path in result.paths) {
                    val file=File(result.directory,path);val p=props(file)
                    p.stringPropertyNames().filter(::sourceKey).forEach { key -> mapping[p.getProperty(key)]?.let { p.setProperty(key,it) } }
                    file.writer(Charsets.UTF_8).use { p.store(it,"Restored media references; review required") }
                }
                return result.copy(paths=result.paths+added,mediaCount=count)
            }
        } catch(error:Throwable) { staged?.discard();throw error }
        finally { temp.delete();work.deleteRecursively() }
    }
}
