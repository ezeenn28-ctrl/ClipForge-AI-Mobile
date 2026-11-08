package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CampaignTracker(directory: File, enabled: Boolean) {
    val file = remember(directory) { File(directory, "campaigns/submissions.properties") }
    var entries by remember { mutableStateOf<List<CampaignEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var campaign by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("TikTok") }
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(SubmissionStatus.PENDING) }
    var views by remember { mutableStateOf("0") }
    var cpm by remember { mutableStateOf("1.00") }
    var minimum by remember { mutableStateOf("3.00") }
    var maximum by remember { mutableStateOf("150.00") }
    var paid by remember { mutableStateOf("0.00") }
    var tierMinimum by remember { mutableStateOf("0") }
    var tierActual by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val editable = enabled && !loading && !saving && !loadFailed

    LaunchedEffect(file) {
        try { entries = withContext(Dispatchers.IO) { CampaignEntryStore.read(file) } }
        catch (error: Exception) { loadFailed = true; message = "Could not read tracker. Existing data was preserved: ${error.message}" }
        finally { loading = false }
    }

    if (loading) {
        Text("Loading local submissions…")
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        return
    }
    if (loadFailed) {
        Text(message, color = MaterialTheme.colorScheme.error)
        return
    }
    Text("Submission and payout tracker", style = MaterialTheme.typography.titleMedium)
    Text("Manual USD bookkeeping only. Enter platform-reported values. Estimates are not guaranteed payouts; account standing and campaign rules still need checking.", style = MaterialTheme.typography.bodySmall)
    Text("${entries.count { it.status == SubmissionStatus.APPROVED }} approved • ${entries.count { it.status == SubmissionStatus.PENDING }} pending • ${entries.count { it.status == SubmissionStatus.REJECTED }} rejected")
    val estimateTotal = entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.qualifiedEstimate() }
    val paidTotal = entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.paid }
    Text("Approved estimates meeting entered limits: USD ${estimateTotal.toPlainString()}")
    Text("Actual paid (entered by you): USD ${paidTotal.toPlainString()}")

    OutlinedTextField(enabled = editable, value = campaign, onValueChange = { campaign = it.take(200) }, label = { Text("Campaign name") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("TikTok", "Instagram", "YouTube", "X").forEach { option ->
            FilterChip(enabled = editable, selected = platform == option, onClick = { platform = option }, label = { Text(option) })
        }
    }
    OutlinedTextField(enabled = editable, value = url, onValueChange = { url = it.take(2000) }, label = { Text("HTTPS post URL (optional)") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SubmissionStatus.entries.forEach { option ->
            FilterChip(enabled = editable, selected = status == option, onClick = { status = option }, label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) })
        }
    }
    OutlinedTextField(enabled = editable, value = views, onValueChange = { views = it.filter(Char::isDigit).take(13) }, label = { Text("Platform-reported views") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(enabled = editable, value = cpm, onValueChange = { cpm = moneyInput(it) }, label = { Text("USD per 1,000 views") }, modifier = Modifier.weight(1f))
        OutlinedTextField(enabled = editable, value = paid, onValueChange = { paid = moneyInput(it) }, label = { Text("Actual USD paid") }, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(enabled = editable, value = minimum, onValueChange = { minimum = moneyInput(it) }, label = { Text("Minimum payout USD") }, modifier = Modifier.weight(1f))
        OutlinedTextField(enabled = editable, value = maximum, onValueChange = { maximum = moneyInput(it) }, label = { Text("Maximum payout USD") }, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(enabled = editable, value = tierMinimum, onValueChange = { tierMinimum = it.filter(Char::isDigit).take(3) }, label = { Text("Required Tier 1 % (0 = none)") }, modifier = Modifier.weight(1f))
        OutlinedTextField(enabled = editable, value = tierActual, onValueChange = { tierActual = it.filter(Char::isDigit).take(3) }, label = { Text("Actual Tier 1 % (blank = unknown)") }, modifier = Modifier.weight(1f))
    }
    OutlinedTextField(enabled = editable, value = reason, onValueChange = { reason = it.take(3000) }, label = { Text("Rejection reason / notes") }, modifier = Modifier.fillMaxWidth())
    Button(enabled = editable && campaign.isNotBlank(), onClick = {
        try {
            val entry = CampaignEntry(editingId ?: UUID.randomUUID().toString(), campaign.trim(), platform, url.trim(), status,
                views.toLong(), BigDecimal(cpm), BigDecimal(minimum), BigDecimal(maximum), BigDecimal(paid),
                tierMinimum.toInt(), tierActual.takeIf { it.isNotBlank() }?.toInt(), reason)
            entry.validate()
            val updated = if (editingId == null) entries + entry else entries.map { if (it.id == entry.id) entry else it }
            saving = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { CampaignEntryStore.write(file, updated) }
                    entries = updated
                    editingId = entry.id
                    message = "Submission saved locally"
                } catch (error: Exception) { message = "Save failed: ${error.message}" }
                finally { saving = false }
            }
        } catch (error: Exception) { message = "Check the submission fields: ${error.message}" }
    }) { Text(if (saving) "Saving…" else if (editingId == null) "Save submission" else "Update submission") }
    OutlinedButton(enabled = editable, onClick = {
        editingId = null; campaign = ""; url = ""; status = SubmissionStatus.PENDING
        views = "0"; paid = "0.00"; tierActual = ""; reason = ""; message = "New submission"
    }) { Text("New submission") }
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    val pages = ((entries.size + 9) / 10).coerceAtLeast(1)
    val currentPage = page.coerceIn(0, pages - 1)
    if (entries.isNotEmpty()) {
        Row {
            OutlinedButton(enabled = editable && currentPage > 0, onClick = { page = currentPage - 1 }) { Text("Previous") }
            Text("Page ${currentPage + 1}/$pages")
            OutlinedButton(enabled = editable && currentPage + 1 < pages, onClick = { page = currentPage + 1 }) { Text("Next") }
        }
        entries.asReversed().drop(currentPage * 10).take(10).forEach { entry ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${entry.campaign} • ${entry.platform} • ${entry.status.name.lowercase()}")
                    Text("${entry.views} views • estimate USD ${entry.estimatedAmount().toPlainString()} • actual paid USD ${entry.paid.toPlainString()}")
                    entry.estimateIssues().forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (entry.rejectionReason.isNotBlank()) Text(entry.rejectionReason)
                    OutlinedButton(enabled = editable, onClick = {
                        editingId = entry.id; campaign = entry.campaign; platform = entry.platform; url = entry.postUrl
                        status = entry.status; views = entry.views.toString(); cpm = entry.ratePerThousand.toPlainString()
                        minimum = entry.minimumPayout.toPlainString(); maximum = entry.maximumPayout.toPlainString()
                        paid = entry.paid.toPlainString(); tierMinimum = entry.minimumTierOnePercent.toString()
                        tierActual = entry.actualTierOnePercent?.toString().orEmpty(); reason = entry.rejectionReason
                        message = "Editing ${entry.campaign}"
                    }) { Text("Edit submission") }
                }
            }
        }
    }
}

private fun moneyInput(value: String): String = value.filter { it.isDigit() || it == '.' }.take(20)
