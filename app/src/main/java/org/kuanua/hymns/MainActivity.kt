package org.kuanua.hymns

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private data class Verse(val number:String,val text:String)
private data class Hymn(val number:Int,val sectionId:String,val title:String,val source:String,val verses:List<Verse>)
private data class Section(val id:String,val title:String)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val data=JSONObject(assets.open("hymns.json").bufferedReader().use{it.readText()}).getJSONArray("hymns")
  val hymns=(0 until data.length()).map{i->val h=data.getJSONObject(i);val v=h.getJSONArray("stanzas");Hymn(h.getInt("number"),h.getString("sectionId"),h.optString("title"),h.optString("sourceReference"),(0 until v.length()).map{j->val x=v.getJSONObject(j);Verse(x.getString("number"),x.getString("text"))})}
  val raw=JSONObject(assets.open("sections.json").bufferedReader().use{it.readText()}).getJSONArray("sections")
  val sections=(0 until raw.length()).map{i->val s=raw.getJSONObject(i);Section(s.getString("id"),s.getString("title"))}
  setContent{HymnApp(hymns,sections,getSharedPreferences("hymnbook_local",MODE_PRIVATE))}
 }
}

@Composable private fun HymnApp(hymns:List<Hymn>,sections:List<Section>,prefs:SharedPreferences){
 var query by remember{mutableStateOf("")};var section by remember{mutableStateOf<String?>(null)};var opened by remember{mutableStateOf<Hymn?>(null)}
 var tab by remember{mutableStateOf("All")};var favs by remember{mutableStateOf(prefs.getStringSet("favs",emptySet())?.toSet()?:emptySet())}
 var recent by remember{mutableStateOf(prefs.getString("recent","").orEmpty().split(",").filter{it.isNotBlank()})}
 var scale by remember{mutableIntStateOf(prefs.getInt("scale",18))}
 fun favourite(n:Int){val next=favs.toMutableSet();if(!next.add(n.toString()))next.remove(n.toString());favs=next;prefs.edit().putStringSet("favs",next).apply()}
 fun open(h:Hymn){opened=h;recent=(listOf(h.number.toString())+recent.filter{it!=h.number.toString()}).take(30);prefs.edit().putString("recent",recent.joinToString(",")).apply()}
 MaterialTheme{Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().padding(16.dp)){
  Text("A BUK NA KAKAILAI",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
  Text("Kuanua Hymns · Blanche Bay Dialect · 1960 source",style=MaterialTheme.typography.bodySmall)
  if(opened!=null){
   val h=opened!!
   TextButton(onClick={opened=null}){Text("← Back")}
   Text(h.number.toString()+". "+h.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   if(h.source.isNotBlank())Text(h.source,style=MaterialTheme.typography.labelSmall)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    OutlinedButton(onClick={favourite(h.number)}){Text(if(favs.contains(h.number.toString()))"★ Favourite saved" else "☆ Add favourite")}
    TextButton(onClick={scale=(scale-2).coerceAtLeast(14);prefs.edit().putInt("scale",scale).apply()}){Text("A−")}
    TextButton(onClick={scale=(scale+2).coerceAtMost(30);prefs.edit().putInt("scale",scale).apply()}){Text("A+")}
   }
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
    if(h.verses.isEmpty())Text("This hymn is not reproduced in the supplied source scan. No lyrics have been added.")
    h.verses.forEach{v->Text("Verse "+v.number,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp));Text(v.text,fontSize=scale.sp,modifier=Modifier.padding(vertical=8.dp))}
    HorizontalDivider()
    Text("My note",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    var note by remember(h.number){mutableStateOf(prefs.getString("note_"+h.number,"").orEmpty())}
    var saved by remember{mutableStateOf(false)}
    OutlinedTextField(note,{note=it;saved=false},Modifier.fillMaxWidth(),placeholder={Text("Add a personal note")},minLines=2)
    Button(onClick={prefs.edit().putString("note_"+h.number,note).apply();saved=true}){Text("Save note")}
    if(saved)Text("Note saved on this device.",style=MaterialTheme.typography.bodySmall)
   }
  }else{
   Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("All","Favourites","Recent").forEach{t->FilterChip(selected=tab==t,onClick={tab=t},label={Text(t)})}}
   OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Search number, title or lyrics")},singleLine=true)
   if(tab=="All")Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick={section=null}){Text("All sections")};sections.forEach{s->TextButton(onClick={section=if(section==s.id)null else s.id}){Text(s.title)}}}
   val base=when(tab){"Favourites"->hymns.filter{favs.contains(it.number.toString())};"Recent"->recent.mapNotNull{id->hymns.find{it.number.toString()==id}};else->hymns}
   val shown=base.filter{(tab!="All"||section==null||it.sectionId==section)&&(query.isBlank()||it.number.toString().contains(query.trim())||it.title.contains(query,true)||it.verses.any{v->v.text.contains(query,true)})}
   Text(shown.size.toString()+" hymns",style=MaterialTheme.typography.labelMedium)
   LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(shown,key={it.number}){h->Card(Modifier.fillMaxWidth().clickable{open(h)}){Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(h.number.toString()+". "+h.title.ifBlank{"Hymn "+h.number},fontWeight=FontWeight.SemiBold);if(h.source.isNotBlank())Text(h.source,style=MaterialTheme.typography.bodySmall)};if(favs.contains(h.number.toString()))Text("★")}}}}
  }
 }}}
}
