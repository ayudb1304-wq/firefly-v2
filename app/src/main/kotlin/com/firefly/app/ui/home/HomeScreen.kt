package com.firefly.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firefly.app.FireflyApp
import com.firefly.app.R
import com.firefly.app.core.group.GroupCode

/** Create or join a group by typed code (PRD A1/A2; QR arrives in Phase 2). */
@Composable
fun HomeScreen() {
    val app = LocalContext.current.applicationContext as FireflyApp
    val vm: HomeViewModel = viewModel { HomeViewModel(app.container.groupRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(40.dp))

            Button(onClick = vm::create, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_create_group))
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = state.joinInput,
                onValueChange = vm::onJoinInput,
                label = { Text(stringResource(R.string.home_code_label)) },
                singleLine = true,
                isError = state.error,
                supportingText = { Text(if (state.error) stringResource(R.string.home_code_invalid) else stringResource(R.string.home_code_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = vm::join,
                enabled = !state.busy && GroupCode.normalise(state.joinInput) != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.home_join_group)) }
        }
    }
}
