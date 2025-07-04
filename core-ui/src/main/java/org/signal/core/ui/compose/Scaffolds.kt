/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.theme.SignalTheme

@OptIn(ExperimentalMaterial3Api::class)
object Scaffolds {

  /**
   * Settings scaffold that takes an icon as an ImageVector.
   *
   * @param titleContent The title area content. First parameter is the contentOffset.
   */
  @Composable
  fun Settings(
    title: String,
    onNavigationClick: () -> Unit,
    navigationIcon: ImageVector,
    modifier: Modifier = Modifier,
    navigationContentDescription: String? = null,
    titleContent: @Composable (Float, String) -> Unit = { _, title ->
      Text(text = title, style = MaterialTheme.typography.titleLarge)
    },
    snackbarHost: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
  ) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
      snackbarHost = snackbarHost,
      topBar = {
        DefaultTopAppBar(
          title = title,
          titleContent = titleContent,
          onNavigationClick = onNavigationClick,
          navigationIcon = navigationIcon,
          navigationContentDescription = navigationContentDescription,
          actions = actions,
          scrollBehavior = scrollBehavior
        )
      },
      modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
      content = content
    )
  }

  /**
   * Top app bar that takes an ImageVector
   */
  @Composable
  fun DefaultTopAppBar(
    title: String,
    titleContent: @Composable (Float, String) -> Unit,
    onNavigationClick: () -> Unit,
    navigationIcon: ImageVector,
    navigationContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  ) {
    TopAppBar(
      title = {
        titleContent(scrollBehavior.state.contentOffset, title)
      },
      navigationIcon = {
        IconButton(
          onClick = onNavigationClick,
          Modifier.padding(end = 16.dp)
        ) {
          Icon(
            imageVector = navigationIcon,
            contentDescription = navigationContentDescription
          )
        }
      },
      scrollBehavior = scrollBehavior,
      colors = TopAppBarDefaults.topAppBarColors(
        scrolledContainerColor = SignalTheme.colors.colorSurface2
      ),
      actions = actions
    )
  }
}

@Preview
@Composable
private fun SettingsScaffoldPreview() {
  SignalTheme(isDarkMode = false) {
    Scaffolds.Settings(
      "Settings Scaffold",
      onNavigationClick = {},
      navigationIcon = Icons.Filled.Settings,
      actions = {
        IconButton(onClick = {}) {
          Icon(Icons.Default.Settings, contentDescription = null)
        }
      }
    ) { paddingValues ->
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .padding(paddingValues)
          .fillMaxSize()
      ) {
        Text("Content")
      }
    }
  }
}
