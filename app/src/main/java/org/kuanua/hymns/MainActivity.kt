package org.kuanua.hymns

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private data class Stanza(val number: String, val text: String)
private data class Hymn(val number: Int, val sectionId: String, val title: String, val source: String, val stanzas: List<Stanza>)
private data class HymnSection(val id: String, val title: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = JSONObject(assets.open("hymns.json").bufferedReader().use { it.readText() })
        val array = root.getJSONArray("hymns")
        val hymns = (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val verses = item.getJSONArray("stanzas")
            val stanzas = (0 until verses.length()).map { j ->
                val verse = verses.getJSONObject(j)
                Stanza(verse.getString("number"), verse.getString("text"))
            }
            Hymn(item.getInt("number"), item.getString("sectionId"), item.optString("title"), item.optString("sourceReference"), stanzas)
        }
        val sectionRoot = JSONObject(assets.open("sections.json").bufferedReader().use { it.readText() })
        val sectionArray = sectionRoot.getJSONArray("sections")
        val sections = (0 until sectionArray.length()).map { i ->
            val item = sectionArray.getJSONObject(i)
            HymnSection(item.getString("id"), item.getString("title"))
        }
        setContent { HymnBookApp(hymns, sections) }
    }
}

@Composable
private fun HymnBookApp(hymns: List<Hymn>, sections: List<HymnSection>) {
    var query by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf<String?>(null) }
    var selectedHymn by remember { mutableStateOf<Hymn?>(null) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("A BUK NA KAKAILAI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Kuanua Hymns · Blanche Bay Dialect · 1960 source", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                if (selectedHymn != null) {
                    TextButton(onClick = { selectedHymn = null }) { Text("← All hymns") }
                    HymnDetail(selectedHymn!!)
                } else {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search number, title or lyrics") }, singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { selectedSection = null }) { Text("All sections") }
                        sections.forEach { section ->
                            TextButton(onClick = { selectedSection = if (selectedSection == section.id) null else section.id }) {
                                Text(if (selectedSection == section.id) "✓ " + section.title else section.title)
                            }
                        }
                    }
                    val filtered = hymns.filter { hymn ->
                        (selectedSection == null || hymn.sectionId == selectedSection) &&
                        (query.isBlank() || hymn.number.toString().contains(query.trim()) ||
                         hymn.title.contains(query, true) || hymn.stanzas.any { it.text.contains(query, true) })
                    }
                    Text(filtered.size.toString() + " hymns", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.number }) { hymn ->
                            Card(Modifier.fillMaxWidth().clickable { selectedHymn = hymn }) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(hymn.title.ifBlank { "Hymn " + hymn.number + " — source text unavailable" }, fontWeight = FontWeight.SemiBold)
                                    if (hymn.source.isNotBlank()) Text(hymn.source, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HymnDetail(hymn: Hymn) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(hymn.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (hymn.source.isNotBlank()) Text(hymn.source, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
        if (hymn.stanzas.isEmpty()) {
            Text("This hymn is not reproduced in the supplied source scan. No lyrics have been added.")
        } else {
            hymn.stanzas.forEach { stanza ->
                HorizontalDivider()
                Text("Verse " + stanza.number, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text(stanza.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
