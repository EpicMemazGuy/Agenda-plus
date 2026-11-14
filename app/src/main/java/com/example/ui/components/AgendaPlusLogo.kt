package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingOutline

@Composable
fun AgendaPlusLogo(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val nothingMode = isNothingOs
    val bg = if (nothingMode) NothingBlack else Color(0xFF15100C)
    val resId = if (nothingMode) {
        R.drawable.agenda_plus_nothing_icon_1794665678395
    } else {
        R.drawable.agenda_plus_icon_1794665139149
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (nothingMode) Modifier.border(1.dp, NothingOutline, CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = "Agenda+ Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
