package com.doorcountylighthouses.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

const val LIGHT_LIST_ITEM = "light"
const val LIGHT_LIST_HEADER_COUNT = 1

class ListDragDropState(
    private val listState: LazyListState,
    private val headerCount: Int = LIGHT_LIST_HEADER_COUNT,
) {
    var onMove: (Int, Int) -> Unit = { _, _ -> }
    var onDragEnd: () -> Unit = {}

    var draggingItemKey by mutableStateOf<Any?>(null)
        private set
    private var draggingItemIndex by mutableStateOf<Int?>(null)
    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset by mutableIntStateOf(0)
    val scrollChannel = Channel<Float>(Channel.UNLIMITED)

    val draggingItemOffset: Float
        get() = draggingLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
        } ?: 0f

    private val draggingLayoutInfo: LazyListItemInfo?
        get() {
            val index = draggingItemIndex ?: return null
            return listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        }

    fun onDragStart(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        if (item.contentType != LIGHT_LIST_ITEM) return
        draggingItemKey = key
        draggingItemIndex = item.index
        draggingItemInitialOffset = item.offset
        draggingItemDraggedDelta = 0f
    }

    fun onDragInterrupted() {
        val wasDragging = draggingItemKey != null
        draggingItemKey = null
        draggingItemIndex = null
        draggingItemDraggedDelta = 0f
        if (wasDragging) onDragEnd()
    }

    fun onDrag(offsetY: Float) {
        draggingItemDraggedDelta += offsetY
        val dragging = draggingLayoutInfo ?: return
        val startOffset = dragging.offset + draggingItemOffset
        val endOffset = startOffset + dragging.size
        val middle = startOffset + dragging.size / 2f
        val neighborDown = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.contentType == LIGHT_LIST_ITEM && item.index == dragging.index + 1
        }
        val neighborUp = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.contentType == LIGHT_LIST_ITEM && item.index == dragging.index - 1
        }
        val target = when {
            neighborDown != null && middle > neighborDown.offset + neighborDown.size / 2f -> neighborDown
            neighborUp != null && middle < neighborUp.offset + neighborUp.size / 2f -> neighborUp
            else -> null
        }
        if (target != null) {
            val from = dragging.index - headerCount
            val to = target.index - headerCount
            if (from >= 0 && to >= 0 && from != to) {
                onMove(from, to)
                draggingItemIndex = target.index
                draggingItemInitialOffset = target.offset
                draggingItemDraggedDelta = 0f
            }
        }
        val viewportStart = listState.layoutInfo.viewportStartOffset
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val overscroll = when {
            draggingItemDraggedDelta > 0 -> (endOffset - viewportEnd).coerceAtLeast(0f)
            draggingItemDraggedDelta < 0 -> (startOffset - viewportStart).coerceAtMost(0f)
            else -> 0f
        }
        if (overscroll != 0f) {
            scrollChannel.trySend((overscroll / 6f).coerceIn(-8f, 8f))
        }
    }
}

@Composable
fun rememberListDragDropState(
    listState: LazyListState,
    onMove: (Int, Int) -> Unit,
    onDragEnd: () -> Unit = {},
): ListDragDropState {
    val state = remember(listState) { ListDragDropState(listState) }
    state.onMove = onMove
    state.onDragEnd = onDragEnd
    LaunchedEffect(state) {
        state.scrollChannel.receiveAsFlow().collect { listState.scrollBy(it) }
    }
    return state
}

fun Modifier.dragHandle(dragDrop: ListDragDropState, key: Any, enabled: Boolean): Modifier {
    if (!enabled) return this
    return pointerInput(dragDrop, key) {
        detectDragGestures(
            onDragStart = { dragDrop.onDragStart(key) },
            onDragEnd = { dragDrop.onDragInterrupted() },
            onDragCancel = { dragDrop.onDragInterrupted() },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDrop.onDrag(dragAmount.y)
            },
        )
    }
}
