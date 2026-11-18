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

private fun displayHymnTitle(h:Hymn):String = h.title.replace(Regex("^\\s*${h.number}\\.\\s*"), "").ifBlank { "Hymn ${h.number}" }

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
 var playlist by remember{mutableStateOf(prefs.getString("service_playlist","").orEmpty().split(",").filter{it.isNotBlank()})}
 var scale by remember{mutableIntStateOf(prefs.getInt("scale",18))};var presenting by remember{mutableStateOf(false)};var presentationIndex by remember{mutableIntStateOf(0)};var darkMode by remember{mutableStateOf(prefs.getBoolean("dark_mode",false))}
 fun favourite(n:Int){val next=favs.toMutableSet();if(!next.add(n.toString()))next.remove(n.toString());favs=next;prefs.edit().putStringSet("favs",next).apply()}
 fun open(h:Hymn){opened=h;recent=(listOf(h.number.toString())+recent.filter{it!=h.number.toString()}).take(30);prefs.edit().putString("recent",recent.joinToString(",")).apply()}
 fun addToService(n:Int){if(!playlist.contains(n.toString())){playlist=playlist+n.toString();prefs.edit().putString("service_playlist",playlist.joinToString(",")).apply()}}
 fun removeFromService(n:String){playlist=playlist.filter{it!=n};prefs.edit().putString("service_playlist",playlist.joinToString(",")).apply()}
 fun moveService(n:String,delta:Int){val from=playlist.indexOf(n);val to=from+delta;if(from<0||to !in playlist.indices)return;val next=playlist.toMutableList();val item=next.removeAt(from);next.add(to,item);playlist=next;prefs.edit().putString("service_playlist",next.joinToString(",")).apply()}
 MaterialTheme(colorScheme=if(darkMode) darkColorScheme() else lightColorScheme()){Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().padding(16.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
   Column(Modifier.weight(1f)){Text("A BUK NA KAKAILAI",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Kuanua Hymns · Blanche Bay Dialect · 1960 source",style=MaterialTheme.typography.bodySmall)}
   TextButton(onClick={darkMode=!darkMode;prefs.edit().putBoolean("dark_mode",darkMode).apply()}){Text(if(darkMode)"☀ Light" else "☾ Dark")}
  }
  if(presenting){
   val presentationHymns=playlist.mapNotNull{id->hymns.find{it.number.toString()==id}}
   TextButton(onClick={presenting=false}){Text("← Exit presentation")}
   if(presentationHymns.isEmpty()){Text("Add hymns to your Service list before starting presentation.")}else{
    val current=presentationHymns[presentationIndex.coerceIn(0,presentationHymns.lastIndex)]
    Text("WORSHIP SERVICE · ${presentationIndex+1} OF ${presentationHymns.size}",style=MaterialTheme.typography.labelMedium)
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Center){
     Text(current.number.toString()+". "+displayHymnTitle(current),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
     if(current.verses.isEmpty())Text("Lyrics are not available in the supplied source scan.",fontSize=scale.sp,modifier=Modifier.padding(top=20.dp))
     current.verses.forEach{v->Text("Verse "+v.number,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=18.dp));Text(v.text,fontSize=(scale+6).coerceAtMost(36).sp,modifier=Modifier.padding(vertical=10.dp))}
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
     OutlinedButton(onClick={presentationIndex=(presentationIndex-1).coerceAtLeast(0)},enabled=presentationIndex>0){Text("Previous")}
     Button(onClick={presentationIndex=(presentationIndex+1).coerceAtMost(presentationHymns.lastIndex)},enabled=presentationIndex<presentationHymns.lastIndex){Text("Next hymn")}
    }
   }
  }else if(opened!=null){
   val h=opened!!
   TextButton(onClick={opened=null}){Text("← Back")}
   Text(h.number.toString()+". "+displayHymnTitle(h),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   if(h.source.isNotBlank())Text(h.source,style=MaterialTheme.typography.labelSmall)
   Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
    OutlinedButton(onClick={favourite(h.number)}){Text(if(favs.contains(h.number.toString()))"★ Favourite saved" else "☆ Favourite")}
    OutlinedButton(onClick={addToService(h.number)}){Text(if(playlist.contains(h.number.toString()))"✓ In service" else "+ Service")}
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
   Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("All","Favourites","Recent","Service").forEach{t->FilterChip(selected=tab==t,onClick={tab=t},label={Text(t)})}}
   if(tab!="Service")OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Search number, title or lyrics")},singleLine=true)
   if(tab=="All")Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick={section=null}){Text("All sections")};sections.forEach{s->TextButton(onClick={section=if(section==s.id)null else s.id}){Text(s.title)}}}
   val base=when(tab){"Favourites"->hymns.filter{favs.contains(it.number.toString())};"Recent"->recent.mapNotNull{id->hymns.find{it.number.toString()==id}};"Service"->playlist.mapNotNull{id->hymns.find{it.number.toString()==id}};else->hymns}
   val shown=if(tab=="Service")base else base.filter{(tab!="All"||section==null||it.sectionId==section)&&(query.isBlank()||it.number.toString().contains(query.trim())||it.title.contains(query,true)||it.verses.any{v->v.text.contains(query,true)})}
   if(tab=="Service"){
    Text("Service order · ${playlist.size} hymns",style=MaterialTheme.typography.labelMedium)
    Button(onClick={presentationIndex=0;presenting=true},enabled=playlist.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("▶ Present worship service")}
    Text("Add hymns as you prepare a worship service. Your list is saved on this phone.",style=MaterialTheme.typography.bodySmall)
   }else Text(shown.size.toString()+" hymns",style=MaterialTheme.typography.labelMedium)
   LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(shown,key={it.number}){h->Card(Modifier.fillMaxWidth().clickable{open(h)}){Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(h.number.toString()+". "+displayHymnTitle(h),fontWeight=FontWeight.SemiBold);if(h.source.isNotBlank())Text(h.source,style=MaterialTheme.typography.bodySmall)};if(tab=="Service")Row(horizontalArrangement=Arrangement.spacedBy(0.dp)){TextButton(onClick={moveService(h.number.toString(),-1)}){Text("↑")};TextButton(onClick={moveService(h.number.toString(),1)}){Text("↓")};TextButton(onClick={removeFromService(h.number.toString())}){Text("Remove")}}else if(favs.contains(h.number.toString()))Text("★")}}}}
   if(tab=="Service"&&playlist.isNotEmpty())TextButton(onClick={playlist=emptyList();prefs.edit().putString("service_playlist","").apply()}){Text("Clear service list")}
   if(tab=="Service"&&playlist.isEmpty())Text("Your service list is empty. Browse hymns and tap + Service to add them.")
  }
 }}}
}
