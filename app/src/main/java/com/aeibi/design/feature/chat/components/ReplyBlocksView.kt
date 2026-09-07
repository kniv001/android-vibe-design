package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aeibi.design.feature.chat.InlineRun
import com.aeibi.design.feature.chat.ReplyBlock
import com.aeibi.design.theme.spacing

/**
 * 容器方案 v0 渲染：块类型直映组件，无解析层。
 *
 * 每个块是独立组件（组件数 = 块数）——块级流式意味着每个块到达即渲染、
 * 已渲染块永不重绘（无 markdown 的"未闭合块"状态）。
 * 与 MarkdownText 同口径：渲染树确定性、无异步解析阶段。
 */
@Composable
fun ReplyBlocksView(
    blocks: List<ReplyBlock>,
    modifier: Modifier = Modifier,
    bubbleColor: androidx.compose.ui.graphics.Color,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    val spacing = MaterialTheme.spacing
    val textColor = androidx.compose.material3.contentColorFor(bubbleColor)
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        blocks.forEach { block ->
            when (block) {
                is ReplyBlock.Heading -> Text(
                    text = block.runs.toAnnotatedString(textColor, codeColor),
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor
                )
                is ReplyBlock.Paragraph -> Text(
                    text = block.runs.toAnnotatedString(textColor, codeColor),
                    style = style,
                    color = textColor
                )
                is ReplyBlock.BulletList -> block.items.forEach { item ->
                    Row {
                        Text("•", color = textColor)
                        Spacer(Modifier.width(spacing.sm))
                        Text(
                            item.toAnnotatedString(textColor, codeColor),
                            style = style,
                            color = textColor
                        )
                    }
                }
                is ReplyBlock.CodeBlock -> CodeBlockView(block)
            }
        }
    }
}

@Composable
private fun CodeBlockView(block: ReplyBlock.CodeBlock) {
    val spacing = MaterialTheme.spacing
    val codeStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        block.language?.let { lang ->
            Text(lang, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        block.lines.forEach { line ->
            Text(line, style = codeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun List<InlineRun>.toAnnotatedString(
    textColor: androidx.compose.ui.graphics.Color,
    codeColor: androidx.compose.ui.graphics.Color
) = buildAnnotatedString {
    forEach { run ->
        withStyle(
            SpanStyle(
                color = if (run.code) codeColor else textColor,
                fontFamily = if (run.code) FontFamily.Monospace else null,
                fontWeight = if (run.bold) FontWeight.Bold else null,
                fontStyle = if (run.italic) androidx.compose.ui.text.font.FontStyle.Italic else null
            )
        ) {
            append(run.text)
        }
    }
}
