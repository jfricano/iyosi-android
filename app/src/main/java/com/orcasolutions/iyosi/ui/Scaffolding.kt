package com.orcasolutions.iyosi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A full-screen page with the guide's paper background and a simple top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidePage(
    title: String,
    onClose: (() -> Unit)?,
    closeIsBack: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = Guide.colors
    Scaffold(
        containerColor = colors.paper,
        contentColor = colors.ink,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = Guide.heading(17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            if (closeIsBack) Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            else Icon(Icons.Filled.Close, "Close")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.paper, titleContentColor = colors.ink,
                    navigationIconContentColor = colors.ink, actionIconContentColor = colors.tealInk,
                ),
            )
        },
    ) { padding -> content(padding) }
}

/** Scrollable, width-limited column used by most pages. */
@Composable
fun GuideColumn(padding: PaddingValues, spacing: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.dp),
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(20.dp),
            content = content,
        )
    }
}

@Composable
fun GuideHeading(text: String, size: Int = 26, editorial: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        style = if (editorial) Guide.editorial(size.sp) else Guide.heading(size.sp),
        color = Guide.colors.ink,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun GuideMuted(text: String, size: Int = 14) {
    Text(text, style = Guide.body(size.sp), color = Guide.colors.muted)
}

@Composable
fun GuideToolbarAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) { Text(label, style = Guide.heading(15.sp)) }
}

@Composable
fun GuideTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    limit: Int? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    val colors = Guide.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = singleLine,
            minLines = minLines,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                capitalization = capitalization,
                autoCorrectEnabled = !password && keyboardType == KeyboardType.Text,
            ),
            isError = limit != null && value.length > limit,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.tealInk, unfocusedBorderColor = colors.muted,
                focusedLabelColor = colors.tealInk, unfocusedLabelColor = colors.muted, cursorColor = colors.tealInk,
            ),
            modifier = modifier.fillMaxWidth(),
        )
        if (limit != null) {
            Text(
                "${value.length} / ${"%,d".format(limit)} characters",
                style = Guide.body(12.sp),
                color = if (value.length > limit) colors.warning else colors.muted,
            )
        }
    }
}
