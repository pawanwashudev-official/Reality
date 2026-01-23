package com.neubofy.reality.ui.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class ChatAdapter(private val messages: MutableList<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private var markwon: Markwon? = null

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card_message)
        val text: TextView = view.findViewById(R.id.tv_message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        val context = holder.itemView.context
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        
        if (markwon == null) {
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
        
        
        // Detect complex markdown that would be broken by character-by-character typing
        val hasTable = msg.message.contains("|") && (msg.message.contains("---") || msg.message.contains("-:-"))
        val hasCodeBlock = msg.message.contains("```")
        val hasList = msg.message.contains("\n- ") || msg.message.contains("\n* ") || msg.message.contains("\n1. ")
        
        val isComplex = hasTable || hasCodeBlock || hasList
        
        val screenWidth = displayMetrics.widthPixels
        val marginSmall = (16 * density).toInt()
        val marginLarge = (60 * density).toInt()
        val maxTextWidth = screenWidth - (marginSmall + marginLarge)

        val params = holder.text.layoutParams
        if (hasTable) {
            params.width = (1200 * density).toInt() 
            holder.text.layoutParams = params
            holder.text.maxWidth = Int.MAX_VALUE
            holder.text.setHorizontallyScrolling(true) 
            holder.text.setPadding((12 * density).toInt(), (12 * density).toInt(), (40 * density).toInt(), (12 * density).toInt())
        } else {
            params.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            holder.text.layoutParams = params
            holder.text.maxWidth = maxTextWidth
            holder.text.setHorizontallyScrolling(false) 
            holder.text.setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        }
        
        // If it's complex, we MUST NOT sub-sequence the spans or it breaks Markwon table rendering
        if (isComplex) {
            msg.isAnimating = false
        }
        
        if (msg.isAnimating && !msg.isUser) {
             val owner = holder.itemView.findViewTreeLifecycleOwner()
             if (owner != null) {
                val fullMarkdown = markwon!!.toMarkdown(msg.message)
                val textLength = fullMarkdown.length
                holder.text.text = ""
                owner.lifecycleScope.launch {
                    try {
                        // For non-complex text, we can do typing animation
                        for (i in 1..textLength) {
                             if (!msg.isAnimating) break 
                             holder.text.text = fullMarkdown.subSequence(0, i)
                             delay(if (textLength > 100) 2 else 5)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        msg.isAnimating = false
                        markwon?.setMarkdown(holder.text, msg.message)
                    }
                }
            } else {
                markwon?.setMarkdown(holder.text, msg.message)
                msg.isAnimating = false
            }
        } else {
            if (hasTable) {
                // IMPORTANT: Defer markdown setting until layout has measured the new width
                holder.text.post {
                    markwon?.setMarkdown(holder.text, msg.message)
                    holder.itemView.requestLayout()
                }
            } else {
                markwon?.setMarkdown(holder.text, msg.message)
            }
        }
        
        val llParams = holder.card.layoutParams as LinearLayout.LayoutParams
        val colorOnSurface = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface)
        val colorPrimaryContainer = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimaryContainer)
        val colorOnPrimaryContainer = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnPrimaryContainer)

        if (msg.isUser) {
             llParams.setMargins(marginLarge, (8 * density).toInt(), marginSmall, (8 * density).toInt()) 
             llParams.gravity = Gravity.END
             holder.card.setCardBackgroundColor(colorPrimaryContainer)
             holder.card.elevation = 0f
             holder.text.setTextColor(colorOnPrimaryContainer)
             holder.card.strokeWidth = 0
        } else {
             if (hasTable) {
                 llParams.setMargins(marginSmall, (8 * density).toInt(), marginSmall, (8 * density).toInt())
             } else {
                 llParams.setMargins(marginSmall, (8 * density).toInt(), marginLarge, (8 * density).toInt())
             }
             llParams.gravity = Gravity.START
             holder.card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
             holder.card.elevation = 0f
             holder.card.strokeWidth = 0
             holder.text.setTextColor(colorOnSurface)
        }
        holder.card.layoutParams = llParams
    }

    override fun getItemCount() = messages.size

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }
}
