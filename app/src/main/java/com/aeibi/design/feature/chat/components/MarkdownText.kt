package com.aeibi.design.feature.chat.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberStreamingMarkdownState

/**
 * Markdown 渲染——multiplatform-markdown-renderer（Compose 原生、异步解析）。
 * 完成态文本用 [MarkdownText]；流式文本用 [StreamingMarkdownText]。
 */

/** 完成态渲染：全文一次性异步解析。 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified
) {
    val textColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
    Markdown(
        content = text,
        modifier = modifier,
        colors = markdownColor(text = textColor),
        typography = markdownTypography(text = style)
    )
}

/**
 * 流式渲染：文本按前缀累积逐段 append 进 StreamingMarkdownState——
 * 库只渲染**已闭合块**（stable AST），进行中的尾部保持纯文本，内容不会闪没。
 */
@Composable
fun StreamingMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified
) {
    val textColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
    val state = rememberStreamingMarkdownState()
    var previous by remember { mutableStateOf("") }
    LaunchedEffect(text) {
        if (text.startsWith(previous)) {
            // 流式增量 = 新全量的后缀
            state.append(text.removePrefix(previous))
        }
        previous = text
    }
    Markdown(
        streamingMarkdownState = state,
        modifier = modifier,
        colors = markdownColor(text = textColor),
        typography = markdownTypography(text = style)
    )
}
