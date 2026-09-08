package org.pashri.bustimes.ui.favourites

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.pashri.bustimes.data.db.FavouriteStop

/**
 * The favourites menu, expanding upward from its button.
 *
 * Hand-rolled because Material 3 at this version has no FAB menu component.
 *
 * Entries are drawn bottom-up, so the first one — the nearest stop, which is
 * nearly always the one being checked — sits closest to the button and takes
 * the least thumb travel to reach.
 *
 * @param favourites the starred stops, already ordered nearest first.
 * @param visible whether the menu is open.
 * @param bottomPadding space to leave beneath the menu for the buttons it
 *   expands above.
 * @param onFavouriteClicked called with the stop chosen.
 * @param onDismiss called when the scrim is tapped.
 * @param modifier layout modifier.
 */
@Composable
fun FavouritesMenu(
    favourites: List<FavouriteStop>,
    visible: Boolean,
    bottomPadding: Dp,
    onFavouriteClicked: (FavouriteStop) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(SCRIM_MILLIS)),
        exit = fadeOut(tween(SCRIM_MILLIS)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                // Anywhere off the menu closes it, which is the expected way
                // out of something opened by mistake.
                .clickable(onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(MENU_MILLIS)) + slideInVertically(tween(MENU_MILLIS)) { it / 4 },
        exit = fadeOut(tween(MENU_MILLIS)) + slideOutVertically(tween(MENU_MILLIS)) { it / 4 },
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.End,
                // Reversed so the nearest stop ends up at the bottom of the
                // stack, next to the button.
                verticalArrangement = Arrangement.spacedBy(ENTRY_GAP, Alignment.Bottom),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = MENU_END_PADDING, bottom = bottomPadding)
                    .heightIn(max = MENU_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState(), reverseScrolling = true),
            ) {
                for (favourite in favourites.reversed()) {
                    FavouriteEntry(
                        favourite = favourite,
                        onClick = { onFavouriteClicked(favourite) },
                    )
                }
            }
        }
    }
}

/** One labelled pill in the menu. */
@Composable
private fun FavouriteEntry(favourite: FavouriteStop, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(ENTRY_CORNER),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = ENTRY_ELEVATION,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = favourite.name,
            style = MaterialTheme.typography.bodyLarge,
            // Names from bustimes are fully qualified and long, so the pill
            // trims rather than wrapping into a paragraph.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = ENTRY_MAX_WIDTH)
                .padding(horizontal = ENTRY_PADDING_H, vertical = ENTRY_PADDING_V),
        )
    }
}

private const val SCRIM_ALPHA = 0.32f
private const val SCRIM_MILLIS = 150
private const val MENU_MILLIS = 180
private val ENTRY_GAP = 8.dp
private val ENTRY_CORNER = 20.dp
private val ENTRY_ELEVATION = 4.dp
private val ENTRY_MAX_WIDTH = 260.dp
private val ENTRY_PADDING_H = 16.dp
private val ENTRY_PADDING_V = 10.dp
private val MENU_END_PADDING = 16.dp
private val MENU_MAX_HEIGHT = 420.dp
