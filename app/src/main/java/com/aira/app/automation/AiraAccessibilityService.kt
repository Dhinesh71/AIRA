package com.aira.app.automation

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AiraAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connectionState.value = true
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onDestroy() {
        clearServiceReference()
        super.onDestroy()
    }

    fun performNavigation(target: String): Boolean = when (target.lowercase()) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        else -> false
    }

    fun clickNodeByText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findBestMatchingNode(root, query) ?: return false
        return performClick(target)
    }

    fun typeIntoFocusedField(
        text: String,
        hint: String,
    ): Boolean {
        val root = rootInActiveWindow ?: return false
        val targetNode = when {
            hint.isNotBlank() -> findEditableNode(root, hint)
            else -> null
        } ?: findFocusedEditableNode(root) ?: findFirstEditableNode(root) ?: return false

        if (!targetNode.isFocused) {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text,
            )
        }
        return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollNode = findFirstScrollableNode(root) ?: return false
        val action = when (direction.lowercase()) {
            "up", "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        return scrollNode.performAction(action)
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    private fun findBestMatchingNode(
        root: AccessibilityNodeInfo,
        query: String,
    ): AccessibilityNodeInfo? {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) return null

        val matches = mutableListOf<AccessibilityNodeInfo>()
        walkTree(root) { node ->
            val merged = buildNodeText(node)
            if (merged.contains(normalizedQuery)) {
                matches += node
            }
        }

        return matches.sortedWith(
            compareByDescending<AccessibilityNodeInfo> { buildNodeText(it) == normalizedQuery }
                .thenByDescending { it.isClickable }
                .thenByDescending { it.isVisibleToUser },
        ).firstOrNull()
    }

    private fun findEditableNode(
        root: AccessibilityNodeInfo,
        hint: String,
    ): AccessibilityNodeInfo? {
        val normalizedHint = hint.lowercase()
        var match: AccessibilityNodeInfo? = null
        walkTree(root) { node ->
            if (match != null) return@walkTree
            if (!isEditable(node)) return@walkTree
            val merged = buildNodeText(node)
            if (merged.contains(normalizedHint)) {
                match = node
            }
        }
        return match
    }

    private fun findFocusedEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var match: AccessibilityNodeInfo? = null
        walkTree(root) { node ->
            if (match != null) return@walkTree
            if (isEditable(node) && node.isFocused) {
                match = node
            }
        }
        return match
    }

    private fun findFirstEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var match: AccessibilityNodeInfo? = null
        walkTree(root) { node ->
            if (match != null) return@walkTree
            if (isEditable(node)) {
                match = node
            }
        }
        return match
    }

    private fun findFirstScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var match: AccessibilityNodeInfo? = null
        walkTree(root) { node ->
            if (match != null) return@walkTree
            if (node.isScrollable) {
                match = node
            }
        }
        return match
    }

    private fun isEditable(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        return node.isEditable || className.contains("EditText", ignoreCase = true)
    }

    private fun buildNodeText(node: AccessibilityNodeInfo): String {
        val parts = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.hintText?.toString(),
            node.viewIdResourceName?.substringAfterLast('/'),
        )
        return parts.joinToString(" ").lowercase()
    }

    private fun walkTree(
        node: AccessibilityNodeInfo,
        visitor: (AccessibilityNodeInfo) -> Unit,
    ) {
        visitor(node)
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                walkTree(child, visitor)
            }
        }
    }

    private fun clearServiceReference() {
        if (instance === this) {
            instance = null
            _connectionState.value = false
        }
    }

    companion object {
        @Volatile
        var instance: AiraAccessibilityService? = null
            private set

        private val _connectionState = MutableStateFlow(false)
        val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()
    }
}
