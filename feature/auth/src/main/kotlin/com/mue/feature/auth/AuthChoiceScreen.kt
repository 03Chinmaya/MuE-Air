package com.mue.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mue.core.design.MuELogo
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite

@Composable
fun AuthChoiceScreen(onNavigateToHome: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MuELogo(symbolSize = 96.dp)
        Spacer(modifier = Modifier.height(36.dp))
        Button(
            onClick = { /* Login */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PureWhite,
                contentColor = PureBlack
            )
        ) {
            Text("Sign In")
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onNavigateToHome,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = PureWhite
            )
        ) {
            Text("Continue as Guest")
        }
    }
}
