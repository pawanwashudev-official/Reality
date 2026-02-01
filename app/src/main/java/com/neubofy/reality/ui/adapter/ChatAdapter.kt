package com.neubofy.reality.ui.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.neubofy.reality.R
import com.neubofy.reality.data.model.ChatMessage
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.widget.HorizontalScrollView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.neubofy.reality.utils.ThemeManager

class ChatAdapter(private val messages: MutableList<ChatMessage>, private val userName: String) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private var markwon: Markwon? = null
    private var tableMarkwon: Markwon? = null
    
    // STREAMING: Direct reference to the TextView being streamed to
    // This allows us to update text without triggering adapter rebind
    private var streamingTextView: TextView? = null
    private var isStreaming = false

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card_message)
        val container: LinearLayout = view.findViewById(R.id.container_message)
        val senderText: TextView = view.findViewById(R.id.tv_sender)
        val cardContainer: ConstraintLayout = view.findViewById(R.id.card_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    // ... (getMarkwon helper methods remain same)

    private fun getMarkwon(context: android.content.Context): Markwon {
        if (markwon == null) {
            val density = context.resources.displayMetrics.density
            val colorOutlineVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, android.graphics.Color.LTGRAY)
            val colorSurfaceHigh = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, android.graphics.Color.LTGRAY)
            val colorSurfaceLow = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerLow, android.graphics.Color.TRANSPARENT)

            val tableTheme = io.noties.markwon.ext.tables.TableTheme.Builder()
                .tableBorderColor(colorOutlineVariant)
                .tableBorderWidth((1 * density).toInt())
                .tableCellPadding((8 * density).toInt())
                .tableHeaderRowBackgroundColor(colorSurfaceHigh)
                .tableOddRowBackgroundColor(android.graphics.Color.TRANSPARENT)
                .tableEvenRowBackgroundColor(colorSurfaceLow)
                .build()

            markwon = Markwon.builder(context)
                .usePlugin(TablePlugin.create(tableTheme))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(CoilImagesPlugin.create(context))
                .build()
        }
        return markwon!!
    }

    private fun getTableMarkwon(context: android.content.Context): Markwon {
        if (tableMarkwon == null) {
            val density = context.resources.displayMetrics.density
            val tableTheme = io.noties.markwon.ext.tables.TableTheme.Builder()
                .tableBorderColor(android.graphics.Color.parseColor("#9E9E9E")) 
                .tableBorderWidth((1 * density).toInt())
                .tableCellPadding((8 * density).toInt())
                .tableHeaderRowBackgroundColor(android.graphics.Color.parseColor("#F5F5F5")) 
                .tableOddRowBackgroundColor(android.graphics.Color.WHITE)
                .tableEvenRowBackgroundColor(android.graphics.Color.parseColor("#FAFAFA")) 
                .build()

            tableMarkwon = Markwon.builder(context)
                .usePlugin(TablePlugin.create(tableTheme))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(HtmlPlugin.create())
                .build()
        }
        return tableMarkwon!!
    }

    enum class SegmentType { TEXT, TABLE, IMAGE }
    data class Segment(val content: String, val type: SegmentType, val extra: String? = null)

    private fun splitMarkdown(content: String): List<Segment> {
        val lines = content.split("\n")
        val initialSegments = mutableListOf<Pair<String, Boolean>>()
        if (lines.isEmpty()) return emptyList()

        val isTableLine = { line: String -> line.contains("|") }
        val isSeparator = { line: String -> line.contains("|") && line.contains("---") }

        var i = 0
        while (i < lines.size) {
            var j = i
            var hasSeparator = false
            while (j < lines.size && isTableLine(lines[j])) {
                if (isSeparator(lines[j])) hasSeparator = true
                j++
            }

            if (hasSeparator && j > i) {
                val tableText = lines.subList(i, j).joinToString("\n")
                initialSegments.add(tableText to true)
                i = j
            } else {
                val textStart = i
                while (i < lines.size) {
                    var nextHasSeparator = false
                    var k = i
                    while (k < lines.size && isTableLine(lines[k])) {
                        if (isSeparator(lines[k])) { nextHasSeparator = true; break }
                        k++
                    }
                    if (nextHasSeparator) break
                    i++
                }
                if (i > textStart) {
                    val text = lines.subList(textStart, i).joinToString("\n")
                    if (text.isNotBlank()) initialSegments.add(text to false)
                }
            }
        }

        // Now split non-table segments by images
        val finalSegments = mutableListOf<Segment>()
        initialSegments.forEach { (text, isTable) ->
            if (isTable) {
                finalSegments.add(Segment(text, SegmentType.TABLE))
            } else {
                // Regex to find ![Alt](URL)
                val imageRegex = Regex("!\\[(.*?)]\\((.*?)\\)")
                var lastIdx = 0
                imageRegex.findAll(text).forEach { match ->
                    // Add text before image
                    val before = text.substring(lastIdx, match.range.first).trim()
                    if (before.isNotEmpty()) {
                        finalSegments.add(Segment(before, SegmentType.TEXT))
                    }
                    
                    // Add image
                    val alt = match.groupValues[1]
                    val url = match.groupValues[2]
                    finalSegments.add(Segment(url, SegmentType.IMAGE, alt))
                    
                    lastIdx = match.range.last + 1
                }
                
                // Add remaining text
                val after = text.substring(lastIdx).trim()
                if (after.isNotEmpty()) {
                    finalSegments.add(Segment(after, SegmentType.TEXT))
                }
            }
        }
        
        return finalSegments
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density
        val mwNormal = getMarkwon(context)
        val mwTable = getTableMarkwon(context)
        
        // --- NAME BINDING ---
        holder.senderText.visibility = View.VISIBLE
        if (msg.isUser) {
            holder.senderText.text = userName
            holder.senderText.gravity = Gravity.END
        } else {
            holder.senderText.text = "Reality AI" // Or Model Name if available
            holder.senderText.gravity = Gravity.START
        }
        // Align name text view container if needed, currently generic match_parent usually
        // assuming senderText is width=match_parent in xml
        
        val screenWidth = context.resources.displayMetrics.widthPixels
        val marginSmall = (8 * density).toInt() 
        val textMaxWidth = (screenWidth * 0.88).toInt() 

        // Colors
        val colorOnSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK)
        val colorSurfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceVariant, android.graphics.Color.LTGRAY)
        val colorPrimary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, android.graphics.Color.BLUE)
        val colorOutline = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, android.graphics.Color.GRAY)

        holder.container.removeAllViews()
        val segments = splitMarkdown(msg.message)
        
        segments.forEach { segment ->
            when (segment.type) {
                SegmentType.TABLE -> {
                    val content = segment.content
                    // Dynamic Width Logic: Force table container to be at least bubble width
                    val bubbleWidthLimit = (context.resources.displayMetrics.widthPixels * 0.88).toInt()
                    val requiredWidth = calculateRequiredTableWidth(content, density)

                    // PREMIUM SHEET-STYLE CONTAINER
                    val sheetContainer = LinearLayout(context).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = (12 * density).toInt()
                            bottomMargin = (12 * density).toInt()
                        }
                        orientation = LinearLayout.VERTICAL
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.WHITE)
                            setStroke((1 * density).toInt(), android.graphics.Color.parseColor("#E0E0E0"))
                            cornerRadius = (8 * density)
                        }
                        elevation = 2f
                        setPadding(0, 0, 0, 0)
                        minimumWidth = bubbleWidthLimit
                    }

                    // Table Header (Copy CSV Button)
                    val header = android.widget.RelativeLayout(context).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.parseColor("#F5F5F5"))
                            cornerRadii = floatArrayOf(8*density, 8*density, 8*density, 8*density, 0f, 0f, 0f, 0f)
                        }
                    }
                    
                    val icon = ImageView(context).apply {
                        setImageResource(R.drawable.baseline_content_copy_24) 
                        imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.DKGRAY)
                        layoutParams = android.widget.RelativeLayout.LayoutParams((18 * density).toInt(), (18 * density).toInt()).apply {
                            addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                            addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                        }
                    }
                    
                    val label = TextView(context).apply {
                        text = "Spreadsheet"
                        textSize = 11f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.GRAY)
                        layoutParams = android.widget.RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
                            addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                        }
                    }
                    
                    header.setOnClickListener {
                         val csv = convertTableToCsv(content)
                         val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                         val clip = android.content.ClipData.newPlainText("Table CSV", csv)
                         clipboard.setPrimaryClip(clip)
                         android.widget.Toast.makeText(context, "Table copied as CSV", android.widget.Toast.LENGTH_SHORT).show()
                    }

                    header.addView(label)
                    header.addView(icon)
                    sheetContainer.addView(header)

                    val scroll = HorizontalScrollView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        isFillViewport = true 
                        isHorizontalScrollBarEnabled = false
                    }
                    
                    val tableTv = TextView(context).apply {
                        layoutParams = android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(android.graphics.Color.BLACK)
                        minimumWidth = maxOf(requiredWidth, bubbleWidthLimit)
                    }
                    scroll.addView(tableTv)
                    sheetContainer.addView(scroll)
                    
                    holder.container.addView(sheetContainer)
                    mwTable.setMarkdown(tableTv, content)
                }

                SegmentType.IMAGE -> {
                    val imageUrl = segment.content
                    val imgView = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = (8 * density).toInt()
                            bottomMargin = (8 * density).toInt()
                            gravity = if (msg.isUser) Gravity.END else Gravity.START
                        }
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        maxWidth = (screenWidth * 0.85).toInt() // Slightly smaller than text bubble
                        
                        // Rounded corners for the image
                        clipToOutline = true
                        outlineProvider = object : android.view.ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: android.graphics.Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, 16 * density)
                            }
                        }
                    }
                    holder.container.addView(imgView)
                    
                    // Use Coil to load
                    imgView.setOnClickListener {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))
                        try {
                            context.startActivity(browserIntent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "No app to view images", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    // Load with crossfade for premium feel
                    coil.Coil.imageLoader(context).enqueue(
                        coil.request.ImageRequest.Builder(context)
                            .data(imageUrl)
                            .target(imgView)
                            .crossfade(true)
                            .build()
                    )
                }

                SegmentType.TEXT -> {
                    val content = segment.content
                    // --- GLASSMORPHISM BUBBLE ---
                    val tv = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            gravity = if (msg.isUser) Gravity.END else Gravity.START
                            topMargin = (2 * density).toInt()
                            bottomMargin = (2 * density).toInt()
                        }
                        setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        
                        // Glassy Logic
                        var bgColor = 0
                        var strokeColor = 0
                        var textColor = 0
                        
                        if (msg.isUser) {
                            bgColor = androidx.core.graphics.ColorUtils.setAlphaComponent(colorPrimary, 40)
                            strokeColor = androidx.core.graphics.ColorUtils.setAlphaComponent(colorPrimary, 100)
                            textColor = colorOnSurface
                        } else {
                            bgColor = androidx.core.graphics.ColorUtils.setAlphaComponent(colorSurfaceVariant, 60)
                            strokeColor = androidx.core.graphics.ColorUtils.setAlphaComponent(colorOutline, 80)
                            textColor = colorOnSurface
                        }
                        
                        setTextColor(textColor)
                        maxWidth = textMaxWidth
                        
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(bgColor)
                            setStroke((1 * density).toInt(), strokeColor)
                            cornerRadius = (20 * density) 
                        }
                        
                        setOnLongClickListener {
                             val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                             val clip = android.content.ClipData.newPlainText("Chat Message", content)
                             clipboard.setPrimaryClip(clip)
                             android.widget.Toast.makeText(context, "Message copied", android.widget.Toast.LENGTH_SHORT).show()
                             true
                        }
                    }
                    holder.container.addView(tv)
                    mwNormal.setMarkdown(tv, content)
                }
            }
        }

        // CONTAINER LOGIC (Adaptive with Bias)
        val set = ConstraintSet()
        set.clone(holder.cardContainer)
        
        set.connect(R.id.card_message, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, marginSmall)
        set.connect(R.id.card_message, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, marginSmall)
        
        // ADAPTIVE LOGIC: MATCH_CONSTRAINT with WRAP vs SPREAD behavior
        // If message contains a table, force it to take full width to avoid squashing.
        val hasTable = segments.any { it.type == SegmentType.TABLE }
        
        set.constrainWidth(R.id.card_message, ConstraintSet.MATCH_CONSTRAINT)
        if (hasTable) {
            set.constrainDefaultWidth(R.id.card_message, ConstraintSet.MATCH_CONSTRAINT_SPREAD)
        } else {
            set.constrainDefaultWidth(R.id.card_message, ConstraintSet.MATCH_CONSTRAINT_WRAP)
        }
        
        // Horizontal Bias: Right (1.0) for User, Left (0.0) for AI
        set.setHorizontalBias(R.id.card_message, if (msg.isUser) 1.0f else 0.0f)
        
        set.applyTo(holder.cardContainer)

        // Apply ThemeManager bubble colors (mode-specific)
        val bubbleBg = if (msg.isUser) {
            ThemeManager.getUserBubbleBackgroundColor(context)
                ?: MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, android.graphics.Color.LTGRAY)
        } else {
            ThemeManager.getAiBubbleBackgroundColor(context)
                ?: MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, android.graphics.Color.WHITE)
        }
        holder.card.setCardBackgroundColor(bubbleBg)
        
        // Apply custom stroke color if set
        val strokeColor = ThemeManager.getCardStrokeColor(context)
        if (strokeColor != null) {
            holder.card.strokeColor = strokeColor
            holder.card.strokeWidth = (1 * density).toInt()
        } else {
            holder.card.strokeWidth = 0
        }
        holder.card.elevation = 0f
        
        val llParams = holder.card.layoutParams as ViewGroup.MarginLayoutParams
        llParams.topMargin = (2 * density).toInt()
        llParams.bottomMargin = if (msg.isUser) (16 * density).toInt() else (24 * density).toInt()

        // Ensure LayoutParams is MATCH_CONSTRAINT (0dp) for ConstraintLayout to take control
        llParams.width = 0 
        holder.card.layoutParams = llParams
    }

    // ... (rest of simple methods)

    private fun calculateRequiredTableWidth(content: String, density: Float): Int {
        val lines = content.split("\n").filter { it.contains("|") && !it.contains("---") }
        if (lines.isEmpty()) return 0
        
        // Parse rows and calculate max character length per column
        val rowCells = lines.map { line -> 
            line.trim().trim('|').split("|").map { it.trim().length }
        }
        
        val colCount = rowCells.maxOf { it.size }
        val maxColChars = IntArray(colCount)
        
        rowCells.forEach { row ->
            row.forEachIndexed { index, length ->
                if (index < colCount) {
                    maxColChars[index] = maxOf(maxColChars[index], length)
                }
            }
        }
        
        var totalWidth = 0
        maxColChars.forEach { chars ->
            // Aggressive Heuristic: ~8.5dp per character at 13sp. 
            // Min col width 105dp, Max 400dp (for large reports)
            val baseWidth = (chars * 8.5f * density).toInt()
            val colWidth = baseWidth.coerceIn((105 * density).toInt(), (400 * density).toInt())
            totalWidth += colWidth
        }
        
        // Add padding for cell margins (roughly 16dp per column)
        return totalWidth + (colCount * 16 * density).toInt()
    }

    private fun convertTableToCsv(markdown: String): String {
        return markdown.split("\n")
            .filter { it.contains("|") && !it.contains("---") }
            .joinToString("\n") { line -> 
                line.trim().trim('|')
                    .split("|")
                    .joinToString(",") { cell -> 
                        "\"${cell.trim().replace("\"", "\"\"")}\"" 
                    }
            }
    }

    override fun getItemCount() = messages.size

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun submitList(newList: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newList)
        notifyDataSetChanged()
    }

    /**
     * STREAMING SUPPORT: Updates the text of the last message in the list.
     * Used during streaming to incrementally build up the AI response.
     * STREAMING SUPPORT: Updates the text of the last message in the list.
     * During streaming: Updates TextView directly (no rebind, plain text).
     * Call finishStreaming() at the end to trigger proper Markwon render.
     */
    fun updateLastMessageText(newText: String) {
        if (messages.isEmpty()) return
        val lastIndex = messages.size - 1
        val lastMsg = messages[lastIndex]
        
        // Only update if it's an AI message (not user)
        if (!lastMsg.isUser) {
            // Update data model
            messages[lastIndex] = lastMsg.copy(message = newText)
            
            // During streaming: Update TextView directly (no flicker!)
            if (isStreaming && streamingTextView != null) {
                streamingTextView?.text = newText
            } else {
                // Not streaming yet, do full bind (first message setup)
                notifyItemChanged(lastIndex)
            }
        }
    }
    
    /**
     * Call this when starting to stream a new message.
     * Pass the RecyclerView to find and capture the streaming TextView.
     */
    fun startStreaming(recyclerView: RecyclerView) {
        isStreaming = true
        val lastIndex = messages.size - 1
        
        // Find the ViewHolder for the last item and capture its main TextView
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(lastIndex) as? ChatViewHolder
        if (viewHolder != null && viewHolder.container.childCount > 0) {
            // Get the first TextView child (usually the message text)
            for (i in 0 until viewHolder.container.childCount) {
                val child = viewHolder.container.getChildAt(i)
                if (child is TextView) {
                    streamingTextView = child
                    break
                }
            }
        }
        
        // If no TextView found, create one directly
        if (streamingTextView == null && viewHolder != null) {
            val context = recyclerView.context
            val density = context.resources.displayMetrics.density
            val colorOnSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK)
            
            val tv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setTextColor(colorOnSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                text = ""
            }
            viewHolder.container.addView(tv)
            streamingTextView = tv
        }
    }
    
    /**
     * Call this when streaming is complete.
     * Triggers a full rebind to render Markwon formatting properly.
     */
    fun finishStreaming() {
        isStreaming = false
        streamingTextView = null
        
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            // Final rebind to render markdown properly
            notifyItemChanged(lastIndex)
        }
    }

    /**
     * STREAMING SUPPORT: Appends a token/chunk to the last AI message.
     * Convenience wrapper around updateLastMessageText.
     */
    fun appendTokenToLastMessage(token: String) {
        if (messages.isEmpty()) return
        val lastIndex = messages.size - 1
        val lastMsg = messages[lastIndex]
        if (!lastMsg.isUser) {
            val newText = lastMsg.message + token
            updateLastMessageText(newText)
        }
    }
}
