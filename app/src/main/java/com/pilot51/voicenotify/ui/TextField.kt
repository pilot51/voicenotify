package com.pilot51.voicenotify.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pilot51.voicenotify.ui.theme.VoicenotifyTheme
import kotlinx.coroutines.delay

/**
 * @param contentDescription Text label of the `TextField` for the accessibility service
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SealTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    contentDescription: String? = null,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    clearIcon: @Composable (() -> Unit)? = {
        Icon(
            imageVector = Icons.Default.Clear,
            modifier = Modifier.padding(0.dp)
                .clickable(
                    onClick = { onValueChange("") },
                ),
            contentDescription = "Clear text"
        )
    },
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = VoicenotifyTheme.colors.textFieldContainer,
        unfocusedContainerColor = VoicenotifyTheme.colors.textFieldContainer,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics {
            if (contentDescription != null) {
                this.contentDescription = contentDescription
            }
        },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        singleLine = singleLine,
        decorationBox = @Composable { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = value,
                visualTransformation = visualTransformation,
                innerTextField = innerTextField,
                placeholder = placeholder,
                label = label,
                leadingIcon = leadingIcon,
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        if (clearIcon != null) {
                            clearIcon()
                        }
                    }
                    trailingIcon?.invoke()
                },
                prefix = prefix,
                suffix = suffix,
                supportingText = supportingText,
                singleLine = singleLine,
                enabled = enabled,
                isError = isError,
                interactionSource = interactionSource,
                colors = colors,
                contentPadding = PaddingValues(0.dp, 0.dp, 0.dp, 0.dp),
            )
        }
    )
//    BasicTextField(
//        value = value,
//        onValueChange = onValueChange,
//        modifier = modifier,
//        enabled = enabled,
//        readOnly = readOnly,
//        textStyle = textStyle,
//        keyboardOptions = keyboardOptions,
//        keyboardActions = keyboardActions,
//        visualTransformation = visualTransformation,
//        interactionSource = interactionSource,
//        singleLine = true,
//        decorationBox = { innerTextField ->
//            Row(
//                verticalAlignment = Alignment.CenterVertically,
//                modifier = Modifier
//                    .padding(horizontal = 8.dp, vertical = 8.dp)
//                    .then(
//                        Modifier.background(
//                            color = colors.focusedContainerColor,
//                            shape = shape
//                        )
//                    )
//            ) {
//                if (leadingIcon != null) {
//                    leadingIcon()
//                    Spacer(modifier = Modifier.width(8.dp))
//                }
//                Box(Modifier.weight(1f)) {
//                    if (value.isEmpty() && placeholder != null) {
//                        placeholder()
//                    }
//                    innerTextField()
//                }
//                if (value.isNotEmpty()) {
//                    if (clearIcon != null) {
//                        clearIcon()
//                    }
//                }
//                if (trailingIcon != null) {
//                    Spacer(modifier = Modifier.width(8.dp))
//                    trailingIcon()
//                }
//            }
//        }
//    )
}



@Composable
fun SealAutoFocusTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    contentDescription: String? = null,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(
        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    ),
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
    }

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .then(Modifier.semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            })
            .focusRequester(focusRequester = focusRequester),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus()
        }),
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SealTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
    )
) {
    TextField(
        value,
        onValueChange,
        modifier,
        enabled,
        readOnly,
        textStyle,
        label,
        placeholder,
        leadingIcon,
        trailingIcon,
        prefix,
        suffix,
        supportingText,
        isError,
        visualTransformation,
        keyboardOptions,
        keyboardActions,
        singleLine,
        maxLines,
        minLines,
        interactionSource,
        shape,
        colors
    )
}

@Composable
fun AdjacentLabel(modifier: Modifier = Modifier, text: String) {
    Text(
        text = text,
        modifier = modifier.padding(bottom = 12.dp, start = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}