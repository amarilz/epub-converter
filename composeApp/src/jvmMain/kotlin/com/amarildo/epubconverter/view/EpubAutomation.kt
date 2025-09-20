package com.amarildo.epubconverter.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amarildo.epubconverter.viewmodel.EpubAutomationViewModel
import com.amarildo.epubconverter.viewmodel.UiState
import com.composables.icons.lucide.Book
import com.composables.icons.lucide.Delete
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun EpubAutomation(vm: EpubAutomationViewModel = viewModel()) {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        var isPathLoading by remember { mutableStateOf(false) }
        val uiState by vm.uiState.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(uiState.successMessage, uiState.error) {
            uiState.successMessage?.let { msg ->
                snackbarHostState.showSnackbar(
                    message = msg,
                    duration = SnackbarDuration.Short,
                )
                vm.clearMessage()
            }
            uiState.error?.let { error ->
                snackbarHostState.showSnackbar(
                    message = error,
                    duration = SnackbarDuration.Long,
                )
                vm.clearMessage()
            }
        }

        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = if (data.visuals.message.contains("Error", ignoreCase = true)) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (data.visuals.message.contains("Error", ignoreCase = true)) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    )
                }
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    ConfigurationFileInput(uiState, vm, isPathLoading) { isPathLoading = it }
                }
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                vm.convertEpubToTxtz(uiState.epubFilePath)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp,
                        ),
                    ) {
                        Text("Load ePub")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigurationFileInput(
    uiState: UiState,
    vm: EpubAutomationViewModel,
    isPathLoading: Boolean,
    setPathLoading: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()

    EnhancedInputRow(
        label = "Select the epub to convert",
        value = uiState.epubFilePath,
        onValueChange = { vm.clearEpubFilePath() },
        placeholder = "Selecting the epub file...",
        isLoading = isPathLoading,
        onFocusAction = {
            scope.launch {
                setPathLoading(true)
                try {
                    vm.selectEpub()
                } finally {
                    setPathLoading(false)
                }
            }
        },
        leadingIcon = {
            Icon(
                imageVector = Lucide.Book,
                contentDescription = "Configuration File",
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
fun EnhancedInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onFocusAction: () -> Unit,
    placeholder: String = "",
    isLoading: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            leadingIcon = leadingIcon,
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                    ) {
                        Icon(
                            imageVector = Lucide.Delete,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !isLoading) {
                        onFocusAction()
                    }
                },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
            ),
            shape = RoundedCornerShape(12.dp),
        )
    }
}
