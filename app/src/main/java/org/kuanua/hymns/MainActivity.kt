package org.kuanua.hymns

import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

private data class Verse(val number:String,val text:String)
private data class Hymn(val number:Int,val sectionId:String,val title:String,val source:String,val verses:List<Verse>)
private data class Section(val id:String,val title:String)

private fun displayHymnTitle(h:Hymn):String = h.title.replace(Regex("^\\s*${h.number}\\.\\s*"), "").ifBlank { "Hymn ${h.number}" }

private fun normalizeSearch(value:String):String = value.lowercase().filter { it.isLetterOrDigit() }

private data class HymnRecording(val platform:String,val title:String,val url:String)

private fun loadHymnRecordings(prefs:SharedPreferences,hymnNumber:Int):List<HymnRecording> {
 return runCatching {
  val array=org.json.JSONArray(prefs.getString("recordings_"+hymnNumber,"[]"))
  (0 until array.length()).mapNotNull { index ->
   val item=array.optJSONObject(index) ?: return@mapNotNull null
   HymnRecording(item.optString("platform"),item.optString("title"),item.optString("url"))
  }
 }.getOrDefault(emptyList())
}

private fun saveHymnRecordings(prefs:SharedPreferences,hymnNumber:Int,items:List<HymnRecording>) {
 val array=org.json.JSONArray()
 items.forEach { item ->
  array.put(JSONObject().put("platform",item.platform).put("title",item.title).put("url",item.url))
 }
 prefs.edit().putString("recordings_"+hymnNumber,array.toString()).apply()
}

private const val BACKUP_APP_ID = "org.kuanua.hymns"
private const val BACKUP_FORMAT_VERSION = 1

private fun createBackupJson(prefs: SharedPreferences): String {
 val values = JSONObject()
 prefs.all.toSortedMap().forEach { (key, value) ->
  val entry = JSONObject()
  when (value) {
   is String -> { entry.put("type", "string"); entry.put("value", value) }
   is Boolean -> { entry.put("type", "boolean"); entry.put("value", value) }
   is Int -> { entry.put("type", "int"); entry.put("value", value) }
   is Long -> { entry.put("type", "long"); entry.put("value", value) }
   is Float -> { entry.put("type", "float"); entry.put("value", value.toDouble()) }
   is Set<*> -> {
    if (value.all { it is String }) {
     entry.put("type", "string_set")
     entry.put("value", org.json.JSONArray(value.filterIsInstance<String>().sorted()))
    }
   }
  }
  if (entry.has("type")) values.put(key, entry)
 }
 return JSONObject().put("appId", BACKUP_APP_ID)
  .put("formatVersion", BACKUP_FORMAT_VERSION)
  .put("createdAt", java.time.Instant.now().toString())
  .put("preferences", values).toString(2)
}

private fun restoreBackupJson(prefs: SharedPreferences, json: String) {
 val root = JSONObject(json)
 require(root.optString("appId") == BACKUP_APP_ID) { "This backup belongs to a different app." }
 require(root.optInt("formatVersion", -1) == BACKUP_FORMAT_VERSION) { "This backup format is not supported." }
 val values = root.optJSONObject("preferences") ?: throw IllegalArgumentException("The backup contains no saved data.")
 val editor = prefs.edit().clear()
 val keys = values.keys()
 while (keys.hasNext()) {
  val key = keys.next()
  require(key.isNotBlank() && key.length <= 200) { "The backup contains an invalid setting." }
  val entry = values.optJSONObject(key) ?: throw IllegalArgumentException("Invalid saved setting: $key")
  when (entry.optString("type")) {
   "string" -> editor.putString(key, entry.getString("value"))
   "boolean" -> editor.putBoolean(key, entry.getBoolean("value"))
   "int" -> editor.putInt(key, entry.getInt("value"))
   "long" -> editor.putLong(key, entry.getLong("value"))
   "float" -> editor.putFloat(key, entry.getDouble("value").toFloat())
   "string_set" -> {
    val array = entry.optJSONArray("value") ?: throw IllegalArgumentException("Invalid saved list: $key")
    val set = (0 until array.length()).map { array.getString(it) }.toSet()
    editor.putStringSet(key, set)
   }
   else -> throw IllegalArgumentException("The backup contains an unsupported setting type.")
  }
 }
 check(editor.commit()) { "Could not save the restored data. Please try again." }
}

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val adsAllowed = mutableStateOf(false)
  val privacyOptionsRequired = mutableStateOf(false)
  var adsInitializationStarted = false
  val data=JSONObject(assets.open("hymns.json").bufferedReader().use{it.readText()}).getJSONArray("hymns")
  val hymns=(0 until data.length()).map{i->val h=data.getJSONObject(i);val v=h.getJSONArray("stanzas");Hymn(h.getInt("number"),h.getString("sectionId"),h.optString("title"),h.optString("sourceReference"),(0 until v.length()).map{j->val x=v.getJSONObject(j);Verse(x.getString("number"),x.getString("text"))})}
  val raw=JSONObject(assets.open("sections.json").bufferedReader().use{it.readText()}).getJSONArray("sections")
  val sections=(0 until raw.length()).map{i->val s=raw.getJSONObject(i);Section(s.getString("id"),s.getString("title"))}
  setContent {
   HymnApp(
    hymns,
    sections,
    getSharedPreferences("hymnbook_local",MODE_PRIVATE),
    adsAllowed.value,
    privacyOptionsRequired.value
   ) {
    UserMessagingPlatform.showPrivacyOptionsForm(this) {
     privacyOptionsRequired.value =
      UserMessagingPlatform.getConsentInformation(this).privacyOptionsRequirementStatus ==
       ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
     if (UserMessagingPlatform.getConsentInformation(this).canRequestAds() && !adsInitializationStarted) {
      adsInitializationStarted = true
      MobileAds.initialize(this) { runOnUiThread { adsAllowed.value = true } }
     }
    }
   }
  }
  val consentInformation = UserMessagingPlatform.getConsentInformation(this)
  fun initializeAdsIfAllowed() {
   privacyOptionsRequired.value =
    consentInformation.privacyOptionsRequirementStatus ==
     ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
   if (consentInformation.canRequestAds() && !adsInitializationStarted) {
    adsInitializationStarted = true
    MobileAds.initialize(this) { runOnUiThread { adsAllowed.value = true } }
   }
  }
  val consentParameters = ConsentRequestParameters.Builder().build()
  consentInformation.requestConsentInfoUpdate(
   this,
   consentParameters,
   {
    UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) {
     initializeAdsIfAllowed()
    }
   },
   {
    initializeAdsIfAllowed()
   }
  )
 }
}

@Composable private fun HymnApp(
 hymns:List<Hymn>,
 sections:List<Section>,
 prefs:SharedPreferences,
 adsAllowed:Boolean,
 privacyOptionsRequired:Boolean,
 onPrivacyOptionsClick:()->Unit
){
 val uriHandler = LocalUriHandler.current
 val context = LocalContext.current
 var backupMessage by remember { mutableStateOf<String?>(null) }
 val exportBackup = rememberLauncherForActivityResult(
  ActivityResultContracts.CreateDocument("application/json")
 ) { uri ->
  if (uri != null) {
   try {
    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
     it.write(createBackupJson(prefs))
    } ?: throw IllegalStateException("Could not create the backup file.")
    backupMessage = "Backup saved successfully."
    Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
   } catch (error: Exception) {
    backupMessage = "Backup failed: ${error.localizedMessage ?: "please try again"}"
    Toast.makeText(context, "Could not save backup", Toast.LENGTH_LONG).show()
   }
  }
 }
 val importBackup = rememberLauncherForActivityResult(
  ActivityResultContracts.OpenDocument()
 ) { uri ->
  if (uri != null) {
   try {
    val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
     ?: throw IllegalStateException("Could not read the selected file.")
    restoreBackupJson(prefs, json)
    Toast.makeText(context, "Backup restored. Reloading app…", Toast.LENGTH_LONG).show()
    (context as? ComponentActivity)?.recreate()
   } catch (error: Exception) {
    backupMessage = "Restore failed: ${error.localizedMessage ?: "the file may be invalid"}"
    Toast.makeText(context, "Could not restore backup", Toast.LENGTH_LONG).show()
   }
  }
 }
 var showWelcome by remember{mutableStateOf(true)}
 LaunchedEffect(showWelcome) { if (showWelcome) { delay(5_000); showWelcome = false } }
 var query by remember{mutableStateOf("")};var section by remember{mutableStateOf<String?>(null)};var sourceFilter by remember{mutableStateOf("All sources")};var sourceFilterExpanded by remember{mutableStateOf(false)};var opened by remember{mutableStateOf<Hymn?>(null)}
 var tab by remember{mutableStateOf("All")};var favs by remember{mutableStateOf(prefs.getStringSet("favs",emptySet())?.toSet()?:emptySet())}
 var recent by remember{mutableStateOf(prefs.getString("recent","").orEmpty().split(",").filter{it.isNotBlank()})}
 var playlist by remember{mutableStateOf(prefs.getString("service_playlist","").orEmpty().split(",").filter{it.isNotBlank()})}
 var scale by remember{mutableIntStateOf(prefs.getInt("scale",18))};var presenting by remember{mutableStateOf(false)};var presentingHymn by remember{mutableStateOf<Hymn?>(null)};var presentationIndex by remember{mutableIntStateOf(0)};var darkMode by remember{mutableStateOf(prefs.getBoolean("dark_mode",false))}
 var headerVisible by remember { mutableStateOf(true) }
 var showRecordingDialog by remember { mutableStateOf(false) }
 var recordingTitle by remember { mutableStateOf("") }
 var recordingUrl by remember { mutableStateOf("") }
 var recordingPlatform by remember { mutableStateOf("YouTube") }
 var recordingError by remember { mutableStateOf<String?>(null) }
 var sharedRecordings by remember { mutableStateOf<List<HymnRecording>>(emptyList()) }
 var sharedRecordingsMessage by remember { mutableStateOf<String?>(null) }
 var adminUid by remember { mutableStateOf<String?>(null) }
 var isAdmin by remember { mutableStateOf(false) }
 var showAdminPanel by remember { mutableStateOf(false) }
 var pendingSubmissions by remember { mutableStateOf<List<com.google.firebase.firestore.DocumentSnapshot>>(emptyList()) }
 var adminMessage by remember { mutableStateOf<String?>(null) }
 var adminLoading by remember { mutableStateOf(false) }
 LaunchedEffect(Unit) {
  FirebaseAuth.getInstance().signInAnonymously()
   .addOnSuccessListener { result ->
    val uid = result.user?.uid ?: return@addOnSuccessListener
    adminUid = uid
    FirebaseFirestore.getInstance().collection("admins").document(uid).get()
     .addOnSuccessListener { isAdmin = it.getBoolean("enabled") == true }
   }
 }
 fun loadPendingSubmissions() {
  adminLoading = true
  FirebaseFirestore.getInstance().collection("recordingSubmissions")
   .whereEqualTo("status", "pending")
   .get()
   .addOnSuccessListener { pendingSubmissions = it.documents; adminMessage = if (it.isEmpty) "No submissions are waiting for review." else null; adminLoading = false }
   .addOnFailureListener { adminMessage = "Could not load submissions. Check your connection."; adminLoading = false }
 }
 fun reviewSubmission(doc: com.google.firebase.firestore.DocumentSnapshot, approve: Boolean) {
  val db = FirebaseFirestore.getInstance()
  val batch = db.batch()
  if (approve) {
   val recording = hashMapOf<String,Any>(
    "hymnNumber" to (doc.getLong("hymnNumber") ?: 0L),
    "platform" to (doc.getString("platform") ?: "Other"),
    "title" to (doc.getString("title").orEmpty()),
    "url" to (doc.getString("url") ?: ""),
    "status" to "approved",
    "submittedBy" to (doc.getString("submittedBy") ?: ""),
    "approvedAt" to FieldValue.serverTimestamp()
   )
   batch.set(db.collection("recordings").document(doc.id), recording)
  }
  batch.update(doc.reference, "status", if (approve) "approved" else "rejected", "reviewedAt", FieldValue.serverTimestamp())
  batch.commit().addOnSuccessListener { loadPendingSubmissions(); adminMessage = if (approve) "Recording approved and published." else "Submission rejected." }
   .addOnFailureListener { adminMessage = "Review action failed. Please try again." }
 }
 val headerScrollConnection = remember { object : NestedScrollConnection { override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset { if (consumed.y < -6f) headerVisible = false else if (consumed.y > 6f) headerVisible = true; return Offset.Zero } } }
 BackHandler(enabled = opened != null || presenting || presentingHymn != null || showAdminPanel) { when { presenting -> presenting = false; presentingHymn != null -> presentingHymn = null; showAdminPanel -> showAdminPanel = false; opened != null -> opened = null } }
 fun favourite(n:Int){val next=favs.toMutableSet();if(!next.add(n.toString()))next.remove(n.toString());favs=next;prefs.edit().putStringSet("favs",next).apply()}
 fun open(h:Hymn){opened=h;recent=(listOf(h.number.toString())+recent.filter{it!=h.number.toString()}).take(30);prefs.edit().putString("recent",recent.joinToString(",")).apply()}
 fun addToService(n:Int){if(!playlist.contains(n.toString())){playlist=playlist+n.toString();prefs.edit().putString("service_playlist",playlist.joinToString(",")).apply()}}
 fun removeFromService(n:String){playlist=playlist.filter{it!=n};prefs.edit().putString("service_playlist",playlist.joinToString(",")).apply()}
 fun moveService(n:String,delta:Int){val from=playlist.indexOf(n);val to=from+delta;if(from<0||to !in playlist.indices)return;val next=playlist.toMutableList();val item=next.removeAt(from);next.add(to,item);playlist=next;prefs.edit().putString("service_playlist",next.joinToString(",")).apply()}
 val jakartaFamily = FontFamily(
  Font(R.font.plus_jakarta_sans, FontWeight.W200),
  Font(R.font.plus_jakarta_sans, FontWeight.W300),
  Font(R.font.plus_jakarta_sans, FontWeight.Normal),
  Font(R.font.plus_jakarta_sans, FontWeight.Medium),
  Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
  Font(R.font.plus_jakarta_sans, FontWeight.Bold),
  Font(R.font.plus_jakarta_sans, FontWeight.W800)
 )
 val modernTypography = Typography().copy(
  displayLarge = Typography().displayLarge.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp),
  displayMedium = Typography().displayMedium.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
  headlineLarge = Typography().headlineLarge.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
  headlineMedium = Typography().headlineMedium.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.35).sp),
  headlineSmall = Typography().headlineSmall.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
  titleLarge = Typography().titleLarge.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
  titleMedium = Typography().titleMedium.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold),
  titleSmall = Typography().titleSmall.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold),
  bodyLarge = Typography().bodyLarge.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp),
  bodyMedium = Typography().bodyMedium.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
  bodySmall = Typography().bodySmall.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold),
  labelLarge = Typography().labelLarge.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold),
  labelMedium = Typography().labelMedium.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold),
  labelSmall = Typography().labelSmall.copy(fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold)
 )
 val maroon=Color(0xFF681F36)
 val gold=Color(0xFFB99456)
 val ivory=Color(0xFFF7F4F0)
 val sage=Color(0xFF526B5D)
 val lightPalette=lightColorScheme(
  primary=maroon,onPrimary=Color.White,primaryContainer=Color(0xFFF1E3E8),onPrimaryContainer=Color(0xFF4D172A),
  secondary=gold,onSecondary=Color(0xFF241A08),secondaryContainer=Color(0xFFF3EBDD),onSecondaryContainer=Color(0xFF493719),
  tertiary=sage,onTertiary=Color.White,background=ivory,onBackground=Color(0xFF29242A),
  surface=Color(0xFFFFFEFC),onSurface=Color(0xFF29242A),surfaceVariant=Color(0xFFF0EAE6),
  onSurfaceVariant=Color(0xFF62585D),outline=Color(0xFFD9CDD0),outlineVariant=Color(0xFFE9E0E2)
 )
 val darkPalette=darkColorScheme(
  primary=Color(0xFF963B56),onPrimary=Color(0xFFFFF7F2),primaryContainer=Color(0xFF572437),onPrimaryContainer=Color(0xFFFFDCE5),
  secondary=Color(0xFFD6B875),onSecondary=Color(0xFF241A0A),secondaryContainer=Color(0xFF49391D),onSecondaryContainer=Color(0xFFF3E2BC),
  tertiary=Color(0xFFA9BDAF),onTertiary=Color(0xFF17251D),tertiaryContainer=Color(0xFF304438),onTertiaryContainer=Color(0xFFD1E5D7),
  background=Color(0xFF111214),onBackground=Color(0xFFF1ECE8),
  surface=Color(0xFF1A1B1E),onSurface=Color(0xFFF1ECE8),
  surfaceVariant=Color(0xFF29272B),onSurfaceVariant=Color(0xFFD0C6C7),
  outline=Color(0xFF82767B),outlineVariant=Color(0xFF494247)
 )
 MaterialTheme(colorScheme=if(darkMode) darkPalette else lightPalette, typography=modernTypography){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Box(Modifier.fillMaxSize().safeDrawingPadding().nestedScroll(headerScrollConnection)){Column(Modifier.fillMaxSize().padding(if(presenting || presentingHymn != null) 0.dp else 16.dp)){
  if (!presenting && presentingHymn == null) AnimatedVisibility(
   visible=headerVisible,
   enter=slideInVertically(animationSpec=tween(260)) { -it } + fadeIn(animationSpec=tween(220)),
   exit=slideOutVertically(animationSpec=tween(220)) { -it } + fadeOut(animationSpec=tween(180))
  ) {
   Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primary),shape=MaterialTheme.shapes.large){
    Row(
     Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=9.dp),
     verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
    ){
     Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)){
      Text("A BUK NA KAKAILAI",fontSize=20.sp,lineHeight=24.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimary,letterSpacing=0.2.sp)
      Text("Kuanua Hymns · Blanche Bay Dialect · 1960 source",fontSize=10.sp,lineHeight=13.sp,color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.82f))
     }
     IconButton(onClick={darkMode=!darkMode;prefs.edit().putBoolean("dark_mode",darkMode).apply()},modifier=Modifier.size(38.dp)){
      Text(if(darkMode)"☀" else "☾",fontSize=22.sp,color=MaterialTheme.colorScheme.onPrimary)
     }
     if(privacyOptionsRequired) {
      TextButton(onClick=onPrivacyOptionsClick,contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){
       Text("Privacy",fontSize=10.sp,color=MaterialTheme.colorScheme.onPrimary)
      }
     }
    }
   }
  }
  if(showAdminPanel){
   Text("COMMUNITY RECORDING REVIEW",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   Text("Administrator access · ${adminUid ?: "Not signed in"}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Button(onClick={loadPendingSubmissions()}){Text("Refresh")}
    OutlinedButton(onClick={showAdminPanel=false}){Text("Close")}
   }
   adminMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
   if(adminLoading) CircularProgressIndicator()
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
    pendingSubmissions.forEach { doc ->
     Card(Modifier.fillMaxWidth()){
      Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
       Text("Hymn ${doc.getLong("hymnNumber") ?: 0L}",fontWeight=FontWeight.Bold)
       Text(doc.getString("title").orEmpty().ifBlank { doc.getString("platform") ?: "Recording" })
       Text(doc.getString("platform") ?: "Other",style=MaterialTheme.typography.bodySmall)
       Text(doc.getString("url").orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
       Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Button(onClick={reviewSubmission(doc,true)}){Text("Approve")}
        OutlinedButton(onClick={reviewSubmission(doc,false)}){Text("Reject")}
       }
      }
     }
    }
   }
  }else if(presenting || presentingHymn != null){
   val presentationHymns=if(presentingHymn != null) listOf(presentingHymn!!) else playlist.mapNotNull{id->hymns.find{it.number.toString()==id}}
   val stage=Color(0xFF35101F)
   val stageCard=Color(0xFF4A1A2D)
   val gold=Color(0xFFE8C98A)
   Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF24101A),stage,Color(0xFF541D34))))){
    if(presentationHymns.isEmpty()){
     Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
      Text("♪",fontSize=42.sp,color=gold)
      Text("Your worship service",style=MaterialTheme.typography.headlineSmall,color=Color.White,fontWeight=FontWeight.Bold)
      Text("Add hymns to your Service list before starting presentation.",color=Color.White.copy(alpha=.78f),modifier=Modifier.padding(top=12.dp))
      OutlinedButton(onClick={presenting=false;presentingHymn=null},modifier=Modifier.padding(top=24.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=gold)){Text("Close")}
     }
    }else{
     val current=presentationHymns[presentationIndex.coerceIn(0,presentationHymns.lastIndex)]
     Column(Modifier.fillMaxSize().padding(horizontal=20.dp, vertical=8.dp)){
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
       IconButton(onClick={presenting=false;presentingHymn=null}){
        Text("✕",fontSize=26.sp,fontWeight=FontWeight.Light,color=Color.White)
       }
      }
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally){
       Text(displayHymnTitle(current),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=Color.White,textAlign=androidx.compose.ui.text.style.TextAlign.Center,lineHeight=38.sp,modifier=Modifier.padding(top=18.dp,bottom=8.dp))
       if(current.source.isNotBlank()) Text(current.source,fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=gold.copy(alpha=.9f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.padding(bottom=18.dp))
       if(current.verses.isNotEmpty()){
        current.verses.forEachIndexed { index, verse ->
         Surface(Modifier.fillMaxWidth().padding(vertical=5.dp),shape=MaterialTheme.shapes.large,color=stageCard.copy(alpha=.88f)){
          Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=14.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally){
           Text("VERSE ${verse.number}",fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp,color=gold,modifier=Modifier.padding(bottom=8.dp))
           Text(verse.text,fontSize=(scale+4).coerceAtMost(34).sp,lineHeight=((scale+4)*1.42f).coerceAtMost(48f).sp,fontWeight=FontWeight.SemiBold,color=Color.White,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
          }
         }
        }
       }else Text("Lyrics are not available in the supplied source scan.",fontSize=scale.sp,color=Color.White.copy(alpha=.8f),modifier=Modifier.padding(top=20.dp))
       Text("✦",fontSize=18.sp,color=gold,modifier=Modifier.padding(vertical=12.dp))
      }
      Box(Modifier.fillMaxWidth().height(1.dp).background(gold.copy(alpha=.4f)))
      Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
       if(presentingHymn == null) OutlinedButton(onClick={presentationIndex=(presentationIndex-1).coerceAtLeast(0)},enabled=presentationIndex>0,colors=ButtonDefaults.outlinedButtonColors(contentColor=gold)){Text("‹  Previous")}
       else Spacer(Modifier.width(4.dp))
       Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)){
        TextButton(onClick={scale=(scale-2).coerceAtLeast(14);prefs.edit().putInt("scale",scale).apply()},colors=ButtonDefaults.textButtonColors(contentColor=gold)){Text("A−")}
        Text("${scale+4}",fontSize=11.sp,color=Color.White.copy(alpha=.8f))
        TextButton(onClick={scale=(scale+2).coerceAtMost(30);prefs.edit().putInt("scale",scale).apply()},colors=ButtonDefaults.textButtonColors(contentColor=gold)){Text("A+")}
       }
       if(presentingHymn == null) Button(onClick={presentationIndex=(presentationIndex+1).coerceAtMost(presentationHymns.lastIndex)},enabled=presentationIndex<presentationHymns.lastIndex,colors=ButtonDefaults.buttonColors(containerColor=gold,contentColor=stage)){Text("Next hymn  ›")}
       else Spacer(Modifier.width(4.dp))
      }
     }
    }
   }
  }else if(opened!=null){
   val h=opened!!
   Text(h.number.toString()+". "+displayHymnTitle(h),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   if(h.source.isNotBlank())Text(h.source,style=MaterialTheme.typography.labelSmall)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
    OutlinedButton(onClick={favourite(h.number)},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=5.dp,vertical=8.dp)){Text(if(favs.contains(h.number.toString()))"★ Saved" else "☆ Favourite",maxLines=1)}
    OutlinedButton(onClick={addToService(h.number)},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=5.dp,vertical=8.dp)){Text(if(playlist.contains(h.number.toString()))"✓ Service" else "+ Service",maxLines=1)}
    OutlinedButton(onClick={val hymnText=h.number.toString()+". "+displayHymnTitle(h)+System.lineSeparator()+System.lineSeparator()+h.verses.joinToString(System.lineSeparator()+System.lineSeparator()){v->"Verse "+v.number+System.lineSeparator()+v.text};val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager;clipboard.setPrimaryClip(ClipData.newPlainText("Hymn "+h.number,hymnText));Toast.makeText(context,"Hymn copied",Toast.LENGTH_SHORT).show()},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=5.dp,vertical=8.dp)){Text("Copy",maxLines=1)}
    OutlinedButton(onClick={presentationIndex=0;presentingHymn=h},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=5.dp,vertical=8.dp)){Text("▶ Present",maxLines=1)}
   }
   Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text("Text size",style=MaterialTheme.typography.labelMedium);TextButton(onClick={scale=(scale-2).coerceAtLeast(14);prefs.edit().putInt("scale",scale).apply()}){Text("A−")};Text("${scale} sp",style=MaterialTheme.typography.labelMedium);TextButton(onClick={scale=(scale+2).coerceAtMost(30);prefs.edit().putInt("scale",scale).apply()}){Text("A+")}}
   val hymnRecordings=remember(h.number){mutableStateOf(loadHymnRecordings(prefs,h.number))}
   LaunchedEffect(h.number) {
    sharedRecordings = emptyList()
    sharedRecordingsMessage = "Loading approved community recordings…"
    FirebaseFirestore.getInstance().collection("recordings")
     .whereEqualTo("hymnNumber", h.number)
     .whereEqualTo("status", "approved")
     .get()
     .addOnSuccessListener { snapshot ->
      sharedRecordings = snapshot.documents.mapNotNull { doc ->
       val platform = doc.getString("platform") ?: return@mapNotNull null
       val url = doc.getString("url") ?: return@mapNotNull null
       HymnRecording(platform, doc.getString("title").orEmpty(), url)
      }
      sharedRecordingsMessage = if (sharedRecordings.isEmpty()) "No approved community recordings yet." else null
     }
     .addOnFailureListener { sharedRecordingsMessage = "Community recordings are temporarily unavailable." }
   }
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
    Text("Listen to this hymn",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    Text("Add a recording from YouTube, Spotify, TikTok, Facebook or another website.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick={recordingTitle="";recordingUrl="";recordingPlatform="YouTube";recordingError=null;showRecordingDialog=true},modifier=Modifier.fillMaxWidth()) { Text("+ Add a recording link") }
    if(hymnRecordings.value.isEmpty()) Text("No recordings added yet.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    hymnRecordings.value.forEachIndexed { index, recording ->
     Card(Modifier.fillMaxWidth().padding(vertical=3.dp)) {
      Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
       Column(Modifier.weight(1f)) {
        Text(recording.title.ifBlank { recording.platform+" recording" },fontWeight=FontWeight.SemiBold)
        Text(recording.platform,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
       }
       TextButton(onClick={runCatching{uriHandler.openUri(recording.url)}.onFailure{Toast.makeText(context,"Could not open this link",Toast.LENGTH_SHORT).show()}}) { Text("Open") }
       TextButton(onClick={val next=hymnRecordings.value.toMutableList().also{it.removeAt(index)};hymnRecordings.value=next;saveHymnRecordings(prefs,h.number,next)}) { Text("Remove") }
      }
     }
    }
    if (sharedRecordings.isNotEmpty()) {
     Text("Community recordings",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp))
     sharedRecordings.forEach { recording ->
      Card(Modifier.fillMaxWidth().padding(vertical=3.dp)) {
       Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
         Text(recording.title.ifBlank { recording.platform+" recording" },fontWeight=FontWeight.SemiBold)
         Text(recording.platform+" · Approved",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick={runCatching{uriHandler.openUri(recording.url)}.onFailure{Toast.makeText(context,"Could not open this link",Toast.LENGTH_SHORT).show()}}) { Text("Open") }
       }
      }
     }
    }
    sharedRecordingsMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=4.dp)) }
    Text("Your saved links are stored on this device. New community submissions are reviewed before appearing publicly.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=4.dp))
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
   if(showRecordingDialog) {
    AlertDialog(
     onDismissRequest={showRecordingDialog=false},
     title={Text("Add hymn recording")},
     text={
      Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
       var platformMenu by remember { mutableStateOf(false) }
       Box {
        OutlinedButton(onClick={platformMenu=true},modifier=Modifier.fillMaxWidth()) { Text("Platform: "+recordingPlatform) }
        DropdownMenu(expanded=platformMenu,onDismissRequest={platformMenu=false}) {
         listOf("YouTube","Spotify","TikTok","Facebook","Other").forEach { platform ->
          DropdownMenuItem(text={Text(platform)},onClick={recordingPlatform=platform;platformMenu=false})
         }
        }
       }
       OutlinedTextField(value=recordingTitle,onValueChange={recordingTitle=it},label={Text("Singer / choir / description (optional)")},singleLine=true)
       OutlinedTextField(value=recordingUrl,onValueChange={recordingUrl=it},label={Text("Recording URL")},singleLine=true)
       recordingError?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
      }
     },
     confirmButton={
      TextButton(onClick={
       val parsed=runCatching{android.net.Uri.parse(recordingUrl.trim())}.getOrNull()
       if(parsed==null || parsed.scheme !in listOf("http","https") || parsed.host.isNullOrBlank()) {
        recordingError="Enter a valid link beginning with https:// or http://."
       } else if(hymnRecordings.value.any{it.url.equals(recordingUrl.trim(),ignoreCase=true)}) {
        recordingError="This recording link has already been added."
       } else {
        val newRecording=HymnRecording(recordingPlatform,recordingTitle.trim(),recordingUrl.trim())
        FirebaseAuth.getInstance().signInAnonymously()
         .addOnSuccessListener { authResult ->
          val uid=authResult.user?.uid
          if (uid == null) {
           recordingError="Could not verify your submission. Please try again."
          } else {
           val submission=hashMapOf<String,Any>(
            "hymnNumber" to h.number,
            "platform" to recordingPlatform,
            "title" to recordingTitle.trim(),
            "url" to recordingUrl.trim(),
            "status" to "pending",
            "submittedBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
           )
           FirebaseFirestore.getInstance().collection("recordingSubmissions").add(submission)
            .addOnSuccessListener {
             val next=hymnRecordings.value+newRecording
             hymnRecordings.value=next
             saveHymnRecordings(prefs,h.number,next)
             showRecordingDialog=false
             Toast.makeText(context,"Submitted for review. It will appear publicly after approval.",Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { recordingError="Submission failed. Check your connection and try again." }
          }
         }
         .addOnFailureListener { recordingError="Could not connect to the submission service. Please try again." }
       }
      }) { Text("Save link") }
     },
     dismissButton={TextButton(onClick={showRecordingDialog=false}){Text("Cancel")}}
    )
   }
  }else{
   AnimatedVisibility(
    visible=headerVisible,
    enter=slideInVertically(animationSpec=tween(260)) { -it } + fadeIn(animationSpec=tween(220)),
    exit=slideOutVertically(animationSpec=tween(220)) { -it } + fadeOut(animationSpec=tween(180))
   ) {
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("All","Favourites","Recent","Service").forEach{t->FilterChip(selected=tab==t,onClick={tab=t},label={Text(t,fontWeight=FontWeight.SemiBold)})}}
   }
   if(tab!="Service") Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
    OutlinedTextField(query,{query=it},Modifier.weight(1f),label={Text("Search title, lyrics or reference")},singleLine=true)
    Box {
     OutlinedButton(onClick={sourceFilterExpanded=true},contentPadding=PaddingValues(horizontal=10.dp,vertical=12.dp)){Text("Filter")}
     DropdownMenu(expanded=sourceFilterExpanded,onDismissRequest={sourceFilterExpanded=false}){
      listOf("All sources","Has reference","M.H.B.","Other references","1200.S.","S.M.","German Tune","888.S.","Gypsy Smith's Hymnal","Alexander H.B.","M.S.S.H.B.","Tune: March of the Men of Harlech","Children's Hosannas","L.M.","C.M.","A.S.","Tune: Banner Anniversary H.B.","L. Tonkin","M.B.B.","Heart and Voice","Music by Thomas Troman").forEach { option ->
       DropdownMenuItem(text={Text(option)},onClick={sourceFilter=option;sourceFilterExpanded=false})
      }
     }
    }
   }
   if(tab=="All")Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
    FilterChip(
     selected=section==null,
     onClick={section=null},
     label={Text("ALL SECTIONS")},
     colors=FilterChipDefaults.filterChipColors(
      selectedContainerColor=MaterialTheme.colorScheme.primary,
      selectedLabelColor=MaterialTheme.colorScheme.onPrimary
     )
    )
    sections.forEach{s->
     FilterChip(
      selected=section==s.id,
      onClick={section=s.id},
      label={Text(s.title)},
      colors=FilterChipDefaults.filterChipColors(
       selectedContainerColor=MaterialTheme.colorScheme.primary,
       selectedLabelColor=MaterialTheme.colorScheme.onPrimary
      )
     )
    }
   }
   val base=when(tab){"Favourites"->hymns.filter{favs.contains(it.number.toString())};"Recent"->recent.mapNotNull{id->hymns.find{it.number.toString()==id}};"Service"->playlist.mapNotNull{id->hymns.find{it.number.toString()==id}};else->hymns}
   val normalizedQuery=normalizeSearch(query)
   val shown=if(tab=="Service")base else base.filter { hymn ->
    val source=normalizeSearch(hymn.source)
    val matchesQuery=query.isBlank() ||
     normalizeSearch(hymn.number.toString()).contains(normalizedQuery) ||
     normalizeSearch(hymn.title).contains(normalizedQuery) ||
     source.contains(normalizedQuery) ||
     hymn.verses.any { verse -> normalizeSearch(verse.text).contains(normalizedQuery) }
    val matchesSource=when(sourceFilter){
     "Has reference" -> hymn.source.isNotBlank()
     "M.H.B." -> source.contains("mhb")
     "Other references" -> hymn.source.isNotBlank() && !source.contains("mhb")
     "All sources","Has reference" -> true
     else -> source.contains(normalizeSearch(sourceFilter))
    }
    (tab!="All"||section==null||hymn.sectionId==section) && matchesQuery && matchesSource
   }
   if(tab=="Service"){
    Text("Service order · ${playlist.size} hymns",style=MaterialTheme.typography.labelMedium)
    Button(onClick={presentationIndex=0;presenting=true},enabled=playlist.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("▶ Present worship service")}
    Text("Add hymns as you prepare a worship service. Your list is saved on this phone.",style=MaterialTheme.typography.bodySmall)
   }else Text(shown.size.toString()+" hymns",style=MaterialTheme.typography.labelMedium)
   LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
    itemsIndexed(shown,key={_,h->"hymn_${h.number}"}){index,h->
     Card(Modifier.fillMaxWidth().clickable{open(h)}){
      Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){
       Column(Modifier.weight(1f)){
        Text(h.number.toString()+". "+displayHymnTitle(h),fontWeight=FontWeight.SemiBold)
        if(h.source.isNotBlank())Text(h.source,style=MaterialTheme.typography.bodySmall)
       }
       if(tab=="Service")Row(horizontalArrangement=Arrangement.spacedBy(0.dp)){
        TextButton(onClick={moveService(h.number.toString(),-1)}){Text("↑")}
        TextButton(onClick={moveService(h.number.toString(),1)}){Text("↓")}
        TextButton(onClick={removeFromService(h.number.toString())}){Text("Remove")}
       }else if(favs.contains(h.number.toString()))Text("★")
      }
     }
     if(index==5 && tab!="Service" && adsAllowed && BuildConfig.ADMOB_NATIVE_AD_UNIT_ID.isNotBlank()) {
      NativeAdCard()
     }
    }
   }
   if(tab=="Service"&&playlist.isNotEmpty())TextButton(onClick={playlist=emptyList();prefs.edit().putString("service_playlist","").apply()}){Text("Clear service list")}
   if(tab=="Service"&&playlist.isEmpty())Text("Your service list is empty. Browse hymns and tap + Service to add them.")
   if(tab!="Service") AdMobBanner(adsAllowed)
  }
  if (!presenting && presentingHymn == null) {
  Spacer(Modifier.height(4.dp))
  HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant, thickness=0.7.dp)
  Row(
   Modifier.fillMaxWidth().padding(top=2.dp),
   horizontalArrangement=Arrangement.Center,
   verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
  ) {
   TextButton(
    onClick={uriHandler.openUri("https://nptovaira-debug.github.io/a-buk-na-kakailai/privacy.html")},
    contentPadding=PaddingValues(horizontal=5.dp,vertical=0.dp)
   ) { Text("Privacy Policy",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
   Text("•",fontSize=10.sp,color=MaterialTheme.colorScheme.outline)
   if(adminUid != null) {
    TextButton(onClick={
     val uid=adminUid ?: return@TextButton
     val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
     clipboard.setPrimaryClip(ClipData.newPlainText("Firebase user ID",uid))
     Toast.makeText(context,"Device ID copied",Toast.LENGTH_SHORT).show()
    },contentPadding=PaddingValues(horizontal=5.dp,vertical=0.dp)){Text("Copy device ID",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    Text("•",fontSize=10.sp,color=MaterialTheme.colorScheme.outline)
    if(isAdmin) TextButton(onClick={showAdminPanel=true;loadPendingSubmissions()},contentPadding=PaddingValues(horizontal=5.dp,vertical=0.dp)){Text("Review recordings",fontSize=10.sp)}
    else TextButton(onClick={
     val uid=adminUid
     if(uid != null) FirebaseFirestore.getInstance().collection("admins").document(uid).get()
      .addOnSuccessListener { isAdmin=it.getBoolean("enabled")==true; Toast.makeText(context,if(isAdmin)"Administrator access enabled" else "Administrator access not yet enabled",Toast.LENGTH_SHORT).show() }
      .addOnFailureListener { Toast.makeText(context,"Could not check administrator access",Toast.LENGTH_SHORT).show() }
    },contentPadding=PaddingValues(horizontal=5.dp,vertical=0.dp)){Text("Check admin",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    Text("•",fontSize=10.sp,color=MaterialTheme.colorScheme.outline)
   }
   TextButton(
    onClick={exportBackup.launch("A_Buk_na_Kakailai_Backup.json")},
    contentPadding=PaddingValues(horizontal=5.dp,vertical=0.dp)
   ) { Text("Export backup",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
   Text("•",fontSize=10.sp,color=MaterialTheme.colorScheme.outline)
   TextButton(
    onClick={importBackup.launch(arrayOf("application/json","text/*"))},
    contentPadding=PaddingValues(horizontal=5.dp,vertical=0.dp)
   ) { Text("Restore",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
  }
  backupMessage?.let { message ->
   Text(message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=4.dp))
  }
  }
 }
 if(showWelcome) WelcomeScreen(darkMode = darkMode)
 }}}
}


@Composable
private fun AdMobBanner(adsAllowed:Boolean) {
 if(!adsAllowed || BuildConfig.ADMOB_BANNER_AD_UNIT_ID.isBlank()) return
 val context = LocalContext.current
 val adView = remember {
  AdView(context).apply {
   adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
   val availableWidth = (context.resources.configuration.screenWidthDp - 32).coerceAtLeast(1)
   setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, availableWidth))
   loadAd(AdRequest.Builder().build())
  }
 }
 DisposableEffect(adView) { onDispose { adView.destroy() } }
 AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun NativeAdCard() {
 val context = LocalContext.current
 val adUnitId = BuildConfig.ADMOB_NATIVE_AD_UNIT_ID
 if(adUnitId.isBlank()) return
 val nativeAdState = remember { mutableStateOf<NativeAd?>(null) }
 DisposableEffect(adUnitId) {
  val loader = AdLoader.Builder(context, adUnitId)
   .forNativeAd { loadedAd ->
    nativeAdState.value?.destroy()
    nativeAdState.value = loadedAd
   }
   .withAdListener(object : com.google.android.gms.ads.AdListener() {
    override fun onAdFailedToLoad(error: LoadAdError) {
     // Do not retry automatically; avoids repeated requests on poor connections.
    }
   })
   .withNativeAdOptions(NativeAdOptions.Builder().build())
   .build()
  loader.loadAd(AdRequest.Builder().build())
  onDispose {
   nativeAdState.value?.destroy()
   nativeAdState.value = null
  }
 }
 val nativeAd = nativeAdState.value ?: return
 AndroidView(
  factory = { viewContext ->
   val adView = NativeAdView(viewContext)
   val column = LinearLayout(viewContext).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(18, 14, 18, 14)
    background = android.graphics.drawable.GradientDrawable().apply {
     setColor(AndroidColor.WHITE)
     cornerRadius = 24f
     setStroke(1, AndroidColor.rgb(225, 216, 219))
    }
   }
   val label = TextView(viewContext).apply {
    text = "ADVERTISEMENT"
    textSize = 10f
    setTextColor(AndroidColor.rgb(105, 31, 54))
   }
   val headline = TextView(viewContext).apply {
    textSize = 16f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(AndroidColor.rgb(38, 32, 35))
    setPadding(0, 8, 0, 4)
   }
   val body = TextView(viewContext).apply {
    textSize = 13f
    setTextColor(AndroidColor.DKGRAY)
   }
   val callToAction = TextView(viewContext).apply {
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(AndroidColor.rgb(105, 31, 54))
    setPadding(0, 10, 0, 0)
   }
   column.addView(label)
   column.addView(headline)
   column.addView(body)
   column.addView(callToAction)
   adView.addView(column, ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.WRAP_CONTENT
   ))
   adView.headlineView = headline
   adView.bodyView = body
   adView.callToActionView = callToAction
   adView
  },
  update = { adView -> adView.setNativeAd(nativeAd) },
  modifier = Modifier.fillMaxWidth()
 )
}


@Composable
private fun WelcomeScreen(darkMode: Boolean) {
 val gold = if (darkMode) Color(0xFFD6B875) else Color(0xFFB99456)
 val ivory = if (darkMode) Color(0xFFF1ECE8) else Color(0xFFF7F4F0)
 val muted = if (darkMode) Color(0xFFD0C6C7) else Color(0xFF62585D)
 val background = if (darkMode) Color(0xFF111214) else Color(0xFF681F36)
 val panel = if (darkMode) Color(0xFF1A1B1E) else Color(0xFF572437)
 Box(
  modifier = Modifier
   .fillMaxSize()
   .background(
    Brush.verticalGradient(
     colors = listOf(if (darkMode) Color(0xFF29272B) else Color(0xFF7A2B45), background, if (darkMode) Color(0xFF111214) else Color(0xFF4D172A))
    )
   )
 ) {
  Box(
   Modifier.fillMaxSize().background(
    Brush.radialGradient(
     colors = listOf(gold.copy(alpha = 0.10f), Color.Transparent),
     radius = 950f
    )
   )
  )
  Column(
   modifier = Modifier
    .fillMaxSize()
    .verticalScroll(rememberScrollState())
    .padding(horizontal = 28.dp, vertical = 30.dp),
   horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
   verticalArrangement = Arrangement.Center
  ) {
   Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(0.dp)
   ) {
    Box(
     modifier = Modifier
      .background(panel, MaterialTheme.shapes.extraLarge)
      .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
     Text(
      "A TREASURE OF KUANUA HYMNS",
      fontSize = 10.sp,
      fontWeight = FontWeight.Medium,
      letterSpacing = 1.5.sp,
      color = gold
     )
    }
    Spacer(Modifier.height(42.dp))
    Text(
     "A BUK NA\\nKAKAILAI",
     fontSize = 39.sp,
     lineHeight = 47.sp,
     fontFamily = FontFamily.Serif,
     fontWeight = FontWeight.SemiBold,
     letterSpacing = 1.1.sp,
     color = ivory,
     textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(Modifier.height(14.dp))
    Text(
     "K U A N U A   H Y M N S",
     fontSize = 12.sp,
     fontWeight = FontWeight.Medium,
     color = gold
    )
    Spacer(Modifier.height(32.dp))
    Row(
     horizontalArrangement = Arrangement.spacedBy(5.dp),
     verticalAlignment = androidx.compose.ui.Alignment.Bottom
    ) {
     listOf(30, 42, 54, 66, 58, 46, 34, 24).forEach { barHeight ->
      Box(
       Modifier
        .width(5.dp)
        .height(barHeight.dp)
        .background(gold.copy(alpha = 0.18f), MaterialTheme.shapes.small)
      )
     }
    }
    Spacer(Modifier.height(28.dp))
    HorizontalDivider(
     modifier = Modifier.fillMaxWidth(0.82f),
     color = gold.copy(alpha = 0.58f),
     thickness = 0.8.dp
    )
    Spacer(Modifier.height(28.dp))
    Text(
     "Welcome",
     fontSize = 27.sp,
     fontFamily = FontFamily.Serif,
     fontWeight = FontWeight.Medium,
     color = ivory
    )
    Spacer(Modifier.height(12.dp))
    Text(
     "A place to read, revisit and prepare the hymns that have accompanied generations in worship.",
     fontSize = 15.sp,
     lineHeight = 24.sp,
     fontWeight = FontWeight.Normal,
     color = muted,
     textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(Modifier.height(30.dp))
    Column(
     modifier = Modifier
      .fillMaxWidth()
      .background(panel.copy(alpha = 0.72f), MaterialTheme.shapes.large)
      .border(androidx.compose.foundation.BorderStroke(0.8.dp, gold.copy(alpha = 0.48f)), MaterialTheme.shapes.large)
      .padding(horizontal = 18.dp, vertical = 22.dp),
     horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
     verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
     Text(
      "ROOTED IN A 1960 EDITION",
      fontSize = 11.sp,
      fontWeight = FontWeight.Medium,
      letterSpacing = 1.4.sp,
      color = gold
     )
     Text(
      "A Buk na Tinir, ma A Buk na Kakailai ure ra Lotu, ma A Lotu na Pupunang",
      fontSize = 15.sp,
      lineHeight = 23.sp,
      fontFamily = FontFamily.Serif,
      fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
      color = ivory,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
     )
     Text(
      "250 hymns  •  Blanche Bay Dialect",
      fontSize = 12.sp,
      color = gold.copy(alpha = 0.92f),
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
     )
    }
   }
   Button(
    onClick = onOpen,
    modifier = Modifier.fillMaxWidth().padding(top = 34.dp),
    shape = MaterialTheme.shapes.extraLarge,
    colors = ButtonDefaults.buttonColors(
     containerColor = gold,
     contentColor = Color(0xFF191A1C)
    ),
    contentPadding = PaddingValues(vertical = 17.dp)
   ) {
    Text(
     "Open Hymnal",
     fontSize = 18.sp,
     fontWeight = FontWeight.SemiBold,
     letterSpacing = 0.1.sp
    )
   }
  }
 }
}
