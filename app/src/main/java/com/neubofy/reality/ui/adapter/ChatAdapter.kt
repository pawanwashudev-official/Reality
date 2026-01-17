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
        
        if (markwon == null) {
            markwon = Markwon.builder(context)
                .usePlugin(TablePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(CoilImagesPlugin.create(context))
                .build()
        }
        
        val markdown = markwon!!.toMarkdown(msg.message)
        
        if (msg.isAnimating && !msg.isUser) {
            // Use findViewTreeLifecycleOwner extension
            val owner = holder.itemView.findViewTreeLifecycleOwner()
            if (owner != null) {
                val textLength = markdown.length
                holder.text.text = ""
                
                // Use lifecycleScope from owner
                owner.lifecycleScope.launch {
                    try {
                        for (i in 1..textLength) {
                             if (!msg.isAnimating) break 
                             holder.text.text = markdown.subSequence(0, i)
                             delay(5)
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
            markwon?.setMarkdown(holder.text, msg.message)
        }
        
        // Check if layout params are LinearLayout.LayoutParams (because item root is LinearLayout)
        // Actually, item_chat_message.xml root IS LinearLayout.
        // holder.card is child of that LinearLayout.
        // So params IS LinearLayout.LayoutParams (if we cast it correctly).
        // BUT hold on: holder.itemView is the LinearLayout. holder.card IS the child.
        // So params is LinearLayout.LayoutParams.
        
        val llParams = holder.card.layoutParams as LinearLayout.LayoutParams
        
        val colorOnSurface = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface)
        val colorPrimaryContainer = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimaryContainer)
        val colorOnPrimaryContainer = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnPrimaryContainer)

        if (msg.isUser) {
             llParams.setMargins(100, 8, 16, 8) 
             llParams.gravity = Gravity.END
             
             // User: Bubble (Primary Container)
             holder.card.setCardBackgroundColor(colorPrimaryContainer)
             holder.card.elevation = 0f
             holder.text.setTextColor(colorOnPrimaryContainer)
             holder.card.strokeWidth = 0
        } else {
             llParams.setMargins(16, 8, 100, 8)
             llParams.gravity = Gravity.START
             
             // AI: No Bubble (Transparent) or Surface
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
