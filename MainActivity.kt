package ir.modirmali.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

private val Teal = Color(0xFF147D70)
private val Ink = Color(0xFF1F2937)
private val Muted = Color(0xFF64748B)
private val Page = Color(0xFFF5F7FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Teal, background = Page)) { FinanceApp(this) } }
    }
}

data class BankSms(val id: Long, val address: String, val body: String, val timestamp: Long, val amountToman: Long?, val type: String, val suggestedCategory: String, val isFinancial: Boolean)
data class FinanceTransaction(val key: String, val date: String, val type: String, val category: String, val amount: Long, val note: String)

private val expenseCategories = listOf("خوراک و سوپرمارکت", "رستوران و کافه", "فرزند", "همسر", "هزینه شخصی", "خودرو و سوخت", "درمان و دارو", "قبوض و خانه", "قسط وام", "کارمزد و جریمه وام", "خرید آنلاین", "سفر و تفریح", "سایر هزینه‌ها")
private val incomeCategories = listOf("حقوق", "نظام مهندسی", "کار فنی بیرون", "پاداش و اضافه‌کاری", "واریز شخصی/انتقال بین حساب‌ها", "سود و سرمایه‌گذاری", "سایر درآمدها")

@Composable
private fun FinanceApp(context: Context) {
    var smsPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) }
    var messages by remember { mutableStateOf(emptyList<BankSms>()) }
    var transactions by remember { mutableStateOf(loadTransactions(context)) }
    var tab by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("برای واردکردن پیامک‌های بانکی، اجازه خواندن پیامک را بده.") }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsPermission = granted
        if (granted) { messages = readSms(context); status = "${messages.size} پیامک بررسی شد. پیامک‌های مالی احتمالی را تأیید کن." }
        else status = "مجوز داده نشد؛ می‌توانی تراکنش‌ها را دستی ثبت کنی."
    }
    fun refreshSms() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            messages = readSms(context); status = "${messages.size} پیامک بررسی شد."
        } else permissionLauncher.launch(Manifest.permission.READ_SMS)
    }
    LaunchedEffect(Unit) { if (smsPermission) { messages = readSms(context); status = "${messages.size} پیامک بارگذاری شد." } }

    Scaffold(containerColor = Page, topBar = {
        Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("مدیر مالی شخصی", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("ثبت سریع پیامک‌های مالی · ذخیره روی گوشی", color = Muted, fontSize = 12.sp)
        }
    }, bottomBar = {
        NavigationBar(containerColor = Color.White) {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("✉") }, label = { Text("پیامک‌ها") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("✓") }, label = { Text("تراکنش‌ها") })
            NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("ⓘ") }, label = { Text("حریم خصوصی") })
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> SmsScreen(messages, transactions, status, onRefresh = { refreshSms() }, onSave = { sms, category, amount, type ->
                    val txn = FinanceTransaction("sms:${sms.id}", jalaliDate(sms.timestamp), type, category, amount, "پیامک از ${sms.address}")
                    if (transactions.none { it.key == txn.key }) {
                        transactions = listOf(txn) + transactions; saveTransactions(context, transactions)
                        status = "تراکنش ثبت شد."
                    } else status = "این پیامک قبلاً ثبت شده است."
                })
                1 -> TransactionsScreen(transactions, onDelete = { item -> transactions = transactions.filterNot { it.key == item.key }; saveTransactions(context, transactions) })
                else -> PrivacyScreen(smsPermission, onPermission = { permissionLauncher.launch(Manifest.permission.READ_SMS) })
            }
        }
    }
}

@Composable
private fun SmsScreen(messages: List<BankSms>, transactions: List<FinanceTransaction>, status: String, onRefresh: () -> Unit, onSave: (BankSms, String, Long, String) -> Unit) {
    val done = remember(transactions) { transactions.map { it.key }.toSet() }
    val candidates = remember(messages) { messages.filter { it.isFinancial } }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("پیامک‌های مالی", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("${candidates.size} پیامک احتمالی · ${done.size} مورد ثبت‌شده", color = Muted, fontSize = 12.sp)
            }
            Button(onClick = onRefresh, shape = RoundedCornerShape(12.dp)) { Text("خواندن پیامک‌ها") }
        }
        Spacer(Modifier.height(8.dp))
        Text(status, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        if (messages.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(18.dp)) {
                Text("هنوز پیامکی بارگذاری نشده است.", fontWeight = FontWeight.SemiBold)
                Text("روی «خواندن پیامک‌ها» بزن و در صورت درخواست، مجوز را تأیید کن.", color = Muted, fontSize = 13.sp)
            } }
        } else if (candidates.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Text("پیامک مالی مطمئنی پیدا نشد. تشخیص خودکار ممکن است بعضی قالب‌های بانکی را نشناسد.", Modifier.padding(18.dp), color = Muted) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(candidates, key = { it.id }) { sms ->
                    SmsCard(sms, alreadySaved = "sms:${sms.id}" in done, onSave = onSave)
                }
            }
        }
    }
}

@Composable
private fun SmsCard(sms: BankSms, alreadySaved: Boolean, onSave: (BankSms, String, Long, String) -> Unit) {
    var category by remember(sms.id) { mutableStateOf(sms.suggestedCategory) }
    var type by remember(sms.id) { mutableStateOf(sms.type) }
    var amountText by remember(sms.id) { mutableStateOf(sms.amountToman?.toString() ?: "") }
    var expanded by remember { mutableStateOf(false) }
    val categories = if (type == "درآمد") incomeCategories else expenseCategories
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sms.address.ifBlank { "فرستنده نامشخص" }, fontWeight = FontWeight.Bold, color = Ink)
                    Text(jalaliDate(sms.timestamp), color = Muted, fontSize = 11.sp)
                }
                AssistChip(onClick = { type = if (type == "هزینه") "درآمد" else "هزینه"; category = if (type == "درآمد") incomeCategories.first() else expenseCategories.first() }, label = { Text(type) })
            }
            Text(sms.body.take(260), color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() || c == ',' || c == '٬' || c == ' ' } }, label = { Text("مبلغ به تومان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("دسته‌بندی: $category") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                    categories.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { category = item; expanded = false }) }
                }
            }
            Button(onClick = {
                val amount = parseAmount(amountText)
                if (amount != null && amount > 0) onSave(sms, category, amount, type)
            }, enabled = !alreadySaved && parseAmount(amountText)?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(if (alreadySaved) "ثبت شده ✓" else "ثبت تراکنش با یک لمس")
            }
        }
    }
}

@Composable
private fun TransactionsScreen(transactions: List<FinanceTransaction>, onDelete: (FinanceTransaction) -> Unit) {
    val income = transactions.filter { it.type == "درآمد" }.sumOf { it.amount }
    val expense = transactions.filter { it.type == "هزینه" }.sumOf { it.amount }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("تراکنش‌های ثبت‌شده", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryBox("درآمد", income, Modifier.weight(1f), Color(0xFFE5F5EE))
            SummaryBox("هزینه", expense, Modifier.weight(1f), Color(0xFFFFECE8))
        }
        Spacer(Modifier.height(10.dp))
        if (transactions.isEmpty()) Text("هنوز تراکنشی ثبت نشده است.", color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transactions, key = { it.key }) { t ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.category, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("${t.date} · ${t.type}", color = Muted, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${formatMoney(t.amount)} تومان", fontWeight = FontWeight.Bold, color = if (t.type == "درآمد") Teal else Ink)
                            TextButton(onClick = { onDelete(t) }) { Text("حذف") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBox(title: String, amount: Long, modifier: Modifier, tint: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = tint), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(13.dp)) { Text(title, color = Muted, fontSize = 12.sp); Text("${formatMoney(amount)} تومان", fontWeight = FontWeight.Bold, color = Ink, fontSize = 15.sp) }
    }
}

@Composable
private fun PrivacyScreen(granted: Boolean, onPermission: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("حریم خصوصی و دسترسی‌ها", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("این نسخه پیامک‌های صندوق ورودی را فقط پس از دریافت مجوز READ_SMS می‌خواند. داده‌های تراکنش در حافظه محلی برنامه نگهداری می‌شود و این نسخه هیچ سرور یا ارسال خودکاری به اینترنت ندارد.", color = Ink, lineHeight = 22.sp)
        Text("مجوز خواندن پیامک: ${if (granted) "فعال" else "غیرفعال"}", fontWeight = FontWeight.SemiBold, color = if (granted) Teal else Color(0xFFB45309))
        if (!granted) Button(onClick = onPermission) { Text("درخواست مجوز پیامک") }
        Text("مهم: قبل از ثبت، مبلغ و دسته‌بندی را بررسی کن. انتقال وجه بین حساب‌های خودت نباید به‌عنوان درآمد یا هزینه واقعی شمرده شود. برای انتشار در Google Play باید شرایط جاری سیاست مجوزهای SMS و افشای حریم خصوصی بررسی شود.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

private fun readSms(context: Context): List<BankSms> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val result = mutableListOf<BankSms>()
    val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
    val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
    try {
        context.contentResolver.query(uri, projection, null, null, "${Telephony.Sms.DATE} DESC")?.use { cursor: Cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            var scanned = 0
            while (cursor.moveToNext() && scanned < 500) {
                scanned++
                val body = cursor.getString(bodyCol) ?: continue
                val address = cursor.getString(addressCol) ?: ""
                val parsed = parseFinancialSms(body) ?: continue
                result.add(BankSms(cursor.getLong(idCol), address, body, cursor.getLong(dateCol), parsed.first, parsed.second, parsed.third, true))
            }
        }
    } catch (_: SecurityException) { }
    return result
}

private fun parseFinancialSms(body: String): Triple<Long?, String, String>? {
    val normalized = normalizeDigits(body)
    val lower = normalized.lowercase(Locale.ROOT)
    val keywords = listOf("برداشت", "خرید", "واریز", "انتقال", "مانده", "تراکنش", "withdraw", "purchase", "deposit", "transfer", "debit", "credit", "مبلغ")
    if (keywords.none { lower.contains(it) }) return null
    val hasIncome = listOf("واریز", "دریافت", "deposit", "credit", "بستانکار").any { lower.contains(it) }
    val hasExpense = listOf("برداشت", "خرید", "پرداخت", "withdraw", "purchase", "debit", "کسر").any { lower.contains(it) }
    if (!hasIncome && !hasExpense) return null
    val type = if (hasIncome && !hasExpense) "درآمد" else "هزینه"
    val patterns = listOf(
        Pattern.compile("(?:مبلغ|به مبلغ|مبلغ تراکنش)\\s*[:：]?\\s*([0-9][0-9,٬.]*)\\s*(ریال|تومان)?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9][0-9,٬.]*)\\s*(ریال|تومان)", Pattern.CASE_INSENSITIVE)
    )
    var amount: Long? = null
    var unit = ""
    for (pattern in patterns) {
        val matcher = pattern.matcher(normalized)
        if (matcher.find()) {
            val n = matcher.group(1)?.replace(",", "")?.replace("٬", "")?.replace(".", "")?.toLongOrNull()
            unit = matcher.group(2) ?: ""
            if (n != null && n > 0) { amount = if (unit == "ریال") n / 10 else n; break }
        }
    }
    val category = if (type == "درآمد") {
        if (lower.contains("حقوق")) "حقوق" else "واریز شخصی/انتقال بین حساب‌ها"
    } else when {
        lower.contains("قسط") || lower.contains("وام") || lower.contains("تسهیلات") -> "قسط وام"
        lower.contains("کارمزد") || lower.contains("جریمه") -> "کارمزد و جریمه وام"
        lower.contains("سوخت") || lower.contains("پمپ بنزین") -> "خودرو و سوخت"
        lower.contains("خرید اینترنتی") || lower.contains("درگاه") -> "خرید آنلاین"
        else -> "سایر هزینه‌ها"
    }
    return Triple(amount, type, category)
}

private fun parseAmount(s: String): Long? = normalizeDigits(s).replace(",", "").replace("٬", "").replace(" ", "").toLongOrNull()
private fun normalizeDigits(s: String): String = s.map { c -> when (c) { in '۰'..'۹' -> ('0'.code + (c.code - '۰'.code)).toChar(); in '٠'..'٩' -> ('0'.code + (c.code - '٠'.code)).toChar(); else -> c } }.joinToString("")
private fun formatMoney(value: Long): String = NumberFormat.getNumberInstance(Locale("fa", "IR")).format(value)

private fun loadTransactions(context: Context): List<FinanceTransaction> {
    val arr = try { JSONArray(context.getSharedPreferences("finance_local", Context.MODE_PRIVATE).getString("transactions", "[]")) } catch (_: Exception) { JSONArray() }
    val out = mutableListOf<FinanceTransaction>()
    for (i in 0 until arr.length()) try {
        val o = arr.getJSONObject(i); out.add(FinanceTransaction(o.optString("key"), o.optString("date"), o.optString("type"), o.optString("category"), o.optLong("amount"), o.optString("note")))
    } catch (_: Exception) { }
    return out
}
private fun saveTransactions(context: Context, transactions: List<FinanceTransaction>) {
    val arr = JSONArray()
    transactions.forEach { t -> arr.put(JSONObject().put("key", t.key).put("date", t.date).put("type", t.type).put("category", t.category).put("amount", t.amount).put("note", t.note)) }
    context.getSharedPreferences("finance_local", Context.MODE_PRIVATE).edit().putString("transactions", arr.toString()).apply()
}

// Gregorian timestamp to Jalali date; dates are stored/displayed as Persian calendar strings.
private fun jalaliDate(timestamp: Long): String {
    val cal = java.util.Calendar.getInstance(); cal.time = Date(timestamp)
    val gy = cal.get(java.util.Calendar.YEAR); val gm = cal.get(java.util.Calendar.MONTH) + 1; val gd = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val gdm = intArrayOf(0,31,if ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0) 29 else 28,31,30,31,30,31,31,30,31,30,31)
    val gy2 = gy - 1600; val gm2 = gm - 1; val gd2 = gd - 1
    var gdn = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
    for (i in 0 until gm2) gdn += gdm[i + 1]
    gdn += gd2
    var jdn = gdn - 79; val jnp = jdn / 12053; jdn %= 12053
    var jy = 979 + 33 * jnp + 4 * (jdn / 1461); jdn %= 1461
    if (jdn >= 366) { jy += (jdn - 1) / 365; jdn = (jdn - 1) % 365 }
    val jm: Int; val jd: Int
    if (jdn < 186) { jm = 1 + jdn / 31; jd = 1 + jdn % 31 } else { jm = 7 + (jdn - 186) / 30; jd = 1 + (jdn - 186) % 30 }
    return "%04d/%02d/%02d".format(Locale.ROOT, jy, jm, jd).map { c -> if (c in '0'..'9') ('۰'.code + c.code - '0'.code).toChar() else c }.joinToString("")
}
