package com.fsp.morse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.fsp.morse.reception.CameraReceptionScreen
import com.fsp.morse.reception.ReceptionScreen
import com.fsp.morse.transmission.FlashlightScreen
import com.fsp.morse.ui.theme.MorseTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MorseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MorseAppScreen(
                        modifier = Modifier.padding(innerPadding) // Apply padding from Scaffold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MorseAppScreen(modifier: Modifier = Modifier) {
    val tabTitles = listOf("Transmission", "Réception", "Caméra")
    val pagerState = rememberPagerState { tabTitles.size }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pagerState.currentPage) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(title) }
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> FlashlightScreen(Modifier.fillMaxSize())
                1 -> ReceptionScreen(Modifier.fillMaxSize())
                2 -> CameraReceptionScreen(Modifier.fillMaxSize())
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MorseTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            MorseAppScreen(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
