/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

// very limited Markdown parser
// since this is literally just used once I didn't want to add another dependency

package paige.navic.ui.component.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import paige.navic.ui.component.dialog.LinkConfirmationDialog

object MarkdownDefaults {
	@Composable
	fun colors(
		textColor: Color = Color.Unspecified,
		codeBlockContainerColor: Color = Color.Unspecified
	) = MarkdownColors(
		textColor = textColor,
		codeBlockContainerColor = codeBlockContainerColor.takeOrElse { MaterialTheme.colorScheme.surface }
	)
}

data class MarkdownColors(
	val textColor: Color,
	val codeBlockContainerColor: Color
)

@Composable
fun Markdown(
	text: String,
	modifier: Modifier = Modifier,
	colors: MarkdownColors = MarkdownDefaults.colors()
) {
	val lines = text.split("\n")
	var linkToOpen by rememberSaveable { mutableStateOf<String?>(null) }
	val linkInteractionListener = remember {
		LinkInteractionListener { link ->
			linkToOpen = (link as LinkAnnotation.Url).url
		}
	}

	Column(modifier = modifier) {
		lines.forEach { line ->
			if (line.isBlank()) {
				Spacer(Modifier.height(8.dp))
			} else {
				MarkdownLine(line, colors, linkInteractionListener)
			}
		}
	}

	LinkConfirmationDialog(
		linkToOpen = linkToOpen,
		onDismissRequest = { linkToOpen = null }
	)
}

@Composable
private fun MarkdownLine(
	line: String,
	colors: MarkdownColors,
	linkInteractionListener: LinkInteractionListener
) {
	val trimmed = line.trimStart()
	val orderedListMatch = Regex("""^(\d+\.)\s+(.*)""").find(trimmed)

	when {
		trimmed.startsWith("# ") -> {
			MarkdownText(
				text = trimmed.substring(2),
				colors = colors,
				linkInteractionListener = linkInteractionListener,
				style = MaterialTheme.typography.headlineLarge,
				modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
			)
		}

		trimmed.startsWith("## ") -> {
			MarkdownText(
				text = trimmed.substring(3),
				colors = colors,
				linkInteractionListener = linkInteractionListener,
				style = MaterialTheme.typography.headlineMedium,
				modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
			)
		}

		trimmed.startsWith("### ") -> {
			MarkdownText(
				text = trimmed.substring(4),
				colors = colors,
				linkInteractionListener = linkInteractionListener,
				style = MaterialTheme.typography.headlineSmall,
				modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
			)
		}

		trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
			Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
				MarkdownText(
					text = "• ",
					colors = colors,
					linkInteractionListener = linkInteractionListener
				)
				MarkdownText(
					text = trimmed.substring(2),
					colors = colors,
					linkInteractionListener = linkInteractionListener
				)
			}
		}

		orderedListMatch != null -> {
			val number = orderedListMatch.groupValues[1]
			val content = orderedListMatch.groupValues[2]
			Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
				Text(
					text = "$number ",
					color = colors.textColor
				)
				MarkdownText(
					text = content,
					colors = colors,
					linkInteractionListener = linkInteractionListener
				)
			}
		}

		else -> {
			MarkdownText(
				text = line,
				colors = colors,
				linkInteractionListener = linkInteractionListener,
				modifier = Modifier.padding(vertical = 2.dp)
			)
		}
	}
}

@Composable
private fun MarkdownText(
	text: String,
	colors: MarkdownColors = MarkdownDefaults.colors(),
	style: TextStyle = LocalTextStyle.current,
	linkInteractionListener: LinkInteractionListener,
	modifier: Modifier = Modifier
) {
	Text(
		text = buildAnnotatedString {
			appendMarkdown(text, colors, linkInteractionListener)
		},
		modifier = modifier,
		style = style
	)
}

private fun AnnotatedString.Builder.appendMarkdown(
	text: String,
	colors: MarkdownColors,
	linkInteractionListener: LinkInteractionListener
) {
	var remaining = text
	while (remaining.isNotEmpty()) {
		val boldMatch = Regex("""\*\*(.*?)\*\*""").find(remaining)
		val italicMatch = Regex("""\*(.*?)\*""").find(remaining)
		val strikeoutMatch = Regex("""~~(.*?)~~""").find(remaining)
		val codeMatch = Regex("""`(.*?)`""").find(remaining)
		val linkMatch = Regex("""\[(.*?)]\((.*?)\)""").find(remaining)

		val matches = listOfNotNull(boldMatch, italicMatch, strikeoutMatch, codeMatch, linkMatch)
		if (matches.isEmpty()) {
			append(remaining)
			remaining = ""
			continue
		}

		val nextMatch = matches.minWithOrNull(compareBy({ it.range.first }, { -it.value.length }))!!

		append(remaining.substring(0, nextMatch.range.first))

		val content = nextMatch.groupValues[1]
		when (nextMatch) {
			boldMatch -> {
				withStyle(SpanStyle(color = colors.textColor, fontWeight = FontWeight.Bold)) {
					appendMarkdown(content, colors, linkInteractionListener)
				}
			}

			italicMatch -> {
				withStyle(SpanStyle(color = colors.textColor, fontStyle = FontStyle.Italic)) {
					appendMarkdown(content, colors, linkInteractionListener)
				}
			}

			strikeoutMatch -> {
				withStyle(
					SpanStyle(
						color = colors.textColor,
						textDecoration = TextDecoration.LineThrough
					)
				) {
					appendMarkdown(content, colors, linkInteractionListener)
				}
			}

			codeMatch -> {
				withStyle(
					SpanStyle(
						fontFamily = FontFamily.Monospace,
						background = colors.codeBlockContainerColor
					)
				) {
					appendMarkdown(content, colors, linkInteractionListener)
				}
			}

			linkMatch -> {
				val link = nextMatch.groupValues[2]
				withLink(
					LinkAnnotation.Url(
						url = link,
						linkInteractionListener = linkInteractionListener
					)
				) {
					withStyle(SpanStyle(color = colors.textColor)) {
						appendMarkdown(content, colors, linkInteractionListener)
					}
				}
			}
		}

		remaining = remaining.substring(nextMatch.range.last + 1)
	}
}
