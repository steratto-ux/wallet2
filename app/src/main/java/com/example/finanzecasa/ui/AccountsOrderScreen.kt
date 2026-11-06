package com.example.finanzecasa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/** Ordine e visibilità degli account nella schermata principale. */
@Composable
fun AccountsOrderScreen(onBack: () -> Unit, vm: ManageViewModel = viewModel()) {
    val accounts by vm.orderedAccounts.collectAsState()
    val scope = rememberCoroutineScope()
    val dark = LocalDarkTheme.current

    ScreenScaffold("Ordina account", onBack = onBack) { inner ->
        FormColumn(inner) {
            Text(
                "Usa le frecce per cambiare l'ordine. Un account nascosto non compare nella schermata principale, " +
                    "ma il suo saldo conta ancora nel patrimonio netto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (accounts.isEmpty()) Text("Nessun account.")
            AppCard(Modifier.fillMaxWidth()) {
                accounts.forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val tint = accountTint(a.colorKey, a.type, dark)
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(accountIcon(a.iconKey, a.type), contentDescription = null, tint = tint, modifier = Modifier.size(19.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                if (a.hidden) "Nascosto" else "Visibile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(enabled = i > 0, onClick = { scope.launch { vm.moveAccount(a.id, -1) } }) {
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = "Sposta su")
                        }
                        IconButton(enabled = i < accounts.size - 1, onClick = { scope.launch { vm.moveAccount(a.id, 1) } }) {
                            Icon(Icons.Outlined.ArrowDownward, contentDescription = "Sposta giù")
                        }
                        Switch(checked = !a.hidden, onCheckedChange = { visible -> scope.launch { vm.setAccountHidden(a.id, !visible) } })
                    }
                }
            }
        }
    }
}
