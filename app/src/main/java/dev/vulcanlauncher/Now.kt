package dev.vulcanlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The Now page: Nova's feed cards that work offline (date, media controls, calendar, contacts, frequent apps).
// Weather, news and Google Discover need the network and aren't here.

fun MainActivity.granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
fun MainActivity.showNow() {
    drawer.hide()
    val pad = (16 * dp).toInt()
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, insets.top + pad, pad, insets.bottom + pad) }
    // M3 filled cards on a translucent surface.
    fun card(title: String, body: View) = list.addView(LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(m3.surfaceContainer, 24 * dp)
        setPadding(pad + pad / 4, pad, pad + pad / 4, pad + pad / 4)
        addView(text(title, 14f, m3.primary, medium = true).apply { setPadding(0, 0, 0, pad * 3 / 4) })
        addView(body)
    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = pad * 3 / 4 })
    fun text(s: CharSequence, size: Float = 15f) = text(s, size, m3.onSurface)
    fun action(label: String, onClick: () -> Unit) = LinearLayout(this).apply { addView(tonalButton(label, onClick)) }
    fun ask(permission: String, why: String) = action(why) { requestPermissions(arrayOf(permission), MainActivity.REQ_PERMISSION) }

    if (looks.on("nowClock")) list.addView(LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad / 2, pad, 0, pad * 3 / 2)
        addView(TextClock(context).apply {
            format12Hour = "h:mm"; format24Hour = "HH:mm"; textSize = 57f; setTextColor(m3.onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        })
        addView(TextClock(context).apply { format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"; textSize = 16f; setTextColor(m3.onSurfaceVariant) })
    })

    if (looks.on("nowMedia")) {
        val sessions = runCatching {
            getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(this, BadgeService::class.java))
        }
        val c = sessions.getOrNull()?.firstOrNull()
        card("Media", when {
            sessions.isFailure -> action("Turn on notification access") {
                startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            c == null -> text("Nothing playing")
            else -> LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                val md = c.metadata
                md?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { art ->
                    addView(ImageView(context).apply {
                        setImageBitmap(art); scaleType = ImageView.ScaleType.CENTER_CROP
                        background = rounded(m3.surfaceHigh, 12 * dp); clipToOutline = true
                    },
                        LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt()).apply { marginEnd = pad })
                }
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(md?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Playing", 16f).apply { maxLines = 1 })
                    addView(text(md?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "", 14f).apply { maxLines = 1; setTextColor(m3.onSurfaceVariant) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
                addView(iconButton("prev", m3.onSurfaceVariant) { c.transportControls.skipToPrevious() })
                // The play/pause button is the card's filled primary action.
                addView(ImageView(context).apply {
                    setImageDrawable(symbol(if (playing) "pause" else "play", m3.onPrimary))
                    scaleType = ImageView.ScaleType.CENTER
                    background = pressable(rounded(m3.primary, 24 * dp), 24 * dp, m3.onPrimary)
                    setOnClickListener { if (playing) c.transportControls.pause() else c.transportControls.play(); closeOverlay(); showNow() }
                }, LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()))
                addView(iconButton("next", m3.onSurfaceVariant) { c.transportControls.skipToNext() })
                setOnClickListener { c.sessionActivity?.let { runCatching { it.send() } } }
            }
        })
    }

    if (looks.on("nowCalendar")) card("Upcoming", if (!granted(Manifest.permission.READ_CALENDAR)) ask(Manifest.permission.READ_CALENDAR, "Allow calendar access")
    else LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now); ContentUris.appendId(it, now + 7 * 86_400_000L)
        }.build()
        val fmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        // All-day events are stored as UTC midnights, so they're compared and shown in UTC.
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val todayUtc = java.util.Calendar.getInstance().let { l ->
            java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear(); set(l.get(java.util.Calendar.YEAR), l.get(java.util.Calendar.MONTH), l.get(java.util.Calendar.DAY_OF_MONTH))
            }.timeInMillis
        }
        runCatching {
            contentResolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
                null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && childCount < 5) {
                    val id = c.getLong(0)
                    val allDay = c.getInt(4) == 1
                    if (allDay && c.getLong(3) <= todayUtc) continue // ended before today
                    val whenText = if (allDay) "${dayFmt.format(Date(c.getLong(2)))} all day" else fmt.format(Date(c.getLong(2)))
                    addView(LinearLayout(context).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, pad / 3, 0, pad / 3)
                        background = pressable(null, 8 * dp)
                        addView(View(context).apply { background = rounded(m3.primary, 2 * dp) }, LinearLayout.LayoutParams((4 * dp).toInt(), (36 * dp).toInt()).apply { marginEnd = pad * 3 / 4 })
                        addView(LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(text(c.getString(1) ?: "", 16f).apply { maxLines = 1 })
                            addView(text(whenText, 14f).apply { setTextColor(m3.onSurfaceVariant) })
                        })
                        setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))) } }
                    })
                }
            }
        }
        if (childCount == 0) addView(text("No events in the next 7 days", 14f).apply { setTextColor(m3.onSurfaceVariant) })
    })

    if (looks.on("nowContacts")) card("Favourites", if (!granted(Manifest.permission.READ_CONTACTS)) ask(Manifest.permission.READ_CONTACTS, "Allow contacts access")
    else HorizontalScrollView(this).apply {
        val row = LinearLayout(context)
        addView(row)
        runCatching {
            contentResolver.query(ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
                "${ContactsContract.Contacts.STARRED}=1", null, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)?.use { c ->
                while (c.moveToNext() && row.childCount < 12) {
                    val look = ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
                    row.addView(contactView(c.getString(2) ?: "", c.getString(3)) { startActivity(Intent(Intent.ACTION_VIEW, look)) })
                }
            }
        }
        if (row.childCount == 0) row.addView(text("Star contacts in your contacts app to see them here", 14f).apply { setTextColor(m3.onSurfaceVariant) })
    })

    if (looks.on("nowApps")) {
        val counts = launches()
        val top = apps.values.filter { counts.optInt(it.key) > 0 }.sortedByDescending { counts.optInt(it.key) }.take(8)
        if (top.isNotEmpty()) card("Frequently used", LinearLayout(this).apply {
            val st = style("").let { IconStyle(it.sizePx * 3 / 4, false, it.labelSize, panelText, false, false) }
            top.forEach { app -> addView(IconView(context, label(app), icon(app), st, badgeStyle(), app.pkg).apply { setOnClickListener { v -> launch(app, v) } },
                LinearLayout.LayoutParams(0, (st.sizePx + 16 * dp).toInt(), 1f)) }
        })
    }

    var downX = 0f
    val panel = FrameLayout(this).apply {
        setBackgroundColor(m3.surface and 0xFFFFFF or 0xE0000000.toInt())
        isClickable = true
        addView(ScrollView(context).apply { addView(list) })
        // Swipe left closes the page, mirroring the swipe right that opened it.
        setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) downX = ev.x
            if (ev.actionMasked == MotionEvent.ACTION_MOVE && downX - ev.x > 120 * dp) { closeOverlay(); true } else false
        }
    }
    showOverlay(panel)
}

private fun MainActivity.contactView(name: String, photo: String?, onClick: () -> Unit) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    val s = (52 * dp).toInt()
    setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
    addView(if (photo != null) ImageView(context).apply {
        setImageURI(Uri.parse(photo))
        clipToOutline = true
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(m3.primaryContainer) }
    } else TextView(context).apply {
        text = name.take(1).uppercase()
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(m3.onPrimaryContainer)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(m3.primaryContainer) }
    }, LinearLayout.LayoutParams(s, s))
    addView(text(name.substringBefore(" "), 12f, m3.onSurfaceVariant).apply { maxLines = 1; setPadding(0, (6 * dp).toInt(), 0, 0) })
    setOnClickListener { onClick() }
}

// ---- Glance: the date and one line of status above the first home page ----

/** The glance strip: the date (tap opens the calendar) and a status line filled by [updateGlance]. Long-press is the home menu. */
fun MainActivity.glanceView() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (4 * dp).toInt())
    val color = looks.color("labelColor")
    fun TextView.look() = apply { if (looks.on("labelShadow")) setShadowLayer(4 * dp, 0f, dp, 0x66000000) }
    addView(TextClock(context).apply {
        format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"
        textSize = 22f
        setTextColor(color)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        look()
        setOnClickListener { runCatching { startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        setOnLongClickListener { homeMenu(); true }
    }, LinearLayout.LayoutParams(-2, -2))
    addView(TextView(context).apply {
        textSize = 15f
        setTextColor(color and 0xFFFFFF or 0xD9000000.toInt())
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, (2 * dp).toInt(), 0, 0)
        look()
        setOnLongClickListener { homeMenu(); true }
    }, LinearLayout.LayoutParams(-2, -2))
}

/** Refreshes the glance status line: what's playing, else the next event today, else charging, else nothing. */
fun MainActivity.updateGlance() {
    val line = glance?.getChildAt(1) as? TextView ?: return
    val (text, tap) = glanceStatus() ?: (null to null)
    line.text = text
    line.visibility = if (text == null) View.GONE else View.VISIBLE
    line.setOnClickListener(tap?.let { f -> View.OnClickListener { f() } })
}

private fun MainActivity.glanceStatus(): Pair<String, (() -> Unit)?>? {
    // Media sessions are readable only with notification access (the badge service).
    runCatching {
        getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(this, BadgeService::class.java))
            .firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
    }.getOrNull()?.let { c ->
        val md = c.metadata
        val what = listOfNotNull(md?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE), md?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST))
            .filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "Playing" }
        return "♪  $what" to { c.sessionActivity?.let { runCatching { it.send() } }; Unit }
    }
    if (granted(Manifest.permission.READ_CALENDAR)) runCatching {
        val now = System.currentTimeMillis()
        val endOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59); set(java.util.Calendar.SECOND, 59)
        }.timeInMillis
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, now); ContentUris.appendId(it, endOfDay) }.build()
        contentResolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
            "${CalendarContract.Instances.ALL_DAY}=0 AND ${CalendarContract.Instances.END}>?", arrayOf(now.toString()),
            "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                val at = if (c.getLong(2) <= now) "Now" else android.text.format.DateFormat.getTimeFormat(this).format(Date(c.getLong(2)))
                return "$at  ${c.getString(1) ?: ""}" to {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    Unit
                }
            }
        }
    }
    val b = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) * 100 / maxOf(1, b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100))
    return when (b.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)) {
        android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "Charging · $level%" to null
        android.os.BatteryManager.BATTERY_STATUS_FULL -> "Charged" to null
        else -> null
    }
}
