package com.aeibi.design.feature.chat

/**
 * 回复结构容器 v0 实验模型（feature/reply-container）。
 *
 * agent 回复从 markdown 字符串改为**结构化块数组**：渲染端按块类型直映组件，
 * 无解析层、无 AST——组件数 = 块数（确定可控），流式粒度为块级（不存在
 * "未闭合块"）。本模型先服务 `#bktest` 合成流对照实验，协议与存储后定。
 */
sealed interface ReplyBlock {

    /** 段落/标题等文本块——由行内 runs 组成。 */
    data class Paragraph(val runs: List<InlineRun>) : ReplyBlock

    /** 独立容器块：整段代码一起到达（v0 单元粒度），后续再拆行级增量。 */
    data class CodeBlock(val language: String?, val lines: List<String>) : ReplyBlock

    /** 无序列表。 */
    data class BulletList(val items: List<List<InlineRun>>) : ReplyBlock

    /** 标题（块级定位用；v0 只做视觉分级）。 */
    data class Heading(val level: Int, val runs: List<InlineRun>) : ReplyBlock
}

/** 行内富文本最小集：粗体/斜体/行内代码。 */
data class InlineRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false
)
