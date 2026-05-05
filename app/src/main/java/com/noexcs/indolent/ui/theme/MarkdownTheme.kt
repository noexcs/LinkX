package com.noexcs.indolent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val colors = markdownColor(
        text = colorScheme.onSurface,
        codeBackground = colorScheme.surfaceContainerHigh,
        inlineCodeBackground = colorScheme.surfaceContainerHighest,
        dividerColor = colorScheme.outlineVariant,
        tableBackground = colorScheme.surfaceContainerLow,
    )

    val mdTypography = markdownTypography(
        h1 = typography.headlineMedium,
        h2 = typography.titleLarge,
        h3 = typography.titleMedium,
        h4 = typography.titleSmall,
        h5 = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        h6 = typography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
        ),
        text = typography.bodyMedium,
        code = typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
        ),
        inlineCode = typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            color = colorScheme.onSurfaceVariant,
        ),
        quote = typography.bodyMedium.copy(
            color = colorScheme.onSurfaceVariant,
        ),
        paragraph = typography.bodyMedium,
        ordered = typography.bodyMedium,
        bullet = typography.bodyMedium,
        list = typography.bodyMedium,
        textLink = TextLinkStyles(
            style = typography.bodyMedium.copy(
                color = colorScheme.primary,
                fontWeight = FontWeight.Medium,
            ).toSpanStyle()
        ),
        table = typography.labelSmall,
    )

    val components = remember {
        markdownComponents(
            checkbox = {
                MarkdownCheckBox(it.content, it.node, it.typography.text)
            },
            codeFence = {
                MarkdownHighlightedCodeFence(
                    content = it.content,
                    node = it.node,
                    showHeader = true,
                )
            },
            codeBlock = {
                MarkdownHighlightedCodeBlock(
                    content = it.content,
                    node = it.node,
                    showHeader = true,
                )
            },
        )
    }

    Markdown(
        content = content,
        modifier = modifier,
        colors = colors,
        typography = mdTypography,
        components = components,
        retainState = true,
    )
}
