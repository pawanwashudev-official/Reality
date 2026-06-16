package com.neubofy.reality.utils

import android.content.Context
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Utility to convert Markdown content to styled PDF
 */
object PdfGenerator {
    
    /**
     * Generates a PDF from markdown content
     * @param context Android context
     * @param markdownContent The markdown text to convert
     * @param title Title for the PDF document
     * @return ByteArray of the PDF file
     */
    fun generatePdfFromMarkdown(
        context: Context,
        markdownContent: String,
        title: String
    ): ByteArray {
        // Convert Markdown to HTML using Markwon
        val html = markdownToHtml(context, markdownContent, title)
        
        // Convert HTML to PDF using iText
        return htmlToPdf(html)
    }
    
    /**
     * Generates and saves PDF to a temporary file
     * @return The temp file containing the PDF
     */
    fun generatePdfFile(
        context: Context,
        markdownContent: String,
        title: String,
        fileName: String
    ): File {
        val pdfBytes = generatePdfFromMarkdown(context, markdownContent, title)
        val file = File(context.cacheDir, "$fileName.pdf")
        file.writeBytes(pdfBytes)
        return file
    }
    
    private fun markdownToHtml(context: Context, markdown: String, title: String): String {
        // Build Markwon with plugins
        val markwon = Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .build()
        
        val spanned = markwon.toMarkdown(markdown)
        
        // Convert spanned to basic HTML (Markwon doesn't have direct HTML export,
        // so we'll manually convert common markdown to HTML)
        val htmlBody = convertMarkdownToHtml(markdown)
        
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>$title</title>
    <style>
        body {
            font-family: 'Segoe UI', Arial, sans-serif;
            font-size: 11pt;
            line-height: 1.5;
            color: #333;
            margin: 0;
            padding: 0;
        }
        h1, h2, h3 { 
            page-break-after: avoid; 
        }
        h1 { color: #00695C; border-bottom: 2px solid #00695C; padding-bottom: 10px; font-size: 24pt; }
        h2 { color: #004D40; margin-top: 24px; font-size: 18pt; }
        h3 { color: #00796B; font-size: 14pt; }
        table { 
            border-collapse: collapse; 
            width: 100%; 
            margin: 16px 0;
            page-break-inside: auto;
        }
        tr {
            page-break-inside: avoid;
            page-break-after: auto;
        }
        th, td { 
            border: 1px solid #ddd; 
            padding: 8px; 
            text-align: left; 
        }
        th { 
            background-color: #00695C; 
            color: white; 
        }
        tr:nth-child(even) { background-color: #f2f2f2; }
        code {
            background: #f4f4f4;
            padding: 2px 4px;
            border-radius: 4px;
            font-family: 'Consolas', monospace;
            font-size: 10pt;
        }
        pre {
            background: #f4f4f4;
            padding: 12px;
            border-radius: 8px;
            overflow-x: auto;
            page-break-inside: avoid;
        }
        blockquote {
            border-left: 4px solid #00695C;
            margin: 16px 0;
            padding-left: 16px;
            color: #666;
            font-style: italic;
        }
        ul, ol { padding-left: 24px; }
        li { margin: 4px 0; }
        .task-list-item { list-style: none; margin-left: -20px; }
        strong { color: #00695C; }
        hr { border: none; border-top: 1px solid #ddd; margin: 24px 0; }
    </style>
</head>
<body>
$htmlBody
</body>
</html>
        """.trimIndent()
    }
    
    private fun convertMarkdownToHtml(markdown: String): String {
        var html = markdown
        
        // Headers
        html = html.replace(Regex("^### (.+)$", RegexOption.MULTILINE), "<h3>$1</h3>")
        html = html.replace(Regex("^## (.+)$", RegexOption.MULTILINE), "<h2>$1</h2>")
        html = html.replace(Regex("^# (.+)$", RegexOption.MULTILINE), "<h1>$1</h1>")
        
        // Bold and Italic
        html = html.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        html = html.replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        html = html.replace(Regex("__(.+?)__"), "<strong>$1</strong>")
        html = html.replace(Regex("_(.+?)_"), "<em>$1</em>")
        
        // Strikethrough
        html = html.replace(Regex("~~(.+?)~~"), "<del>$1</del>")
        
        // Code blocks
        html = html.replace(Regex("```([\\s\\S]*?)```"), "<pre><code>$1</code></pre>")
        html = html.replace(Regex("`(.+?)`"), "<code>$1</code>")
        
        // Blockquotes
        html = html.replace(Regex("^> (.+)$", RegexOption.MULTILINE), "<blockquote>$1</blockquote>")
        
        // Horizontal rules
        html = html.replace(Regex("^---$", RegexOption.MULTILINE), "<hr>")
        html = html.replace(Regex("^\\*\\*\\*$", RegexOption.MULTILINE), "<hr>")
        
        // Task lists (before regular lists)
        html = html.replace(Regex("^- \\[x\\] (.+)$", RegexOption.MULTILINE), 
            "<li class=\"task-list-item\"><input type=\"checkbox\" checked disabled> $1</li>")
        html = html.replace(Regex("^- \\[ \\] (.+)$", RegexOption.MULTILINE), 
            "<li class=\"task-list-item\"><input type=\"checkbox\" disabled> $1</li>")
        
        // Unordered lists
        html = html.replace(Regex("^[\\-\\*] (.+)$", RegexOption.MULTILINE), "<li>$1</li>")
        
        // Ordered lists
        html = html.replace(Regex("^\\d+\\. (.+)$", RegexOption.MULTILINE), "<li class=\"ordered\">$1</li>")
        
        // Wrap consecutive <li> in <ul> or <ol>
        html = html.replace(Regex("((?:<li[^>]*>.*?</li>\\s*)+)")) { match ->
            if (match.value.contains("class=\"ordered\"")) {
                "<ol>${match.value.replace(" class=\"ordered\"", "")}</ol>"
            } else {
                "<ul>${match.value}</ul>"
            }
        }
        
        // Tables (simplified)
        val lines = html.lines().toMutableList()
        val result = StringBuilder()
        var inTable = false
        var headerDone = false
        
        for (i in lines.indices) {
            val line = lines[i].trim()
            
            if (line.startsWith("|") && line.endsWith("|")) {
                if (!inTable) {
                    result.append("<table>")
                    inTable = true
                    headerDone = false
                }
                
                // Check if separator line
                if (line.contains("---") || line.contains(":---") || line.contains("---:")) {
                    headerDone = true
                    continue
                }
                
                val cells = line.split("|").filter { it.isNotBlank() }
                if (!headerDone) {
                    result.append("<tr>")
                    cells.forEach { result.append("<th>${it.trim()}</th>") }
                    result.append("</tr>")
                } else {
                    result.append("<tr>")
                    cells.forEach { result.append("<td>${it.trim()}</td>") }
                    result.append("</tr>")
                }
            } else {
                if (inTable) {
                    result.append("</table>")
                    inTable = false
                }
                result.append(line)
            }
            result.append("\n")
        }
        
        if (inTable) result.append("</table>")
        
        // Paragraphs - wrap loose text
        html = result.toString()
        html = html.replace(Regex("^(?!<[hpuoltbd]|<li|<bl|<pre|<code|<hr|<table)(.+)$", RegexOption.MULTILINE), "<p>$1</p>")
        
        return html
    }
    
    private fun htmlToPdf(html: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val writer = PdfWriter(outputStream)
        val pdfDoc = PdfDocument(writer)
        
        HtmlConverter.convertToPdf(html.byteInputStream(), pdfDoc, null)
        
        return outputStream.toByteArray()
    }
}
